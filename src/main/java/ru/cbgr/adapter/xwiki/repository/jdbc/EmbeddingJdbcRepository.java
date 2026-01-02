package ru.cbgr.adapter.xwiki.repository.jdbc;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmbeddingJdbcRepository {

    private static final String DELETE_EMBEDDINGS_SQL =
            "DELETE FROM embeddings WHERE chunk_id IN (SELECT id FROM chunks WHERE page_id = ?)";

    private static final String INSERT_EMBEDDING_SQL =
            "INSERT INTO embeddings (chunk_id) VALUES (?)";

    private final JdbcTemplate jdbcTemplate;

    public void deleteEmbeddingsByPageId(long pageId) {
        jdbcTemplate.update(DELETE_EMBEDDINGS_SQL, pageId);
    }

    public long insertEmptyEmbedding(long chunkId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(INSERT_EMBEDDING_SQL, new String[]{"id"});
            ps.setLong(1, chunkId);
            return ps;
        }, keyHolder);

        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow(() -> new IllegalStateException("Не удалось вставить embedding для chunk " + chunkId));
    }

    public void updateEmbeddingVector(long embeddingId, String columnName, PGvector vector) {
        // columnName формируется только через resolver (whitelist по regex), поэтому безопасно для подстановки.
        String sql = "UPDATE embeddings SET " + columnName + " = ? WHERE id = ?";
        jdbcTemplate.update(sql, vector, embeddingId);
    }
}
