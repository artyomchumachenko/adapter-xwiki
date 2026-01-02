package ru.cbgr.adapter.xwiki.service;

import java.util.List;

import com.pgvector.PGvector;

import org.springframework.ai.embedding.Embedding;
import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.LlamaAiClient;
import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.model.Chunk;
import ru.cbgr.adapter.xwiki.model.Page;
import ru.cbgr.adapter.xwiki.repository.ChunkRepository;
import ru.cbgr.adapter.xwiki.repository.jdbc.EmbeddingJdbcRepository;
import ru.cbgr.adapter.xwiki.utils.EmbeddingColumnNameResolver;
import ru.cbgr.adapter.xwiki.utils.FloatVectorMapper;
import ru.cbgr.adapter.xwiki.utils.VectorNormalizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingIngestionService {

    private final ChunkRepository chunkRepository;
    private final EmbeddingJdbcRepository embeddingJdbcRepository;
    private final ContentNormalizationService contentNormalizationService;
    private final VectorNormalizationService vectorNormalizationService;
    private final ModelsProperties modelsProperties;
    private final LlamaAiClient llamaAiClient;
    private final EmbeddingColumnNameResolver columnNameResolver;

    public void cleanupOldData(long pageId) {
        embeddingJdbcRepository.deleteEmbeddingsByPageId(pageId);

        List<Chunk> toDelete = chunkRepository.findByPageId(pageId);
        if (!toDelete.isEmpty()) {
            chunkRepository.deleteAll(toDelete);
        }
        log.debug("Очищены старые чанки/эмбеддинги для страницы {}.", pageId);
    }

    public void persistChunksAndEmbeddings(Page page, List<String> rawChunks) {
        for (int idx = 0; idx < rawChunks.size(); idx++) {
            String normalizedText = contentNormalizationService.normalize(rawChunks.get(idx));
            if (normalizedText.isEmpty()) {
                continue;
            }

            Chunk savedChunk = chunkRepository.save(new Chunk(page, idx, normalizedText));
            long embeddingId = embeddingJdbcRepository.insertEmptyEmbedding(savedChunk.getId());

            for (EmbeddingModelConfigRecord modelCfg : modelsProperties.embeddingModels()) {
                saveEmbeddingForModel(modelCfg, normalizedText, embeddingId);
            }
        }
    }

    private void saveEmbeddingForModel(EmbeddingModelConfigRecord modelCfg, String text, long embeddingId) {
        String modelName = modelCfg.model();
        String column = columnNameResolver.toEmbeddingColumn(modelName);

        try {
            Embedding embedding = llamaAiClient.getEmbeddings(text, modelName)
                    .getResults()
                    .stream()
                    .findFirst()
                    .orElseThrow();

            float[] vector = FloatVectorMapper.toFloatArray(embedding.getOutput());
            float[] normalized = vectorNormalizationService.normalizeVector(modelCfg.index(), vector);

            embeddingJdbcRepository.updateEmbeddingVector(embeddingId, column, new PGvector(normalized));
            log.debug("Эмбеддинг модели {} сохранён в колонку {} (embedding_id={}).", modelName, column, embeddingId);

        } catch (Exception e) {
            log.error("Ошибка генерации эмбеддинга для модели {} (embedding_id={}).", modelName, embeddingId, e);
        }
    }
}
