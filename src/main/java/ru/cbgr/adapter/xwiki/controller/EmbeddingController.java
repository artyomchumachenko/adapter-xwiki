package ru.cbgr.adapter.xwiki.controller;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final LlamaAiClient llamaAiClient;

    @GetMapping
    public ResponseEntity<EmbeddingResponse> embed(@RequestParam(value = "message") String message) {
        return ResponseEntity.ok(llamaAiClient.getEmbeddings(message));
    }
}
