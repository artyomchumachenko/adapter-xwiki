package ru.cbgr.adapter.xwiki.service;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.chunker.CombinedContentChunker;
import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;
import ru.cbgr.adapter.xwiki.model.Chunk;
import ru.cbgr.adapter.xwiki.model.Page;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.repository.ChunkRepository;
import ru.cbgr.adapter.xwiki.repository.PageRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiService {

    private final XWikiClient xWikiClient;
    private final LlamaAiClient llamaAiClient;

    private final PageRepository pageRepository;
    private final ChunkRepository chunkRepository;
    private final JdbcTemplate jdbcTemplate;

    private final ContentNormalizationService contentNormalizationService;
    private final CombinedContentChunker chunker;

    private final ModelsProperties modelsProperties;

    /**
     * Обходит все пространства, полученные по /rest/wikis/xwiki/spaces,
     * и для каждого пространства обрабатывает страницы.
     */
    @Transactional
    public void processAllSpacesAndPages() {
        SpacesResponse spacesResponse = xWikiClient.getSpaces();
        if (spacesResponse == null || spacesResponse.getSpaces() == null) {
            log.warn("Нет пространств для обработки.");
            return;
        }
        for (Space space : spacesResponse.getSpaces()) {
            if (space.getId().startsWith("xwiki:Help") || space.getId().startsWith("xwiki:Main") || space.getId().startsWith("xwiki:Sandbox") || space.getId().startsWith("xwiki:XWiki")) continue;

            processSpace(space);
        }
    }

    /**
     * Обрабатывает отдельное пространство:
     * – извлекает ссылку на список страниц (rel = "http://www.xwiki.org/rel/pages"),
     * – обходит все страницы,
     * – если есть вложенные пространства, обрабатывает их рекурсивно.
     */
    @Transactional
    protected void processSpace(Space space) {
        log.debug("Обрабатываем пространство: {}", space.getId());
        Optional<Link> pagesLinkOpt = space.getLinks().stream()
                .filter(link -> "http://www.xwiki.org/rel/pages".equals(link.getRel()))
                .findFirst();
        if (pagesLinkOpt.isEmpty()) {
            log.warn("Нет ссылки на страницы для пространства: {}", space.getId());
        } else {
            String pagesUrl = pagesLinkOpt.get().getHref();
            PagesResponse pagesResponse = xWikiClient.getPages(pagesUrl);
            if (pagesResponse == null || pagesResponse.getPageSummaries() == null) {
                log.warn("Нет страниц в пространстве: {}", space.getId());
            } else {
                for (PageSummary page : pagesResponse.getPageSummaries()) {
                    processPage(page);
                }
            }
        }
        // Если у пространства есть вложенные пространства – обрабатываем их рекурсивно:
        if (space.getSpaces() != null) {
            for (Space nestedSpace : space.getSpaces()) {
                processSpace(nestedSpace);
            }
        }
    }

    /**
     * Основной метод обработки страницы.
     * Если страница уже существует, вызывается updatePageAndEmbeddings,
     * иначе создаётся новая страница через createNewPageAndEmbeddings.
     */
    @Transactional
    protected void processPage(PageSummary page) {
        if (isSkipProcess(page)) {
            return;
        }

        log.debug("Обработка страницы {} продолжается", page.getId());

        Optional<Page> optPage = pageRepository.findByXwikiId(page.getId());
        if (optPage.isPresent()) {
            log.debug("Обновление страницы {}", page.getId());
            updatePageAndEmbeddings(page, optPage.get());
        } else {
            log.debug("Создание страницы {}", page.getId());
            createNewPageAndEmbeddings(page);
        }
    }

    /**
     * Создает новую страницу и связанные с ней данные (чанки и эмбеддинги).
     */
    @Transactional
    protected void createNewPageAndEmbeddings(PageSummary page) {
        // Создание и сохранение объекта Page через JPA
        Page newPage = new Page();
        newPage.setXwikiId(page.getId());
        newPage.setXwikiVersion(page.getVersion());
        newPage.setXwikiAbsoluteUrl(page.getXwikiAbsoluteUrl());
        Page savedPage = pageRepository.save(newPage);
        log.debug("Страница сохранена с идентификатором {}.", savedPage.getId());

        // Получение подробностей страницы
        Optional<Link> detailLinkOpt = page.getLinks().stream()
                .filter(link -> "http://www.xwiki.org/rel/page".equals(link.getRel()))
                .findFirst();
        if (detailLinkOpt.isEmpty()) {
            log.warn("Не найдена ссылка для получения подробной информации для страницы: {}", page.getId());
            return;
        }
        String detailUrl = detailLinkOpt.get().getHref();
        log.debug("Получаем данные страницы по URL: {}", detailUrl);

        PageDetails pageDetails = xWikiClient.getPageDetails(detailUrl);
        if (pageDetails == null || pageDetails.getContent() == null || pageDetails.getContent().isEmpty()) {
            log.warn("Поле content пустое для страницы: {}", page.getId());
            return;
        }
        String content = pageDetails.getContent();
        List<String> chunks = chunker.chunkContent(content, 1500, 3, 10);

        processChunks(savedPage, chunks);
    }

    /**
     * Обновляет страницу: удаляются старые чанки и эмбеддинги,
     * затем сохраняются новые данные из полученного контента.
     */
    @Transactional
    protected void updatePageAndEmbeddings(PageSummary page, Page existingPage) {
        // Обновляем информацию по странице
        existingPage.setXwikiVersion(page.getVersion());
        existingPage.setXwikiAbsoluteUrl(page.getXwikiAbsoluteUrl());
        Page updatedPage = pageRepository.save(existingPage);
        log.debug("Страница {} обновлена.", updatedPage.getId());

        // Удаляем старые эмбеддинги, привязанные к чанкам данной страницы
        String deleteEmbeddingsSql = "DELETE FROM embeddings WHERE chunk_id IN (SELECT id FROM chunks WHERE page_id = ?)";
        jdbcTemplate.update(deleteEmbeddingsSql, updatedPage.getId());
        log.debug("Удалены старые эмбеддинги для страницы {}.", updatedPage.getId());

        // Удаляем старые чанки через JPA-репозиторий
        List<Chunk> chunksToDelete = chunkRepository.findByPage(updatedPage);
        if (!chunksToDelete.isEmpty()) {
            chunkRepository.deleteAll(chunksToDelete);
            log.debug("Удалены старые чанки для страницы {}.", updatedPage.getId());
        }

        // Получаем подробности страницы
        Optional<Link> detailLinkOpt = page.getLinks().stream()
                .filter(link -> "http://www.xwiki.org/rel/page".equals(link.getRel()))
                .findFirst();
        if (detailLinkOpt.isEmpty()) {
            log.warn("Не найдена ссылка для получения подробной информации для страницы: {}", page.getId());
            return;
        }
        String detailUrl = detailLinkOpt.get().getHref();
        log.debug("Получаем данные страницы по URL: {}", detailUrl);

        PageDetails pageDetails = xWikiClient.getPageDetails(detailUrl);
        if (pageDetails == null || pageDetails.getContent() == null || pageDetails.getContent().isEmpty()) {
            log.warn("Поле content пустое для страницы: {}", page.getId());
            return;
        }
        String content = pageDetails.getContent();
        List<String> chunks = chunker.chunkContent(content, 1500, 3, 10);

        processChunks(updatedPage, chunks);
    }

    /**
     * Общая логика сохранения чанков и эмбеддингов для страницы.
     *
     * @param page   объект страницы (уже сохраненный)
     * @param chunks список строк-чанков контента
     */
    @Transactional
    protected void processChunks(Page page, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            String rawChunk = chunks.get(i);
            String normalizedChunk = contentNormalizationService.normalize(rawChunk);
            if (normalizedChunk.isEmpty()) {
                continue;
            }

            // Сохраняем чанк в таблицу chunks через JPA
            Chunk newChunk = new Chunk();
            newChunk.setPage(page);
            newChunk.setChunkIndex(i);
            newChunk.setTextSnippet(normalizedChunk);
            Chunk savedChunk = chunkRepository.save(newChunk);
            log.debug("Чанк с индексом {} сохранён с идентификатором {}.", i, savedChunk.getId());

            // Создаем запись в таблице embeddings (нативно) для данного чанка
            String insertEmbeddingSql = "INSERT INTO embeddings (chunk_id) VALUES (?)";
            KeyHolder embeddingKeyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertEmbeddingSql, new String[]{"id"});
                ps.setLong(1, savedChunk.getId());
                return ps;
            }, embeddingKeyHolder);
            Number embeddingId = embeddingKeyHolder.getKey();
            if (embeddingId == null) {
                log.error("Не удалось создать запись в embeddings для chunk_id {}", savedChunk.getId());
                continue;
            }

            // Для каждой embedding-модели из настроек
            for (EmbeddingModelConfigRecord modelConfig : modelsProperties.embeddingModels()) {
                String modelName = modelConfig.model();
                EmbeddingResponse embeddingResponse;
                try {
                    // Генерируем эмбеддинг для нормализованного текста, передавая имя модели
                    embeddingResponse = llamaAiClient.getEmbeddings(normalizedChunk, modelName);
                } catch (Exception ex) {
                    log.error("Ошибка генерации эмбеддинга для модели {} для чанка: {}", modelName, normalizedChunk, ex);
                    continue;
                }
                List<Embedding> embeddingResults = embeddingResponse.getResults();
                if (embeddingResults == null || embeddingResults.isEmpty()) {
                    log.warn("Эмбеддинг не получен для модели {} для чанка: {}", modelName, normalizedChunk);
                    continue;
                }

                // Используем первый результат эмбеддинга
                Embedding embedding = embeddingResults.get(0); // или embeddingResults.getFirst(), если используется соответствующий метод
                List<Double> output = embedding.getOutput();
                float[] vector = new float[output.size()];
                for (int j = 0; j < output.size(); j++) {
                    vector[j] = output.get(j).floatValue();
                }
                float[] normalizedVector = normalizeVector(vector);
                PGvector pgVector = new PGvector(normalizedVector);

                // Вычисляем имя динамической колонки: заменяем все символы, не являющиеся цифрами или латинскими буквами, на нижнее подчёркивание, затем добавляем суффикс _embedding
                String columnName = modelName.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
                String updateSql = "UPDATE embeddings SET " + columnName + " = ? WHERE id = ?";
                jdbcTemplate.update(updateSql, pgVector, embeddingId.longValue());
                log.debug("Эмбеддинг для модели {} сохранён в колонку {} для embedding_id {}.", modelName, columnName, embeddingId);
            }
        }
    }

    /**
     * Нормализует вектор, приводя его к единичной длине (L2-норма).
     *
     * @param vector исходный вектор
     * @return нормализованный вектор
     */
    private float[] normalizeVector(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) return vector; // Предотвращаем деление на ноль
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / (float) norm;
        }
        return normalized;
    }

    /**
     * Проверяет, следует ли пропустить обработку для указанной страницы.
     * Производится запрос по xwiki_id в таблице pages и сравнение версии.
     *
     * @param page объект страницы, содержащий getId() и getVersion()
     * @return true если версия в базе совпадает с версией страницы, false если запись не найдена или версии отличаются
     */
    public boolean isSkipProcess(PageSummary page) {
        String checkSql = "SELECT xwiki_version FROM pages WHERE xwiki_id = ?";
        String dbWikiVersion = null;
        try {
            dbWikiVersion = jdbcTemplate.queryForObject(checkSql, String.class, page.getId());
        } catch (EmptyResultDataAccessException e) {
            log.debug("Запись для страницы {} не найдена в таблице pages. Продолжаем обработку.", page.getId());
        }

        if (dbWikiVersion != null && dbWikiVersion.equals(page.getVersion())) {
            log.debug("Для страницы {} версия {} уже актуальна в базе. Пропускаем обработку.", page.getId(), page.getVersion());
            return true;
        }
        return false;
    }

    /**
     * Выполняет поиск по базе знаний с использованием новой схемы (pages, chunks, embeddings).
     * Для каждого embedding-моделя генерируется вектор запроса, после чего выполняется динамический SQL‑запрос
     * для получения top релевантных результатов.
     *
     * @param query текст поискового запроса
     * @param limit максимальное количество результатов для каждой модели
     * @return объединённый список DTO DocumentEmbeddingDto найденных документов
     */
    public List<DocumentEmbeddingDto> search(String query, int limit) {
        // Нормализуем входной запрос
        String normalizedQuery = contentNormalizationService.normalize(query);

        // 1. Для каждого embedding-моделя создаем вектор запроса
        // Ключ: имя модели, значение: PGvector запроса, полученного с помощью llamaAiClient.getEmbeddings
        Map<String, PGvector> queryVectors = new HashMap<>();
        for (EmbeddingModelConfigRecord modelConfig : modelsProperties.embeddingModels()) {
            String modelName = modelConfig.model();
            try {
                EmbeddingResponse embeddingResponse = llamaAiClient.getEmbeddings(normalizedQuery, modelName);
                List<Embedding> results = embeddingResponse.getResults();
                if (results == null || results.isEmpty()) {
                    log.warn("Не удалось получить эмбеддинг для запроса '{}' с моделью {}", query, modelName);
                    continue;
                }
                // Берем первый эмбеддинг
                Embedding embedding = results.getFirst();
                List<Double> output = embedding.getOutput();
                float[] vector = new float[output.size()];
                for (int i = 0; i < output.size(); i++) {
                    vector[i] = output.get(i).floatValue();
                }
                float[] normalizedVector = normalizeVector(vector);
                PGvector queryVector = new PGvector(normalizedVector);
                queryVectors.put(modelName, queryVector);
            } catch (Exception ex) {
                log.error("Ошибка генерации эмбеддинга для модели {} при поиске запроса '{}'", modelName, query, ex);
            }
        }

        // 2. Для каждого embedding-моделя выполняем запрос к новым таблицам
        // Используем динамическое имя колонки, вычисляемое по модели: [modelName] -> заменяем не-латинские/цифровые символы на _
        String sqlTemplate = "SELECT p.xwiki_id, c.chunk_index, e.%s AS embedding, c.text_snippet, " +
                "       e.%s <-> ? AS distance " +
                "FROM pages p " +
                "JOIN chunks c ON c.page_id = p.id " +
                "JOIN embeddings e ON e.chunk_id = c.id " +
                "ORDER BY distance ASC " +
                "LIMIT ?";

        List<DocumentEmbeddingDto> combinedResults = new ArrayList<>();

        for (Map.Entry<String, PGvector> entry : queryVectors.entrySet()) {
            String modelName = entry.getKey();
            PGvector queryVector = entry.getValue();

            // Вычисляем динамическое имя колонки, например: qllama/multilingual-e5-base -> qllama_multilingual_e5_base_embedding
            String columnName = modelName.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
            String sql = String.format(sqlTemplate, columnName, columnName);

            List<DocumentEmbeddingDto> modelResults = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> {
                        // Извлекаем xwikiId и chunk_index из таблицы pages и chunks
                        String xwikiId = rs.getString("xwiki_id");
                        int chunkIndex = rs.getInt("chunk_index");

                        // Читаем значение эмбеддинга, преобразуя объект в PGvector
                        Object embeddingObj = rs.getObject("embedding");
                        PGvector embeddingVector;
                        if (embeddingObj instanceof PGvector) {
                            embeddingVector = (PGvector) embeddingObj;
                        } else if (embeddingObj instanceof org.postgresql.util.PGobject pgObj) {
                            embeddingVector = new PGvector(pgObj.getValue());
                        } else {
                            throw new IllegalStateException("Невозможно преобразовать объект "
                                    + embeddingObj.getClass() + " в PGvector");
                        }

                        String textSnippet = rs.getString("text_snippet");
                        double distance = rs.getDouble("distance");
                        return new DocumentEmbeddingDto(xwikiId, chunkIndex, embeddingVector, textSnippet, distance);
                    },
                    queryVector, limit
            );

            combinedResults.addAll(modelResults);
            modelResults.forEach(dto ->
                    log.info("Найден документ {} с расстоянием {} для модели {}",
                            dto.getXwikiId(), dto.getDistance(), modelName)
            );
        }

        // 3. Сортируем объединенный список результатов по возрастанию расстояния
        combinedResults.sort(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance));

        // При необходимости расширяем текстовый фрагмент (например, добавляем контекст)
        combinedResults.forEach(dto -> dto.setTextSnippet(getExtendedTextSnippet(dto)));

        return combinedResults;
    }

    private String getExtendedTextSnippet(DocumentEmbeddingDto dto) {
        return dto.getTextSnippet();
    }

}
