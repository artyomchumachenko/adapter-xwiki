package ru.cbgr.adapter.xwiki.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pgvector.PGvector;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEmbeddingDto {
    private String xwikiId;
    private Integer chunkIndex;
    @JsonIgnore
    private PGvector embedding;
    private String textSnippet;
    private double distance;
}
