package ru.cbgr.adapter.xwiki.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.SearchResultDto;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
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

    private boolean isBusinessSpace(Space space) {
        return SYSTEM_SPACES_PREFIXES.stream().noneMatch(space.getId()::startsWith);
    }
}
