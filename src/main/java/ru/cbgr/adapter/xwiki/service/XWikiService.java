package ru.cbgr.adapter.xwiki.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.SearchResultDto;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;
import ru.cbgr.adapter.xwiki.model.Page;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.repository.PageRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiService {

    private static final List<String> SYSTEM_SPACES_PREFIXES = List.of(
            "xwiki:Help", "xwiki:Main", "xwiki:Sandbox", "xwiki:XWiki"
    );

    private final XWikiClient xWikiClient;
    private final XWikiSpaceWalker spaceWalker;
    private final PageProcessor pageProcessor;
    private final EmbeddingSearchService embeddingSearchService;
    private final PageRepository pageRepository;

    /** Обходит все пространства и запускает обработку страниц. */
    public void processAllSpacesAndPages() {
        Optional.ofNullable(xWikiClient.getSpaces())
                .map(SpacesResponse::getSpaces)
                .stream()
                .flatMap(List::stream)
                .filter(this::isBusinessSpace)
                .forEach(space -> spaceWalker.walk(space, pageProcessor::processPage));
    }

    /** Поиск (логика сохранена, но вынесена в отдельный сервис). */
    public List<DocumentEmbeddingDto> search(String query, int limit) {
        return embeddingSearchService.search(query, limit);
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
                .toList();
    }

    public void forceProcessPage(String pageId) {
        PageSummary summary = resolvePageSummary(pageId);
        if (summary == null) {
            throw new EntityNotFoundException("XWiki page not found, id: " + pageId);
        }
        pageProcessor.processPageForce(summary);
    }

    private PageSummary resolvePageSummary(String pageIdOrFullName) {
        if (pageIdOrFullName == null || pageIdOrFullName.isBlank()) {
            throw new IllegalArgumentException("pageId must not be blank");
        }

        String value = pageIdOrFullName.trim();
        if (value.contains("/rest/wikis/")) {
            PageSummary fromUrl = resolveFromRestUrl(value);
            if (fromUrl != null) {
                return fromUrl;
            }
        }

        if (value.contains("/")) {
            PageSummary fromPath = resolveFromSpacePath(value);
            if (fromPath != null) {
                return fromPath;
            }
        }

        if (value.contains(".") || value.contains(":")) {
            String withoutWiki = value;
            int wikiSep = withoutWiki.indexOf(':');
            if (wikiSep >= 0) {
                withoutWiki = withoutWiki.substring(wikiSep + 1);
            }

            int lastDot = withoutWiki.lastIndexOf('.');
            if (lastDot > 0 && lastDot < withoutWiki.length() - 1) {
                String space = withoutWiki.substring(0, lastDot);
                String page = withoutWiki.substring(lastDot + 1);
                return xWikiClient.getPageSummaryBySpaceAndName(space, page);
            }
        }

        return xWikiClient.getPageSummary(value);
    }

    private PageSummary resolveFromRestUrl(String url) {
        int idx = url.indexOf("/rest/wikis/");
        if (idx < 0) {
            return null;
        }

        String tail = url.substring(idx + "/rest/wikis/".length());
        String[] parts = tail.split("/");
        if (parts.length < 4) {
            return null;
        }

        int i = 1; // skip wiki name
        List<String> spaces = new java.util.ArrayList<>();
        String page = null;

        while (i < parts.length) {
            String marker = parts[i];
            if ("spaces".equals(marker) && i + 1 < parts.length) {
                spaces.add(decodeSegment(parts[i + 1]));
                i += 2;
                continue;
            }
            if ("pages".equals(marker) && i + 1 < parts.length) {
                page = decodeSegment(parts[i + 1]);
                break;
            }
            i++;
        }

        if (page == null) {
            return null;
        }

        return xWikiClient.getPageSummaryBySpacePath(spaces, page);
    }

    private PageSummary resolveFromSpacePath(String path) {
        String trimmed = path;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        String[] parts = trimmed.split("/");
        if (parts.length < 2) {
            return null;
        }

        String page = decodeSegment(parts[parts.length - 1]);
        List<String> spaces = new java.util.ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isBlank()) {
                spaces.add(decodeSegment(parts[i]));
            }
        }

        if (spaces.isEmpty() || page.isBlank()) {
            return null;
        }

        return xWikiClient.getPageSummaryBySpacePath(spaces, page);
    }

    private String decodeSegment(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isBusinessSpace(Space space) {
        return SYSTEM_SPACES_PREFIXES.stream().noneMatch(space.getId()::startsWith);
    }
}
