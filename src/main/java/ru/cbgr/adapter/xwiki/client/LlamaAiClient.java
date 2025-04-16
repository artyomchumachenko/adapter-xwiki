package ru.cbgr.adapter.xwiki.client;

import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlamaAiClient {

    private final OllamaChatModel chatModel;
    private final OllamaEmbeddingModel embeddingModel;

    private final ModelsProperties properties;

    public ChatResponse generateResult(String prompt) {
        ChatResponse response = chatModel.call(
                new Prompt(
                        prompt,
                        OllamaOptions.create()
                                .withModel(properties.llmModel())
                ));

        return response;
    }

    public EmbeddingResponse getEmbeddings(String chunk, String modelName) {
        log.debug("Получение векторов для: {}", chunk);
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("Входной chunk не должен быть пустым.");
        }

        EmbeddingRequest request = new EmbeddingRequest(
                List.of(chunk),
                OllamaOptions.create().withModel(modelName)
        );

        return embeddingModel.call(request);
    }
}
