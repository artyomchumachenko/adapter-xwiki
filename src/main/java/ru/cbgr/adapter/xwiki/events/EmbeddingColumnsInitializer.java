package ru.cbgr.adapter.xwiki.events;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.cbgr.adapter.xwiki.configuration.properties.EmbeddingModelConfigRecord;
import ru.cbgr.adapter.xwiki.configuration.properties.ModelsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EmbeddingColumnsInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ModelsProperties modelProperties;

    @Override
    public void run(String... args) {
        for (EmbeddingModelConfigRecord config : modelProperties.embeddingModels()) {
            String model = config.model();
            // Преобразуем имя модели в имя колонки:
            // заменяем все символы, отличные от цифр и латинских букв, на нижнее подчёркивание и добавляем суффикс _embedding
            String columnName = model.replaceAll("[^A-Za-z0-9]", "_") + "_embedding";
            log.info("Проверяем наличие колонки: {}", columnName);

            // Проверка существования колонки в таблице embeddings
            String checkQuery = "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_name = 'embeddings' AND column_name = ?";
            Integer count = jdbcTemplate.queryForObject(checkQuery, new Object[]{columnName}, Integer.class);

            if (count != null && count == 0) {
                // Получаем размер вектора из record, иначе выбрасываем исключение
                Integer vectorSize = config.vectorSize();
                if (vectorSize == null) {
                    throw new IllegalStateException("Не указан размер вектора для embedding-модели: " + model);
                }
                String alterQuery = "ALTER TABLE embeddings ADD COLUMN " + columnName + " vector(" + vectorSize + ")";
                jdbcTemplate.execute(alterQuery);
                log.info("Колонка {} успешно добавлена в таблицу embeddings.", columnName);

                // Создаём индекс для новой колонки.
                // Формируем имя индекса, например: "idx_" + columnName + "_hnsw"
                String indexName = "idx_" + columnName + "_hnsw";
                // SQL-запрос для создания индекса с использованием оператора hnsw и оператора vector_l2_ops
                String createIndexQuery = "CREATE INDEX " + indexName +
                        " ON embeddings USING hnsw (" + columnName + " vector_l2_ops)";
                jdbcTemplate.execute(createIndexQuery);
                log.info("Индекс {} успешно создан для колонки {}.", indexName, columnName);
            } else {
                log.info("Колонка {} уже существует в таблице embeddings.", columnName);
            }
        }
    }
}
