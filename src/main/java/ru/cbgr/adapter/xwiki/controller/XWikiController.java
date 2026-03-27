package ru.cbgr.adapter.xwiki.controller;

import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.SearchResultDto;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.service.XWikiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/xwiki")
@RequiredArgsConstructor
@Slf4j
public class XWikiController {

    private final XWikiService xWikiService;
    private final LlamaAiClient llamaAiClient;

    @GetMapping("/processAll")
    public ResponseEntity<String> processAllSpacesAndPages() {
        xWikiService.processAllSpacesAndPages();
        return ResponseEntity.ok("Обработка пространств и страниц запущена.");
    }

    @PostMapping("/processPage")
    public ResponseEntity<String> processPageForce(@RequestParam String pageId) {
        xWikiService.forceProcessPage(pageId);
        return ResponseEntity.ok("Forced processing started for pageId=" + pageId);
    }


    /**
     * Endpoint для умного поиска по базе знаний.
     * Принимает поисковый запрос и возвращает список URL страниц, наиболее похожих по векторной близости.
     *
     * @param query текст поискового запроса
     * @param limit максимальное число результатов (по умолчанию 5)
     * @return ResponseEntity со списком URL
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchResultDto>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {

        List<DocumentEmbeddingDto> urls = xWikiService.search(query, limit);
        List<SearchResultDto> result = xWikiService.getLinkResults(urls);
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint для генерации ответа на основе поискового запроса.
     * Берется наиболее релевантный найденный результат, его text_snippet используется для обогащения запроса,
     * после чего сформированный prompt отправляется в чат-модель для генерации ответа.
     *
     * @param query текст поискового запроса пользователя
     * @return ResponseEntity с ответом от чат-модели
     */
    @GetMapping("/search/answer")
    public ResponseEntity<String> generateAnswer(@RequestParam String query) {
        // Выполняем поиск по базе знаний (ограничиваем, например, 5 результатами)
        List<DocumentEmbeddingDto> dtos = xWikiService.search(query, 5);
        if (dtos.isEmpty()) {
            return ResponseEntity.ok("Извините, не удалось найти подходящую информацию.");
        }

        // Берем первый (наиболее релевантный) результат
        DocumentEmbeddingDto bestMatch = dtos.getFirst();
        String contextSnippet = bestMatch.getTextSnippet();

        // Формируем prompt для чат-модели.
        // Пример хорошего prompt-а: предоставляем контекст и четко формулируем вопрос.
        String prompt = buildPrompt(query, contextSnippet);
        log.info("Context snippet for answer: {}", contextSnippet);

        // Отправляем prompt в чат-модель и получаем ответ
        ChatResponse answer = llamaAiClient.generateResult(prompt);
        return ResponseEntity.ok(answer.getResult().getOutput().getContent());
    }

    /**
     * Формирует prompt для чат-модели, комбинируя контекст из найденного чанка и исходный запрос пользователя.
     *
     * @param query   исходный запрос пользователя
     * @param context контекст из text_snippet наиболее релевантного результата
     * @return сформированный prompt
     */
    private String buildPrompt(String query, String context) {
        return String.format(
                "Дан следующий контекст: %s\n\nНа основе приведенной информации, пожалуйста, ответьте на вопрос в точности сохранив логику из контекста:\n%s",
                context,
                query
        );
    }
}
