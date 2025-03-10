package ru.cbgr.adapter.xwiki.service;

import java.util.List;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final JdbcTemplate jdbcTemplate;
    private final LlamaAiService llamaAiService;
    private final ContentNormalizationService contentNormalizationService;

    /**
     * Выполняет поиск по базе знаний.
     * Преобразует поисковый запрос в эмбеддинг, затем находит наиболее похожие записи.
     *
     * @param query текст поискового запроса
     * @param limit количество возвращаемых результатов
     * @return список xwiki_absolute_url найденных страниц
     */
    public List<String> search(String query, int limit) {
        // Получаем эмбеддинг запроса через LlamaAiService
        EmbeddingResponse embeddingResponse = llamaAiService.getEmbeddings(contentNormalizationService.normalize(query));
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
        PGvector queryVector = new PGvector(vector);

        double threshold = 0.6; // Соответствует точности 0.8 (cosine similarity)
        String sql = "SELECT xwiki_absolute_url FROM page_embeddings " +
                "WHERE embedding <=> ? <= ? " +
                "ORDER BY embedding <=> ? ASC " +
                "LIMIT ?";

        List<String> results = jdbcTemplate.query(
                sql,
                new Object[]{ queryVector, threshold, queryVector, limit },
                (rs, rowNum) -> rs.getString("xwiki_absolute_url")
        );

        return results;
    }
}
