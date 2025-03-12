package ru.cbgr.adapter.xwiki.model.dto;

import com.pgvector.PGvector;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEmbeddingDto {
    private Long id;
    private String documentId;
    private Integer chunkIndex;
    private PGvector embedding;
    private String textSnippet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private double distance;
}
