package ru.cbgr.adapter.xwiki.utils;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingColumnNameResolver {

    public String toEmbeddingColumn(String modelName) {
        String base = modelName == null ? "" : modelName.replaceAll("[^A-Za-z0-9]", "_");
        String column = base + "_embedding";

        // финальная валидация: только [A-Za-z0-9_], иначе падаем (защита от SQL-инъекций через конфиг)
        if (!column.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid embedding column derived from model: " + modelName);
        }
        return column;
    }
}
