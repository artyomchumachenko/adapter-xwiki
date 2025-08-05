package ru.cbgr.adapter.xwiki.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/api/xwiki/search/**")         // или "/api/**"
                .allowedOrigins("http://localhost:8080")    // НЕЛЬЗЯ ставить "*", если allowCredentials=true
                .allowedMethods("GET", "POST", "OPTIONS")   // доп. если будут preflight-запросы
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")             // если нужно «достать» с клиента какой-то ответный заголовок
                .allowCredentials(true)                     // <<< вот это важно
                .maxAge(3600);
    }
}
