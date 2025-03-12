package ru.cbgr.adapter.xwiki.mapper;

import ru.cbgr.adapter.xwiki.model.DocumentEmbedding;
import ru.cbgr.adapter.xwiki.model.dto.DocumentEmbeddingDto;

public class DocumentEmbeddingMapper {

    public static DocumentEmbeddingDto toDtoWithDistance(DocumentEmbedding entity, double distance) {
        return new DocumentEmbeddingDto(
                entity.getId(),
                entity.getDocumentId(),
                entity.getChunkIndex(),
                entity.getEmbedding(),
                entity.getTextSnippet(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                distance
        );
    }
}
