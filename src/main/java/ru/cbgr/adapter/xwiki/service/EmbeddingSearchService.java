package ru.cbgr.adapter.xwiki.service;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingSearchService {

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

        Map<String, List<DocumentEmbeddingDto>> allModelResults = fetchAllModels(queryVectors, limit);

        // --- объединение результатов (как в исходнике) ---
        Map<String, Double> combinedScores = new HashMap<>();
        Map<String, DocumentEmbeddingDto> bestDtoByPage = new HashMap<>();

        for (Map.Entry<String, List<DocumentEmbeddingDto>> entry : allModelResults.entrySet()) {
            List<DocumentEmbeddingDto> resultsForModel = entry.getValue();
            resultsForModel.sort(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance));

            // лучший чанк на страницу для данной модели
            Map<String, DocumentEmbeddingDto> bestPerPageForModel = new LinkedHashMap<>();
            for (DocumentEmbeddingDto dto : resultsForModel) {
                bestPerPageForModel.merge(dto.getXwikiId(), dto,
                        (a, b) -> a.getDistance() <= b.getDistance() ? a : b);
            }

            List<DocumentEmbeddingDto> topNForModel = bestPerPageForModel.values().stream()
                    .sorted(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance))
                    .limit(limit)
                    .toList();

            for (int i = 0; i < topNForModel.size(); i++) {
                DocumentEmbeddingDto dto = topNForModel.get(i);
                double score = (double) (limit - i) / (double) limit; // 1.0 .. (1/limit)
                String pageId = dto.getXwikiId();

                combinedScores.merge(pageId, score, Double::sum);
                bestDtoByPage.merge(pageId, dto,
                        (oldDto, newDto) -> oldDto.getDistance() <= newDto.getDistance() ? oldDto : newDto);
            }
        }

        return combinedScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> bestDtoByPage.get(e.getKey()))
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
                    log.warn("Не удалось получить эмбеддинг для запроса '{}' с моделью {}", rawQueryForLogs, modelName);
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

    private Map<String, List<DocumentEmbeddingDto>> fetchAllModels(Map<String, PGvector> queryVectors, int limit) {
        String sqlTemplate = """
                SELECT p.xwiki_id, c.chunk_index, e.%s AS embedding, c.text_snippet,
                       e.%s <-> ? AS distance
                FROM pages p
                JOIN chunks c ON c.page_id = p.id
                JOIN embeddings e ON e.chunk_id = c.id
                ORDER BY distance ASC
                LIMIT ?
                """;

        Map<String, List<DocumentEmbeddingDto>> allModelResults = new HashMap<>();

        for (Map.Entry<String, PGvector> entry : queryVectors.entrySet()) {
            String modelName = entry.getKey();
            PGvector queryVector = entry.getValue();

            String columnName = columnNameResolver.toEmbeddingColumn(modelName);
            String sql = String.format(sqlTemplate, columnName, columnName);

            List<DocumentEmbeddingDto> modelResults = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> pgVectorRowMapper.map(rs),
                    queryVector, limit
            );

            allModelResults.put(modelName, modelResults);

            modelResults.forEach(dto ->
                    log.info("Найден документ {} с расстоянием {} для модели {}",
                            dto.getXwikiId(), dto.getDistance(), modelName)
            );
        }

        return allModelResults;
    }
}
