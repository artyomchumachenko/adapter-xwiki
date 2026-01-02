package ru.cbgr.adapter.xwiki.service;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pgvector.PGvector;

import org.postgresql.util.PGobject;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;
import ru.cbgr.adapter.xwiki.utils.VectorNormalizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedSemanticSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ModelsProperties modelsProperties;
    private final LlamaAiClient llamaAiClient;
    private final ContentNormalizationService contentNormalizationService;
    private final VectorNormalizationService vectorNormalizationService;

    // ---- Тюнинговые параметры (можно вынести в application.yml) ----
    private static final int   TOPM_MULTIPLIER   = 8;     // сколько кандидатов брать на модель до объединения
    private static final double ABS_MARGIN       = 0.05;  // абсолютное окно к лучшему distance
    private static final double REL_MARGIN       = 0.15;  // относительное окно к лучшему distance (15%)
    private static final double PERCENTILE_CUT   = 0.75;  // перцентиль по distance для отсечки
    private static final double SOFTMAX_T        = 0.10;  // температура softmax для взвешивания кандидатов
    private static final int    MIN_FALLBACK     = 5;     // минимум кандидатов на модель при «жёстком» срезе

    /**
     * Поиск с улучшениями: фильтры плохих кандидатов, нормализация метрик, softmax-слияние и дедуп по странице.
     */
    public List<DocumentEmbeddingDto> searchEnhanced(String query, int limit) {
        String normalizedQuery = contentNormalizationService.normalize(query);

        // 1) Получаем векторы запроса для каждой модели
        Map<EmbeddingModelConfigRecord, PGvector> queryVectors = buildQueryVectors(normalizedQuery);

        if (queryVectors.isEmpty()) {
            log.warn("Не удалось получить эмбеддинги запроса ни для одной модели");
            return List.of();
        }

        // 2) Для каждой модели получаем topM кандидатов (лучший чанк на страницу) и фильтруем «хвост»
        int topM = Math.max(limit * TOPM_MULTIPLIER, limit);
        Map<String, List<ScoredCandidate>> perModelCandidates = new LinkedHashMap<>();
        for (Map.Entry<EmbeddingModelConfigRecord, PGvector> e : queryVectors.entrySet()) {
            EmbeddingModelConfigRecord model = e.getKey();
            PGvector qv = e.getValue();

            List<DocumentEmbeddingDto> raw = fetchBestChunkPerPage(model, qv, topM);
            if (raw.isEmpty()) continue;

            // сортируем по distance (возрастание)
            raw.sort(Comparator.comparingDouble(DocumentEmbeddingDto::getDistance));

            // фильтрация: margin + перцентиль
            List<DocumentEmbeddingDto> filtered = filterByMarginsAndPercentile(raw);
            if (filtered.isEmpty()) {
                filtered = raw.stream().limit(Math.min(MIN_FALLBACK, raw.size())).toList();
            }

            // перевод distance -> similarity (с учётом опса) и min-max нормализация по списку модели
            List<ScoredCandidate> scored = toNormalizedScored(model, filtered);

            perModelCandidates.put(model.model(), scored);
        }

        if (perModelCandidates.isEmpty()) {
            log.warn("После фильтрации кандидатов не осталось");
            return List.of();
        }

        // 3) Слияние моделей через softmax (по нормализованным similarity)
        Map<String, Double> pageScore = new HashMap<>();                 // pageId -> суммарный вес
        Map<String, DocumentEmbeddingDto> bestDtoByPage = new HashMap<>(); // pageId -> лучший dto (для сниппета)

        for (Map.Entry<String, List<ScoredCandidate>> e : perModelCandidates.entrySet()) {
            List<ScoredCandidate> list = e.getValue();
            if (list.isEmpty()) continue;

            double sumExp = 0.0;
            double[] exps = new double[list.size()];

            for (int i = 0; i < list.size(); i++) {
                double v = Math.exp(list.get(i).simNorm / SOFTMAX_T);
                exps[i] = v;
                sumExp += v;
            }
            double denom = Math.max(1e-9, sumExp);

            for (int i = 0; i < list.size(); i++) {
                ScoredCandidate sc = list.get(i);
                double w = exps[i] / denom; // 0..1
                String pageId = sc.dto.getXwikiId();

                pageScore.merge(pageId, w, Double::sum);
                // выбираем лучший dto на страницу (по минимальному distance)
                bestDtoByPage.merge(pageId, sc.dto,
                        (a, b) -> a.getDistance() <= b.getDistance() ? a : b);
            }
        }

        if (pageScore.isEmpty()) {
            return List.of();
        }

        // 4) Финальный топ по страницам и возврат лучших DTO
        List<String> topPageIds = pageScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        List<DocumentEmbeddingDto> result = new ArrayList<>(topPageIds.size());
        for (String pid : topPageIds) {
            DocumentEmbeddingDto dto = bestDtoByPage.get(pid);
            if (dto != null) result.add(dto);
        }
        return result;
    }

    // ------------------------ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ------------------------

    private Map<EmbeddingModelConfigRecord, PGvector> buildQueryVectors(String normalizedQuery) {
        Map<EmbeddingModelConfigRecord, PGvector> map = new LinkedHashMap<>();
        for (EmbeddingModelConfigRecord modelConfig : modelsProperties.embeddingModels()) {
            String modelName = modelConfig.model();
            try {
                EmbeddingResponse resp = llamaAiClient.getEmbeddings(normalizedQuery, modelName);
                List<Embedding> results = resp.getResults();
                if (results == null || results.isEmpty()) {
                    log.warn("Нет эмбеддинга для запроса '{}' (модель {})", normalizedQuery, modelName);
                    continue;
                }
                Embedding emb = results.getFirst();
                List<Double> out = emb.getOutput();
                float[] vec = new float[out.size()];
                for (int i = 0; i < out.size(); i++) vec[i] = out.get(i).floatValue();

                // нормализация вектора под индекс (cosine/l2)
                float[] normalized = vectorNormalizationService.normalizeVector(modelConfig.index(), vec);
                map.put(modelConfig, new PGvector(normalized));
            } catch (Exception ex) {
                log.error("Ошибка генерации эмбеддинга для модели {}: {}", modelName, ex.getMessage(), ex);
            }
        }
        return map;
    }

    /**
     * SQL: лучший чанк на страницу (rn=1), отсортирован по distance, limit=topM.
     */
    private List<DocumentEmbeddingDto> fetchBestChunkPerPage(EmbeddingModelConfigRecord model,
            PGvector queryVector,
            int topM) {
        String column = sanitizeModelColumn(model.model());

        String sql = """
                WITH ranked AS (
                  SELECT p.id AS page_id, p.xwiki_id, c.chunk_index, e.%1$s AS embedding, c.text_snippet,
                         e.%1$s <-> ? AS distance,
                         ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY e.%1$s <-> ? ASC) AS rn
                  FROM pages p
                  JOIN chunks c ON c.page_id = p.id
                  JOIN embeddings e ON e.chunk_id = c.id
                )
                SELECT xwiki_id, chunk_index, embedding, text_snippet, distance
                FROM ranked
                WHERE rn = 1
                ORDER BY distance ASC
                LIMIT ?;
                """.formatted(column);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRowToDto(rs),
                queryVector, queryVector, topM
        );
    }

    private DocumentEmbeddingDto mapRowToDto(ResultSet rs) throws java.sql.SQLException {
        String xwikiId = rs.getString("xwiki_id");
        int chunkIndex = rs.getInt("chunk_index");

        Object embeddingObj = rs.getObject("embedding");
        PGvector embVec;
        if (embeddingObj instanceof PGvector pg) {
            embVec = pg;
        } else if (embeddingObj instanceof PGobject pgObj) {
            embVec = new PGvector(pgObj.getValue());
        } else {
            throw new IllegalStateException("Не могу преобразовать " + embeddingObj.getClass() + " в PGvector");
        }

        String textSnippet = rs.getString("text_snippet");
        double distance = rs.getDouble("distance");

        return new DocumentEmbeddingDto(xwikiId, chunkIndex, embVec, textSnippet, distance);
    }

    private List<DocumentEmbeddingDto> filterByMarginsAndPercentile(List<DocumentEmbeddingDto> sortedByDistanceAsc) {
        if (sortedByDistanceAsc.isEmpty()) return List.of();
        double best = sortedByDistanceAsc.get(0).getDistance();

        List<Double> ds = sortedByDistanceAsc.stream()
                .map(DocumentEmbeddingDto::getDistance)
                .sorted()
                .toList();

        int n = ds.size();
        int idx = (int) Math.floor(PERCENTILE_CUT * (n - 1));
        double perc = ds.get(Math.max(0, Math.min(idx, n - 1)));

        List<DocumentEmbeddingDto> out = new ArrayList<>();
        for (DocumentEmbeddingDto dto : sortedByDistanceAsc) {
            double d = dto.getDistance();
            boolean byMargin = (d - best) <= ABS_MARGIN || ((d - best) / Math.max(best, 1e-9)) <= REL_MARGIN;
            boolean byPercentile = d <= perc;
            if (byMargin && byPercentile) out.add(dto);
        }
        return out;
    }

    private List<ScoredCandidate> toNormalizedScored(EmbeddingModelConfigRecord model,
            List<DocumentEmbeddingDto> filtered) {
        // distance -> similarity (с учётом типа индекса)
        List<Double> sims = filtered.stream()
                .map(dto -> toSimilarity(dto.getDistance(), model.index()))
                .toList();

        List<Double> simsNorm = minMaxNormalize(sims);

        List<ScoredCandidate> scored = new ArrayList<>(filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            ScoredCandidate sc = new ScoredCandidate(filtered.get(i),
                    sims.get(i), simsNorm.get(i), model.model(), model.index());
            scored.add(sc);
        }
        return scored;
    }

    private String sanitizeModelColumn(String modelName) {
        return modelName.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
    }

    private double toSimilarity(double distance, String indexType) {
        if ("vector_cosine_ops".equalsIgnoreCase(indexType)) {
            // cosine distance = 1 - cos_sim
            return clamp01(1.0 - distance);
        } else if ("vector_l2_ops".equalsIgnoreCase(indexType)) {
            // монотонная нормализация L2 в (0..1]
            return 1.0 / (1.0 + distance);
        }
        // дефолт
        return 1.0 / (1.0 + distance);
    }

    private List<Double> minMaxNormalize(List<Double> vals) {
        double min = vals.stream().min(Double::compare).orElse(0.0);
        double max = vals.stream().max(Double::compare).orElse(1.0);
        double denom = Math.max(1e-9, max - min);
        return vals.stream().map(v -> (v - min) / denom).toList();
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // простая обёртка для хранения доп. сигналов
    private record ScoredCandidate(
            DocumentEmbeddingDto dto,
            double simRaw,
            double simNorm,
            String modelName,
            String indexType
    ) { }
}
