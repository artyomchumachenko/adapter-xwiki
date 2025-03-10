package ru.cbgr.adapter.xwiki.service;

import java.util.List;
import java.util.Optional;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.chunker.CombinedContentChunker;
import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.ModificationsResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiService { // todo Добавить мапперы

    private static final List<String> EXPECTED_IDS_FOR_PROCESS_ALL = List.of(
            "xwiki:Дивизион разработки.MDM.Системный анализ.Макет списочной формы каталогов"
    );
    private static final Boolean ALL_SPACE_PROCESSING = Boolean.FALSE;

    private final XWikiClient xWikiClient;
    private final ContentNormalizationService contentNormalizationService;
    private final LlamaAiService llamaAiService;

    private final JdbcTemplate jdbcTemplate;

    private final CombinedContentChunker chunker;

    public ModificationsResponse getModifications() {
        return xWikiClient.getModifications();
    }

    /**
     * Обходит все пространства, полученные по /rest/wikis/xwiki/spaces,
     * и для каждого пространства обрабатывает страницы.
     */
    public void processAllSpacesAndPages() {
        SpacesResponse spacesResponse = xWikiClient.getSpaces();
        if (spacesResponse == null || spacesResponse.getSpaces() == null) {
            log.warn("Нет пространств для обработки.");
            return;
        }
        for (Space space : spacesResponse.getSpaces()) {
            if (space.getId().startsWith("xwiki:Help") || space.getId().startsWith("xwiki:Main") || space.getId().startsWith("xwiki:Sandbox") || space.getId().startsWith("xwiki:XWiki")) continue;

            // Обрабатываем только конкретное пространство
            if (!ALL_SPACE_PROCESSING && !EXPECTED_IDS_FOR_PROCESS_ALL.contains(space.getId())) continue;

            processSpace(space);
        }
    }

    /**
     * Обрабатывает отдельное пространство:
     * – извлекает ссылку на список страниц (rel = "http://www.xwiki.org/rel/pages"),
     * – обходит все страницы,
     * – если есть вложенные пространства, обрабатывает их рекурсивно.
     */
    private void processSpace(Space space) {
        log.info("Обрабатываем пространство: {}", space.getId());
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
     * Обрабатывает страницу:
     * – получает подробную информацию о странице в виде объекта PageDetails,
     * – извлекает только поле content,
     * – нормализует текст,
     * – генерирует эмбеддинг.
     */
    private void processPage(PageSummary page) {
        log.info("Обрабатываем страницу: {}", page.getId());

        Optional<Link> detailLinkOpt = page.getLinks().stream()
                .filter(link -> "http://www.xwiki.org/rel/page".equals(link.getRel()))
                .findFirst();

        if (detailLinkOpt.isEmpty()) {
            log.warn("Не найдена ссылка для получения подробной информации для страницы: {}", page.getId());
            return;
        }

        // Используем полученный URL напрямую, без дополнительного кодирования
        String detailUrl = detailLinkOpt.get().getHref();
        log.debug("Получаем данные страницы по URL: {}", detailUrl);

        // Получаем подробности страницы (например, объект PageDetails), где содержится поле content
        PageDetails pageDetails = xWikiClient.getPageDetails(detailUrl);
        if (pageDetails == null || pageDetails.getContent() == null || pageDetails.getContent().isEmpty()) {
            log.warn("Поле content пустое для страницы: {}", page.getId());
            return;
        }

        String content = pageDetails.getContent();
        List<String> chunks = chunker.chunkContent(content, 2048, 5, 10);

        // Обработка каждого чанка
        for (String chunk : chunks) {
            // Нормализуем чанк
            String normalizedChunk = contentNormalizationService.normalize(chunk);
            // Генерируем эмбеддинги для нормализованного текста
            EmbeddingResponse embeddingResponse = llamaAiService.getEmbeddings(normalizedChunk);

            List<Embedding> embeddings = embeddingResponse.getResults();
            for (Embedding embedding : embeddings) {
                List<Double> output = embedding.getOutput();
                float[] vector = new float[output.size()];
                for (int i = 0; i < output.size(); i++) {
                    vector[i] = output.get(i).floatValue();
                }
                // Создаем объект PGvector
                PGvector pgVector = new PGvector(vector);

                // Сохраняем эмбеддинг в базу данных
                String sql = "INSERT INTO page_embeddings (embedding, xwiki_absolute_url) VALUES (?, ?)";
                jdbcTemplate.update(sql, pgVector, page.getXwikiAbsoluteUrl());
            }
        }
    }
}
