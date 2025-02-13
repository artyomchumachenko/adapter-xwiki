package ru.cbgr.adapter.xwiki.deserializer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import ru.cbgr.adapter.xwiki.dto.xwiki.PagesResponse;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PagesResponseDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String json = """
            {
                "links": [],
                "pageSummaries": [
                    {
                        "links": [
                            {
                                "href": "http://localhost:8080/rest/wikis/xwiki/spaces/%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D0%BD%D0%B0%D1%8F%20%D0%B0%D0%BD%D0%B0%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0",
                                "rel": "http://www.xwiki.org/rel/space",
                                "type": null,
                                "hrefLang": null
                            }
                        ],
                        "id": "xwiki:Системная аналитика.WebHome",
                        "fullName": "Системная аналитика.WebHome",
                        "wiki": "xwiki",
                        "space": "Системная аналитика",
                        "name": "WebHome",
                        "title": "Системная аналитика",
                        "rawTitle": "Системная аналитика",
                        "parent": "Main.WebHome",
                        "parentId": "xwiki:Main.WebHome",
                        "version": "1.1",
                        "author": "XWiki.AAChumachenko",
                        "authorName": null,
                        "xwikiRelativeUrl": "http://localhost:8080/bin/view/%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D0%BD%D0%B0%D1%8F%20%D0%B0%D0%BD%D0%B0%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0/",
                        "xwikiAbsoluteUrl": "http://localhost:8080/bin/view/%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D0%BD%D0%B0%D1%8F%20%D0%B0%D0%BD%D0%B0%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0/",
                        "syntax": "xwiki/2.1"
                    }
                ]
            }
            """;

    @Test
    void testDeserialization() throws Exception {
        PagesResponse response = objectMapper.readValue(json, PagesResponse.class);

        assertNotNull(response);
        assertNotNull(response.getPageSummaries());
        assertEquals(1, response.getPageSummaries().size());

        PageSummary summary = response.getPageSummaries().get(0);
        assertEquals("xwiki:Системная аналитика.WebHome", summary.getId());
        assertEquals("Системная аналитика.WebHome", summary.getFullName());
        assertEquals("xwiki", summary.getWiki());
        assertEquals("Системная аналитика", summary.getSpace());
        assertEquals("WebHome", summary.getName());
        assertEquals("Системная аналитика", summary.getTitle());
        assertEquals("1.1", summary.getVersion());
        assertEquals("XWiki.AAChumachenko", summary.getAuthor());
        assertEquals("xwiki/2.1", summary.getSyntax());

        assertNotNull(summary.getLinks());
        assertEquals(1, summary.getLinks().size());

        Link link = summary.getLinks().get(0);
        assertEquals("http://localhost:8080/rest/wikis/xwiki/spaces/%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D0%BD%D0%B0%D1%8F%20%D0%B0%D0%BD%D0%B0%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0", link.getHref());
        assertEquals("http://www.xwiki.org/rel/space", link.getRel());
    }

    @Test
    void testDecode() {
        // Пример закодированной строки: "Системная аналитика", закодированный через UTF-8
        String encoded = "http://localhost:8080/rest/wikis/xwiki/spaces/%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D0%BD%D0%B0%D1%8F%20%D0%B0%D0%BD%D0%B0%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0/spaces/%D0%98%D0%BD%D1%82%D0%B5%D0%B3%D1%80%D0%B0%D1%86%D0%B8%D0%B8/pages?media=json";

        // Раскодируем:
        String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        System.out.println(decoded);
        // Выведет: "Системная аналитика"
    }
}
