package ru.cbgr.adapter.xwiki.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Конфигурация для чекера изменений в XWiki
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "xwiki.modifications")
public class ModificationCheckerProperties {
    private boolean enabled;
}
