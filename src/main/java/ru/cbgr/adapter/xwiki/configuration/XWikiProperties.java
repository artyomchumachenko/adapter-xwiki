package ru.cbgr.adapter.xwiki.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Конфигурация для доступа к XWiki REST API
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "xwiki")
public class XWikiProperties {
    private String baseUrl;
    private String username;
    private String password;
}
