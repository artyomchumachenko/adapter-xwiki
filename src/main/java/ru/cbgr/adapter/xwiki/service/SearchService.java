package ru.cbgr.adapter.xwiki.service;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.mapper.DocumentEmbeddingMapper;
import ru.cbgr.adapter.xwiki.model.DocumentEmbedding;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final JdbcTemplate jdbcTemplate;
    private final LlamaAiClient llamaAiClient;
    private final ContentNormalizationService contentNormalizationService;

    /**
     * Выполняет поиск по базе знаний.
     * Преобразует поисковый запрос в эмбеддинг, нормализует его, затем находит наиболее похожие записи.
     *
     * @param query текст поискового запроса
     * @param limit количество возвращаемых результатов
     * @return список DTO DocumentEmbeddingDto найденных документов с вычисленным расстоянием
     */
    public List<DocumentEmbeddingDto> search(String query, int limit) {
        // Получаем эмбеддинг запроса через LlamaAiService
        EmbeddingResponse embeddingResponse = llamaAiClient.getEmbeddings(
                contentNormalizationService.normalize(query));
        List<Embedding> embeddings = embeddingResponse.getResults();
        if (embeddings == null || embeddings.isEmpty()) {
            log.warn("Не удалось получить эмбеддинг для запроса: {}", query);
            return List.of();
        }

        // Берем первый эмбеддинг (если их несколько)
        List<Double> output = embeddings.getFirst().getOutput();
        float[] vector = new float[output.size()];
        for (int i = 0; i < output.size(); i++) {
            vector[i] = output.get(i).floatValue();
        }
        // Нормализуем вектор запроса
        float[] normalizedVector = normalizeVector(vector);
        PGvector queryVector = new PGvector(normalizedVector);

        // SQL-запрос возвращает также вычисленное расстояние между векторами
        String sql = "SELECT id, document_id, chunk_index, embedding, text_snippet, created_at, updated_at, " +
                "       embedding <-> ? AS distance " +
                "FROM document_embeddings " +
                "ORDER BY distance ASC " +
                "LIMIT ?";

        List<DocumentEmbeddingDto> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    DocumentEmbedding entity = new DocumentEmbedding();
                    entity.setId(rs.getLong("id"));
                    entity.setDocumentId(rs.getString("document_id"));
                    entity.setChunkIndex(rs.getInt("chunk_index"));

                    // Получаем объект embedding и преобразуем его в PGvector
                    Object embeddingObj = rs.getObject("embedding");
                    PGvector embedding;
                    if (embeddingObj instanceof PGvector) {
                        embedding = (PGvector) embeddingObj;
                    } else if (embeddingObj instanceof org.postgresql.util.PGobject pgObj) {
                        embedding = new PGvector(pgObj.getValue());
                    } else {
                        throw new IllegalStateException("Невозможно преобразовать объект " + embeddingObj.getClass() + " в PGvector");
                    }
                    entity.setEmbedding(embedding);
                    entity.setTextSnippet(rs.getString("text_snippet"));
                    entity.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    entity.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

                    double distance = rs.getDouble("distance");
                    log.info("Найден документ {} с расстоянием: {}", entity.getDocumentId(), distance);
                    return DocumentEmbeddingMapper.toDtoWithDistance(entity, distance);
                },
                queryVector, limit
        );

        // Расширяем контекст каждого найденного результата
        for (DocumentEmbeddingDto dto : results) {
            dto.setTextSnippet(getExtendedTextSnippet(dto));
        }

        return results;
    }

    /**
     * Расширяет text_snippet, добавляя соседние чанки, до достижения 1000 символов.
     * Используется TreeMap для хранения чанков с сортировкой по индексу,
     * что обеспечивает правильный порядок объединения фрагментов.
     *
     * @param dto DTO с информацией о найденном документе и индексом чанка
     * @return расширенный текстовый фрагмент, длина которого не меньше 1000 символов,
     *         либо максимально возможный, если соседние чанки отсутствуют
     */
    private String getExtendedTextSnippet(DocumentEmbeddingDto dto) {
        final int TARGET_LENGTH = 2000;
        // Используем TreeMap для хранения чанков: ключ – chunk_index, значение – текст чанка
        TreeMap<Integer, String> chunksMap = new TreeMap<>();
        int currentIndex = dto.getChunkIndex();
        // Добавляем исходный чанк
        chunksMap.put(currentIndex, dto.getTextSnippet());

        // Инициируем указатели для предыдущего и следующего чанка
        int prevIndex = currentIndex - 1;
        int nextIndex = currentIndex + 1;

        // Собираем расширенный текст из текущего содержимого чанков
        String extendedText = joinChunks(chunksMap);

        // Расширяем, пока суммарная длина текста меньше TARGET_LENGTH
        while (extendedText.length() < TARGET_LENGTH) {
            boolean added = false;

            // Пытаемся добавить предыдущий чанк (если он существует)
            String prevSnippet = fetchChunkSnippet(dto.getDocumentId(), prevIndex);
            if (prevSnippet != null && !prevSnippet.isBlank()) {
                chunksMap.put(prevIndex, prevSnippet);
                prevIndex--;
                added = true;
            }
            extendedText = joinChunks(chunksMap);
            if (extendedText.length() >= TARGET_LENGTH) break;

            // Пытаемся добавить следующий чанк (если он существует)
            String nextSnippet = fetchChunkSnippet(dto.getDocumentId(), nextIndex);
            if (nextSnippet != null && !nextSnippet.isBlank()) {
                chunksMap.put(nextIndex, nextSnippet);
                nextIndex++;
                added = true;
            }
            extendedText = joinChunks(chunksMap);

            // Если ни один соседний чанк не найден, прерываем расширение
            if (!added) {
                break;
            }
        }
        return extendedText.trim();
    }

    /**
     * Объединяет тексты чанков в единый текстовый фрагмент в порядке возрастания chunk_index.
     *
     * @param chunksMap TreeMap с чанками
     * @return объединённая строка
     */
    private String joinChunks(TreeMap<Integer, String> chunksMap) {
        return chunksMap.values().stream()
                .collect(Collectors.joining(" "));
    }

    /**
     * Получает текстовый фрагмент для заданного документа и индекса чанка.
     *
     * @param documentId идентификатор документа
     * @param chunkIndex индекс чанка
     * @return текстовый фрагмент или null, если чанк не найден
     */
    private String fetchChunkSnippet(String documentId, int chunkIndex) {
        String sql = "SELECT text_snippet FROM document_embeddings WHERE document_id = ? AND chunk_index = ?";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, documentId, chunkIndex);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * Нормализует вектор, приводя его к единичной длине (L2-норма).
     *
     * @param vector исходный вектор
     * @return нормализованный вектор
     */
    private float[] normalizeVector(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) return vector; // Предотвращаем деление на ноль
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / (float) norm;
        }
        return normalized;
    }

}
