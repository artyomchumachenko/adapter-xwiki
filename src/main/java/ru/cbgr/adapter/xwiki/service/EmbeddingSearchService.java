package ru.cbgr.adapter.xwiki.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.utils.EmbeddingColumnNameResolver;
import ru.cbgr.adapter.xwiki.utils.FloatVectorMapper;
import ru.cbgr.adapter.xwiki.utils.PgVectorRowMapper;
import ru.cbgr.adapter.xwiki.utils.VectorNormalizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingSearchService {

    // === Applied improvements configuration ===
    private static final int CANDIDATE_MULTIPLIER = 20; // Improvement #2
    private static final int RRF_K = 60;                // Improvement #3 (typical 50-60)

    private final JdbcTemplate jdbcTemplate;
    private final ContentNormalizationService contentNormalizationService;
    private final VectorNormalizationService vectorNormalizationService;
    private final ModelsProperties modelsProperties;
    private final LlamaAiClient llamaAiClient;
    private final EmbeddingColumnNameResolver columnNameResolver;
    private final PgVectorRowMapper pgVectorRowMapper;

    public List<DocumentEmbeddingDto> search(String query, int limit) {
        String normalizedQuery = contentNormalizationService.normalize(query);

        Map<String, PGvector> queryVectors = buildQueryVectors(normalizedQuery, query);
        if (queryVectors.isEmpty()) {
            return List.of();
        }

        // Improvement #2: use larger candidate pool per model
        int candidateLimit = Math.max(limit, limit * CANDIDATE_MULTIPLIER);

        // Each model returns: best (top) chunk per page, sorted by distance
        Map<String, List<DocumentEmbeddingDto>> allModelResults = fetchAllModels(queryVectors, candidateLimit);

        // Improvement #3: RRF fusion across models
        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, DocumentEmbeddingDto> bestDtoByPage = new HashMap<>();

        for (Map.Entry<String, List<DocumentEmbeddingDto>> entry : allModelResults.entrySet()) {
            String modelName = entry.getKey();
            List<DocumentEmbeddingDto> results = entry.getValue();
            if (results == null || results.isEmpty()) {
                continue;
            }

            // Defensive: ensure sorted (SQL already sorts)
            results.sort(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance));

            for (int i = 0; i < results.size(); i++) {
                int rank = i + 1; // 1-based
                DocumentEmbeddingDto dto = results.get(i);

                String pageId = dto.getXwikiId();
                double rrf = 1.0d / (RRF_K + rank);

                fusedScores.merge(pageId, rrf, Double::sum);

                // keep "best evidence" (minimal distance across all models)
                bestDtoByPage.merge(pageId, dto,
                        (oldDto, newDto) -> oldDto.getDistance() <= newDto.getDistance() ? oldDto : newDto);
            }

            if (log.isDebugEnabled()) {
                log.debug("Model {} produced {} per-page candidates (candidateLimit={})",
                        modelName, results.size(), candidateLimit);
            }
        }

        // Final ranking:
        // 1) by fused RRF score DESC
        // 2) tie-breaker: best distance ASC (more similar)
        return fusedScores.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Double.compare(b.getValue(), a.getValue());
                    if (cmp != 0) return cmp;

                    DocumentEmbeddingDto da = bestDtoByPage.get(a.getKey());
                    DocumentEmbeddingDto db = bestDtoByPage.get(b.getKey());
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return Double.compare(da.getDistance(), db.getDistance());
                })
                .limit(limit)
                .map(e -> bestDtoByPage.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, PGvector> buildQueryVectors(String normalizedQuery, String rawQueryForLogs) {
        Map<String, PGvector> queryVectors = new HashMap<>();

        for (EmbeddingModelConfigRecord modelConfig : modelsProperties.embeddingModels()) {
            String modelName = modelConfig.model();
            try {
                EmbeddingResponse embeddingResponse = llamaAiClient.getEmbeddings(normalizedQuery, modelName);
                List<Embedding> results = embeddingResponse.getResults();
                if (results == null || results.isEmpty()) {
                    log.warn("Не удалось получить эмбеддинг для запроса '{}' с моделью {}",
                            rawQueryForLogs, modelName);
                    continue;
                }

                Embedding embedding = results.getFirst();
                float[] vector = FloatVectorMapper.toFloatArray(embedding.getOutput());
                float[] normalizedVector = vectorNormalizationService.normalizeVector(modelConfig.index(), vector);

                queryVectors.put(modelName, new PGvector(normalizedVector));
            } catch (Exception ex) {
                log.error("Ошибка генерации эмбеддинга для модели {} при поиске запроса '{}'",
                        modelName, rawQueryForLogs, ex);
            }
        }

        return queryVectors;
    }

    /**
     * Improvement #1: per-page deduplication in SQL (best chunk per page),
     * returning candidates sorted by distance.
     * Important: this query returns ONE row per page using row_number().
     */
    private Map<String, List<DocumentEmbeddingDto>> fetchAllModels(Map<String, PGvector> queryVectors, int limit) {

        // Note: distance expression used twice (SELECT and ORDER BY in window); passed twice as parameters.
        String sqlTemplate = """
                WITH ranked AS (
                    SELECT
                        p.xwiki_id,
                        c.chunk_index,
                        e.%s AS embedding,
                        c.text_snippet,
                        (e.%s <-> ?) AS distance,
                        ROW_NUMBER() OVER (
                            PARTITION BY p.xwiki_id
                            ORDER BY (e.%s <-> ?) ASC
                        ) AS rn
                    FROM pages p
                    JOIN chunks c ON c.page_id = p.id
                    JOIN embeddings e ON e.chunk_id = c.id
                )
                SELECT
                    xwiki_id,
                    chunk_index,
                    embedding,
                    text_snippet,
                    distance
                FROM ranked
                WHERE rn = 1
                ORDER BY distance ASC
                LIMIT ?
                """;

        Map<String, List<DocumentEmbeddingDto>> allModelResults = new HashMap<>();

        for (Map.Entry<String, PGvector> entry : queryVectors.entrySet()) {
            String modelName = entry.getKey();
            PGvector queryVector = entry.getValue();

            String columnName = columnNameResolver.toEmbeddingColumn(modelName);
            String sql = String.format(sqlTemplate, columnName, columnName, columnName);

            List<DocumentEmbeddingDto> modelResults = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> pgVectorRowMapper.map(rs),
                    // params order:
                    queryVector, // distance
                    queryVector, // window ORDER BY distance
                    limit        // LIMIT
            );

            allModelResults.put(modelName, modelResults);

            if (log.isDebugEnabled()) {
                for (DocumentEmbeddingDto dto : modelResults) {
                    log.debug("Model {}: xwikiId={}, distance={}", modelName, dto.getXwikiId(), dto.getDistance());
                }
            }
        }

        return allModelResults;
    }
}
