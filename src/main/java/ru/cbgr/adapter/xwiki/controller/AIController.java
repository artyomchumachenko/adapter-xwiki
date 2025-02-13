package ru.cbgr.adapter.xwiki.controller;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.service.LlamaAiService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final LlamaAiService llamaAiService;

    @GetMapping("/generate")
    public ResponseEntity<ChatResponse> generate(@RequestParam(value = "message") String promptMessage) {
        return ResponseEntity.ok(llamaAiService.generateResult(promptMessage));
    }

    @GetMapping("/embed")
    public ResponseEntity<EmbeddingResponse> embed(@RequestParam(value = "message") String message) {
        return ResponseEntity.ok(llamaAiService.getEmbeddings(message));
    }
}
