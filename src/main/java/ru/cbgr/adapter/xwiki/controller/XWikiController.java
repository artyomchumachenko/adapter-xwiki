package ru.cbgr.adapter.xwiki.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.dto.xwiki.ModificationsResponse;
import ru.cbgr.adapter.xwiki.service.XWikiService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class XWikiController {

    private final XWikiService xWikiService;

    /**
     * Эндпоинт для получения агрегированной истории изменений из XWiki.
     * Пример запроса: GET http://localhost:8080/api/wiki/modifications
     *
     * @return JSON-ответ с полями links и historySummaries.
     */
    @GetMapping("/modifications")
    public ResponseEntity<ModificationsResponse> getModifications() {
        ModificationsResponse modifications = xWikiService.getModifications();
        return ResponseEntity.ok(modifications);
    }

    @GetMapping("/processAll")
    public ResponseEntity<String> processAllSpacesAndPages() {
        xWikiService.processAllSpacesAndPages();
        return ResponseEntity.ok("Обработка пространств и страниц запущена.");
    }
}
