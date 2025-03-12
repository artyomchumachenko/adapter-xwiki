package ru.cbgr.adapter.xwiki.model;

import java.time.LocalDateTime;
import com.pgvector.PGvector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEmbedding {
    private Long id;
    private String documentId;
    private Integer chunkIndex;
    private PGvector embedding;
    private String textSnippet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
