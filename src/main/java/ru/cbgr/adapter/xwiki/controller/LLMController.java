package ru.cbgr.adapter.xwiki.controller;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class LLMController {

    private final LlamaAiClient llamaAiClient;

    @GetMapping
    public ResponseEntity<ChatResponse> chat(@RequestParam(value = "message") String promptMessage) {
        return ResponseEntity.ok(llamaAiClient.generateResult(promptMessage));
    }
}
