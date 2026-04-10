package ru.cbgr.adapter.xwiki.client;

import java.util.List;
import java.util.Optional;

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
    private final Optional<OllamaEmbeddingModel> embeddingModel;

    private final ModelsProperties properties;

    public ChatResponse generateResult(String prompt) {
        return chatModel.call(
                new Prompt(
                        prompt,
                        OllamaOptions.create().withModel(properties.llmModel())
                ));
    }

    public EmbeddingResponse getEmbeddings(String chunk, String modelName) {
        log.debug("Получение векторов для: {}", chunk);
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("Входной chunk не должен быть пустым.");
        }

        if (embeddingModel.isEmpty()) {
            throw new IllegalStateException(
                    "OllamaEmbeddingModel недоступен. Установите AI_EMBEDDING_ENABLED=true для embedding-вызовов."
            );
        }

        EmbeddingRequest request = new EmbeddingRequest(
                List.of(chunk),
                OllamaOptions.create().withModel(modelName)
        );

        return embeddingModel.get().call(request);
    }
}
