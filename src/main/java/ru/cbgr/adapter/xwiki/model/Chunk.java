package ru.cbgr.adapter.xwiki.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Entity
@Table(name = "chunks")
@Getter
@Setter
public class Chunk extends AbstractAuditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "text_snippet", columnDefinition = "text", nullable = false)
    private String textSnippet;
}
