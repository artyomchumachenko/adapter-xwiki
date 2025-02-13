package ru.cbgr.adapter.xwiki.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingsProcessorService { // todo Проанализировать ответы от xwiki api

    private final XWikiClient xWikiClient;

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

        // Вызываем getPageDetails только если URL заканчивается на "/WebHome"
        /*if (!detailUrl.endsWith("/WebHome")) {
            log.info("Ссылка {} не заканчивается на /WebHome, пропускаем обработку страницы: {}", detailUrl, page.getId());
            return;
        }*/

        // Получаем подробности страницы (например, объект PageDetails), где содержится поле content
        PageDetails pageDetails = xWikiClient.getPageDetails(detailUrl);
        if (pageDetails == null || pageDetails.getContent() == null || pageDetails.getContent().isEmpty()) {
            log.warn("Поле content пустое для страницы: {}", page.getId());
            return;
        }

        String content = pageDetails.getContent();
        log.info(content);

        // Нормализуем content, генерируем эмбеддинг и так далее
//        String normalizedText = textPreprocessingService.preprocess(content);
//        float[] embedding = embeddingGenerationService.generateEmbedding(normalizedText);
//        log.info("Embedding: {}", Arrays.toString(embedding));
        // Если требуется – переходим к следующей странице
    }
}
