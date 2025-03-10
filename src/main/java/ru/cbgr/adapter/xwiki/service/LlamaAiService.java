package ru.cbgr.adapter.xwiki.service;

import java.util.ArrayList;
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

    public static final String MODEL_VERSION = "bambucha/saiga-llama3:8b";

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
     * @return EmbeddingResponse с векторами текста
     */
    public EmbeddingResponse getEmbeddings(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Входное сообщение не должно быть пустым.");
        }

        final int MAX_CHUNK_SIZE = 2048; // максимальное количество символов в одном куске, которое может воспринять LLM
        List<String> chunks = getChunks(message, MAX_CHUNK_SIZE);

        // Формируем запрос на эмбеддинг, передавая список кусочков сообщения и указывая нужную модель
        EmbeddingRequest request = new EmbeddingRequest(
                chunks,
                OllamaOptions.create().withModel(MODEL_VERSION)
        );

        // Вызываем модель для получения эмбеддингов и возвращаем ответ
        return embeddingModel.call(request);
    }

    private List<String> getChunks(String message, int MAX_CHUNK_SIZE) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        // Разбиваем текст на куски, не разрывая слово (по возможности)
        while (start < message.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, message.length());

            // Если мы не достигли конца текста, попробуем найти последний пробел в пределах лимита,
            // чтобы не разрывать слово
            if (end < message.length()) {
                int lastSpace = message.lastIndexOf(" ", end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String chunk = message.substring(start, end);
            chunks.add(chunk);
            start = end;
        }
        return chunks;
    }

}
