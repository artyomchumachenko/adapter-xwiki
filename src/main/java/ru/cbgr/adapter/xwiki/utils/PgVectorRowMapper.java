package ru.cbgr.adapter.xwiki.utils;

import com.pgvector.PGvector;
import org.springframework.stereotype.Component;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class PgVectorRowMapper {

    public DocumentEmbeddingDto map(ResultSet rs) throws SQLException {
        String xwikiId = rs.getString("xwiki_id");
        int chunkIndex = rs.getInt("chunk_index");

        Object embeddingObj = rs.getObject("embedding");
        PGvector embeddingVector;
        if (embeddingObj instanceof PGvector v) {
            embeddingVector = v;
        } else if (embeddingObj instanceof org.postgresql.util.PGobject pgObj) {
            embeddingVector = new PGvector(pgObj.getValue());
        } else {
            throw new IllegalStateException("Невозможно преобразовать объект "
                    + embeddingObj.getClass() + " в PGvector");
        }

        String textSnippet = rs.getString("text_snippet");
        double distance = rs.getDouble("distance");
        return new DocumentEmbeddingDto(xwikiId, chunkIndex, embeddingVector, textSnippet, distance);
    }
}
