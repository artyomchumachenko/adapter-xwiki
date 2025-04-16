package ru.cbgr.adapter.xwiki.client;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import ru.cbgr.adapter.xwiki.configuration.properties.XWikiProperties;
import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.SpacesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageDetails;

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
}
