package ru.cbgr.adapter.xwiki.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pgvector.PGvector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEmbeddingDto {
    private String xwikiId;
    @JsonIgnore
    private Integer chunkIndex;
    @JsonIgnore
    private PGvector embedding;
    private String textSnippet;
    @JsonIgnore
    private double distance;
}
