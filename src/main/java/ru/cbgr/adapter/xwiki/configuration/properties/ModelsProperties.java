package ru.cbgr.adapter.xwiki.configuration.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "models")
public record ModelsProperties(
        List<EmbeddingModelConfigRecord> embeddingModels,
        String llmModel
) {}
