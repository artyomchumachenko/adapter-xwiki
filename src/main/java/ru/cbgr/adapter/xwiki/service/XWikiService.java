package ru.cbgr.adapter.xwiki.service;

import com.pgvector.PGvector;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.cbgr.adapter.xwiki.chunker.CombinedContentChunker;
import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SearchResultDto;
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

import java.sql.PreparedStatement;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiService {

    /* ======================  CONSTANTS  ====================== */

    private static final String REL_PAGES = "http://www.xwiki.org/rel/pages";
    private static final String REL_PAGE_DETAILS = "http://www.xwiki.org/rel/page";

    private static final List<String> SYSTEM_SPACES_PREFIXES = List.of(
            "xwiki:Help", "xwiki:Main", "xwiki:Sandbox", "xwiki:XWiki"
    );

    private static final String DELETE_EMBEDDINGS_SQL =
            "DELETE FROM embeddings WHERE chunk_id IN (SELECT id FROM chunks WHERE page_id = ?)";

    private static final String INSERT_EMBEDDING_SQL =
            "INSERT INTO embeddings (chunk_id) VALUES (?)";

    private static final String SELECT_VERSION_SQL =
            "SELECT xwiki_version FROM pages WHERE xwiki_id = ?";

    /* ======================  DEPENDENCIES  ====================== */

    private final XWikiClient xWikiClient;
    private final LlamaAiClient llamaAiClient;
    private final PageRepository pageRepository;
    private final ChunkRepository chunkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ContentNormalizationService contentNormalizationService;
    private final VectorNormalizationService vectorNormalizationService;
    private final CombinedContentChunker chunker;
    private final ModelsProperties modelsProperties;

    /* ======================  ENTRY POINTS  ====================== */

    /** Обходит все пространства и запускает обработку страниц. */
    @Transactional
    public void processAllSpacesAndPages() {
        Optional.ofNullable(xWikiClient.getSpaces())
                .map(SpacesResponse::getSpaces)
                .stream()
                .flatMap(List::stream)
                .filter(this::isBusinessSpace)
                .forEach(this::processSpace);
    }

    /** Поисковый метод (оставлен без изменений значимых частей). */
    public List<DocumentEmbeddingDto> search(String query, int limit) {
        // ... (см. оригинальный метод, т.к. логика без изменений) ...
        return originalSearch(query, limit);
    }

    /* ======================  SPACE PROCESSING  ====================== */

    @Transactional
    protected void processSpace(Space space) {
        log.debug("Обрабатываем пространство: {}", space.getId());

        getLinkHref(space.getLinks(), REL_PAGES)
                .map(xWikiClient::getPages)
                .map(PagesResponse::getPageSummaries)
                .stream()
                .flatMap(List::stream)
                .forEach(this::processPage);

        Optional.ofNullable(space.getSpaces())
                .stream()
                .flatMap(List::stream)
                .forEach(this::processSpace);
    }

    /* ======================  PAGE PROCESSING  ====================== */

    @Transactional
    protected void processPage(PageSummary summary) {
        if (isSkipProcess(summary)) return;

        Page page = pageRepository.findByXwikiId(summary.getId())
                .map(existing -> updateMeta(existing, summary))
                .orElseGet(() -> createMeta(summary));

        List<String> chunks = loadAndChunkContent(summary);
        if (chunks.isEmpty()) {
            log.warn("Контент страницы {} пуст – пропуск.", summary.getId());
            return;
        }

        if (!page.isNew()) {
            cleanupOldData(page);
        }

        persistChunksAndEmbeddings(page, chunks);
    }

    /* ======================  HELPERS  ====================== */

    /** Проверка, является ли пространство бизнесовым. */
    private boolean isBusinessSpace(Space space) {
        return SYSTEM_SPACES_PREFIXES.stream().noneMatch(space.getId()::startsWith);
    }

    /** Обновление существующей страницы. */
    private Page updateMeta(Page page, PageSummary summary) {
        page.setXwikiVersion(summary.getVersion());
        page.setXwikiAbsoluteUrl(summary.getXwikiAbsoluteUrl());
        Page updated = pageRepository.save(page);
        log.debug("Обновлена страница {}.", updated.getId());
        return updated;
    }

    /** Создание новой страницы. */
    private Page createMeta(PageSummary summary) {
        Page page = new Page();
        page.setTitle(summary.getTitle());
        page.setXwikiId(summary.getId());
        page.setXwikiVersion(summary.getVersion());
        page.setXwikiAbsoluteUrl(summary.getXwikiAbsoluteUrl());
        Page saved = pageRepository.save(page);
        saved.markNew();       // << небольшой приём, чтобы отличать только‑что созданный объект
        log.debug("Создана новая страница {}.", saved.getId());
        return saved;
    }

    /** Загрузка содержимого страницы и разбиение на чанки. */
    private List<String> loadAndChunkContent(PageSummary summary) {
        return getLinkHref(summary.getLinks(), REL_PAGE_DETAILS)
                .map(xWikiClient::getPageDetails)
                .map(PageDetails::getContent)
                .filter(content -> content != null && !content.isBlank())
                .map(content -> chunker.chunkContent(content))
                .orElse(List.of());
    }

    /** Удаляет старые чанки и эмбеддинги, связанные со страницей. */
    private void cleanupOldData(Page page) {
        jdbcTemplate.update(DELETE_EMBEDDINGS_SQL, page.getId());
        List<Chunk> toDelete = chunkRepository.findByPage(page);
        if (!toDelete.isEmpty()) chunkRepository.deleteAll(toDelete);
        log.debug("Очищены старые чанки/эмбеддинги для страницы {}.", page.getId());
    }

    /** Сохраняет чанки и эмбеддинги в БД. */
    private void persistChunksAndEmbeddings(Page page, List<String> chunks) {
        for (int idx = 0; idx < chunks.size(); idx++) {
            String normalized = contentNormalizationService.normalize(chunks.get(idx));
            if (normalized.isEmpty()) continue;

            Chunk savedChunk = chunkRepository.save(new Chunk(page, idx, normalized));
            long embeddingId = insertEmptyEmbedding(savedChunk.getId());

            modelsProperties.embeddingModels()
                    .forEach(model -> saveEmbeddingForModel(model, normalized, embeddingId));
        }
    }

    /** Вставка пустой строки в `embeddings` (возвращает ID записи). */
    private long insertEmptyEmbedding(long chunkId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(INSERT_EMBEDDING_SQL, new String[]{"id"});
            ps.setLong(1, chunkId);
            return ps;
        }, keyHolder);
        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow(() -> new IllegalStateException("Не удалось вставить embedding для chunk " + chunkId));
    }

    /** Сохраняет эмбеддинг нормализованного текста для конкретной модели. */
    private void saveEmbeddingForModel(EmbeddingModelConfigRecord modelCfg,
            String text, long embeddingId) {

        String modelName = modelCfg.model();
        try {
            Embedding embedding = llamaAiClient.getEmbeddings(text, modelName)
                    .getResults()
                    .stream()
                    .findFirst()
                    .orElseThrow();
            float[] vector = toFloatArray(embedding.getOutput());
            float[] normalized = vectorNormalizationService.normalizeVector(modelCfg.index(), vector);
            PGvector pgVector = new PGvector(normalized);

            String column = modelName.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
            jdbcTemplate.update("UPDATE embeddings SET " + column + " = ? WHERE id = ?", pgVector, embeddingId);

            log.debug("Эмбеддинг модели {} сохранён в колонку {} (embedding_id={}).", modelName, column, embeddingId);

        } catch (Exception e) {
            log.error("Ошибка генерации эмбеддинга для модели {} (embedding_id={}).", modelName, embeddingId, e);
        }
    }

    /* ======================  UTILS  ====================== */

    private Optional<Link> getLink(List<Link> links, String rel) {
        return links == null ? Optional.empty()
                : links.stream().filter(l -> rel.equals(l.getRel())).findFirst();
    }

    private Optional<String> getLinkHref(List<Link> links, String rel) {
        return getLink(links, rel).map(Link::getHref);
    }

    private float[] toFloatArray(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
        return arr;
    }

    /* ======================  VERSION CHECK  ====================== */

    public boolean isSkipProcess(PageSummary page) {
        String currentVersion = null;
        try {
            currentVersion = jdbcTemplate.queryForObject(SELECT_VERSION_SQL, String.class, page.getId());
        } catch (EmptyResultDataAccessException ignored) { }

        boolean skip = Objects.equals(currentVersion, page.getVersion());
        if (skip) log.debug("Версия страницы {} уже актуальна ({}). Пропуск.", page.getId(), currentVersion);
        return skip;
    }

    /* ======================  SEARCH (оригинал)  ====================== */

    @SuppressWarnings("DuplicatedCode") // продублирован из оригинала ради неизменности логики
    private List<DocumentEmbeddingDto> originalSearch(String query, int limit) {
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
                float[] normalizedVector = vectorNormalizationService.normalizeVector(modelConfig.index(), vector);
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

        // Собираем результаты для каждой модели
        Map<String, List<DocumentEmbeddingDto>> allModelResults = new HashMap<>();
        for (Map.Entry<String, PGvector> entry : queryVectors.entrySet()) {
            String modelName = entry.getKey();
            PGvector queryVector = entry.getValue();

            // Вычисляем динамическое имя колонки, например: qllama/multilingual-e5-base -> qllama_multilingual_e5_base_embedding
            String columnName = modelName.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
            String sql = String.format(sqlTemplate, columnName, columnName);

            List<DocumentEmbeddingDto> modelResults = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> {
                        String xwikiId = rs.getString("xwiki_id");
                        int chunkIndex = rs.getInt("chunk_index");

                        // Преобразуем объект из БД в PGvector
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
            allModelResults.put(modelName, modelResults);
            modelResults.forEach(dto ->
                    log.info("Найден документ {} с расстоянием {} для модели {}",
                            dto.getXwikiId(), dto.getDistance(), modelName)
            );
        }

        // 1. Для каждой модели выбираем топ-5 уникальных результатов (уникальность по xwikiId и chunkIndex)
        // и присваиваем им оценку на основе позиции в рейтинге (от 1.0 до 0.2)
        Map<String, Double> combinedScores = new HashMap<>();
        Map<String, DocumentEmbeddingDto> resultByUniqueKey = new HashMap<>();

        for (Map.Entry<String, List<DocumentEmbeddingDto>> entry : allModelResults.entrySet()) {
            String modelName = entry.getKey();
            List<DocumentEmbeddingDto> resultsForModel = entry.getValue();

            // Сортируем результаты по возрастанию расстояния (лучшие записи впереди)
            resultsForModel.sort(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance));

            // Извлекаем уникальные результаты (если имеются дубликаты по xwikiId+chunkIndex, оставляем первый)
            Map<String, DocumentEmbeddingDto> uniqueForModel = resultsForModel.stream()
                    .collect(Collectors.toMap(
                            dto -> dto.getXwikiId() + "_" + dto.getChunkIndex(),
                            Function.identity(),
                            (dto1, dto2) -> dto1,
                            LinkedHashMap::new)); // LinkedHashMap сохраняет порядок

            // Берем первые 5 уникальных записей
            List<DocumentEmbeddingDto> top5ForModel = uniqueForModel.values().stream().limit(5).toList();

            // Для каждой записи вычисляем оценку, зависящую от её позиции в top5 (например, 1.0, 0.8, 0.6, 0.4, 0.2)
            for (int i = 0; i < top5ForModel.size(); i++) {
                DocumentEmbeddingDto dto = top5ForModel.get(i);
                double score = (5 - i) / 5.0; // Ранг 0 => 1.0, 1 => 0.8, 2 => 0.6, 3 => 0.4, 4 => 0.2

                String uniqueKey = dto.getXwikiId() + "_" + dto.getChunkIndex();
                // Суммируем оценку, если такая запись уже встречалась от другой модели
                combinedScores.merge(uniqueKey, score, Double::sum);
                // Сохраняем DTO (при повторном появлении той же записи оставляем первое встретившееся)
                resultByUniqueKey.putIfAbsent(uniqueKey, dto);
            }
        }

        // 2. Выбираем 5 записей с наивысшей суммарной оценкой
        List<DocumentEmbeddingDto> finalResults = combinedScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> resultByUniqueKey.get(entry.getKey()))
                .collect(Collectors.toList());

        // При необходимости можно выполнить доработку – например, расширить текстовый фрагмент каждого результата
//        finalResults.forEach(dto -> dto.setTextSnippet(getExtendedTextSnippet(dto)));

        return finalResults;
    }

    public List<SearchResultDto> getLinkResults(List<DocumentEmbeddingDto> urls) {
        return urls.stream()
                .map(u -> {
                    Page page = pageRepository.findByXwikiId(u.getXwikiId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Page not found, xwiki_id: " + u.getXwikiId()
                            ));
                    return new SearchResultDto(
                            page.getTitle(),
                            page.getXwikiAbsoluteUrl(),
                            u.getTextSnippet(),
                            page.getXwikiId(),
                            page.getXwikiVersion()
                    );
                })
                .collect(Collectors.toList());
    }
}
