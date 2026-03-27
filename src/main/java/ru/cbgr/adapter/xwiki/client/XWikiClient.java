package ru.cbgr.adapter.xwiki.client;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import ru.cbgr.adapter.xwiki.configuration.properties.XWikiProperties;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class XWikiClient {

    private final XWikiProperties xWikiProperties;
    private final RestTemplate xWikiRestTemplate;

    /**
     * Получает список пространств.
     */
    public SpacesResponse getSpaces() {
        String url = xWikiProperties.getBaseUrl() + "/rest/wikis/xwiki/spaces";
        ResponseEntity<SpacesResponse> response = xWikiRestTemplate.getForEntity(url, SpacesResponse.class);
        return response.getBody();
    }

    /**
     * Получает список страниц по URL, полученному из ссылки XWiki.
     * URL используется «как есть» (уже закодирован).
     */
    public PagesResponse getPages(String pagesUrl) {
        URI uri = UriComponentsBuilder
                .fromUriString(pagesUrl)
                .queryParam("media", "json")
                .build(true)
                .toUri();
        return xWikiRestTemplate.getForObject(uri, PagesResponse.class);
    }

    /**
     * Получает подробную информацию о странице в виде объекта PageDetails.
     */
    public PageDetails getPageDetails(String pageUrl) {
        URI uri = UriComponentsBuilder
                .fromUriString(pageUrl)
                .queryParam("media", "json")
                .build(true)
                .toUri();
        return xWikiRestTemplate.getForObject(uri, PageDetails.class);
    }

    /**
     * Fetch a single page summary by id for forced processing.
     */
    public PageSummary getPageSummary(String pageId) {
        URI uri = UriComponentsBuilder
                .fromUriString(xWikiProperties.getBaseUrl())
                .path("/rest/wikis/xwiki/pages/{pageId}")
                .queryParam("media", "json")
                .buildAndExpand(pageId)
                .encode()
                .toUri();
        return xWikiRestTemplate.getForObject(uri, PageSummary.class);
    }

    public PageSummary getPageSummaryBySpaceAndName(String spaceName, String pageName) {
        URI uri = UriComponentsBuilder
                .fromUriString(xWikiProperties.getBaseUrl())
                .path("/rest/wikis/xwiki/spaces/{space}/pages/{page}")
                .queryParam("media", "json")
                .buildAndExpand(spaceName, pageName)
                .encode()
                .toUri();
        return xWikiRestTemplate.getForObject(uri, PageSummary.class);
    }

    public PageSummary getPageSummaryBySpacePath(List<String> spacePath, String pageName) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(xWikiProperties.getBaseUrl())
                .pathSegment("rest", "wikis", "xwiki");

        for (String space : spacePath) {
            builder = builder.pathSegment("spaces", space);
        }

        URI uri = builder
                .pathSegment("pages", pageName)
                .queryParam("media", "json")
                .build()
                .encode()
                .toUri();
        return xWikiRestTemplate.getForObject(uri, PageSummary.class);
    }
}
