package ru.cbgr.adapter.xwiki.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.cbgr.adapter.xwiki.chunker.CombinedContentChunker;
import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.utils.XWikiLinkResolver;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PageContentService {

    private static final String REL_PAGE_DETAILS = "http://www.xwiki.org/rel/page";

    private final XWikiClient xWikiClient;
    private final XWikiLinkResolver linkResolver;
    private final CombinedContentChunker chunker;
    private final ContentNormalizationService contentNormalizationService;

    public List<String> loadAndChunkContent(PageSummary summary) {
        return linkResolver.getHref(summary.getLinks(), REL_PAGE_DETAILS)
                .map(xWikiClient::getPageDetails)
                .map(PageDetails::getContent)
                .filter(content -> !content.isBlank())
                // ВАЖНО: сначала чистим HTML/приводим к тексту, потом чанкуем
                .map(contentNormalizationService::normalizeForChunking)
                .map(chunker::chunkContent)
                .orElse(List.of());
    }
}
