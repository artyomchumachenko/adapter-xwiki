package ru.cbgr.adapter.xwiki.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;
import ru.cbgr.adapter.xwiki.utils.XWikiLinkResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiSpaceWalker {

    private static final String REL_PAGES = "http://www.xwiki.org/rel/pages";

    private final XWikiClient xWikiClient;
    private final XWikiLinkResolver linkResolver;

    public void walk(Space space, Consumer<PageSummary> pageConsumer) {
        log.debug("Обрабатываем пространство: {}", space.getId());

        linkResolver.getHref(space.getLinks(), REL_PAGES)
                .map(xWikiClient::getPages)
                .map(PagesResponse::getPageSummaries)
                .stream()
                .flatMap(List::stream)
                .forEach(pageConsumer);

        Optional.ofNullable(space.getSpaces())
                .stream()
                .flatMap(List::stream)
                .forEach(child -> walk(child, pageConsumer));
    }
}
