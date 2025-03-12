package ru.cbgr.adapter.xwiki.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.service.SearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Endpoint для умного поиска по базе знаний.
     * Принимает поисковый запрос и возвращает список URL страниц, наиболее похожих по векторной близости.
     *
     * @param query текст поискового запроса
     * @param limit максимальное число результатов (по умолчанию 5)
     * @return ResponseEntity со списком URL
     */
    @GetMapping
    public ResponseEntity<List<DocumentEmbeddingDto>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {

        List<DocumentEmbeddingDto> urls = searchService.search(query, limit);
        return ResponseEntity.ok(urls);
    }
}
