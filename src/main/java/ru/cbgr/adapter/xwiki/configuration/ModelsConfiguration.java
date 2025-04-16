package ru.cbgr.adapter.xwiki.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;
import ru.cbgr.adapter.xwiki.events.EmbeddingColumnsInitializer;

@Configuration
@EnableConfigurationProperties(ModelsProperties.class)
public class ModelsConfiguration {

    @Bean
    public EmbeddingColumnsInitializer embeddingColumnsInitializer(JdbcTemplate jdbcTemplate, ModelsProperties modelsProperties) {
        return new EmbeddingColumnsInitializer(jdbcTemplate, modelsProperties);
    }
}
