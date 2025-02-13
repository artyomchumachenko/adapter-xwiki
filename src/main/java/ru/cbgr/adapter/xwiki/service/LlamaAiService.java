package ru.cbgr.adapter.xwiki.service;

import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlamaAiService { // todo Добавить мапперы

    public static final String MODEL_VERSION = "llama3.2:3b";

    private final OllamaChatModel chatModel;
    private final OllamaEmbeddingModel embeddingModel;

    /**
     * Отправка запроса в большую языковую модель /api/chat
     * @param prompt Сообщение (запрос) в LLM
     * @return Ответ от LLM
     */
    public ChatResponse generateResult(String prompt) {
        ChatResponse response = chatModel.call(
                new Prompt(
                        prompt,
                        OllamaOptions.create()
                                .withModel(MODEL_VERSION)
                ));

        return response;
    }

    /**
     * Получение embedding`ов для входящего текста (векторов текста)
     * @param message Входные данные
     */
    public EmbeddingResponse getEmbeddings(String message) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(
                        List.of(message),
                        OllamaOptions.create()
                                .withModel(MODEL_VERSION)
                ));

        return response;
    }
}
