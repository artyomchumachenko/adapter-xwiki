package ru.cbgr.adapter.xwiki.chunker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Чанкер для разбивки таблиц.
 */
@Slf4j
public class TableChunker {

    /**
     * Разбивает таблицу, заданную текстом, на чанки с указанным количеством строк.
     * Заголовок таблицы (первая строка) включается в каждый чанк.
     *
     * @param tableText       Полный текст таблицы.
     * @param maxRowsPerChunk Максимальное число строк (без учёта заголовка) в одном чанке.
     * @return Список чанков, каждый из которых содержит заголовок и группу строк.
     */
    public List<String> chunkTable(String tableText, int maxRowsPerChunk) {
        List<String> chunks = new ArrayList<>();
        // Разбиваем таблицу на строки
        List<String> lines = new ArrayList<>(Arrays.asList(tableText.split("\\r?\\n")));
        if (lines.isEmpty()) {
            return chunks;
        }
        // Первая строка считается заголовком
        String header = lines.getFirst();
        // Остальные строки – данные таблицы
        List<String> dataRows = lines.subList(1, lines.size());
        for (int i = 0; i < dataRows.size(); i += maxRowsPerChunk) {
            int end = Math.min(i + maxRowsPerChunk, dataRows.size());
            StringBuilder sb = new StringBuilder();
            // Добавляем заголовок в каждый чанк
            sb.append(header).append("\n");
            for (int j = i; j < end; j++) {
                sb.append(dataRows.get(j)).append("\n");
            }
            chunks.add(sb.toString().trim());
        }
        return chunks;
    }

    /**
     * Пример выделения таблиц из общего контента.
     * Здесь предполагается, что таблица начинается со строки, начинающейся с "|=".
     *
     * @param content Общий текст.
     * @return Список найденных таблиц.
     */
    public List<String> extractTables(String content) {
        List<String> tables = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        StringBuilder currentTable = new StringBuilder();
        boolean inTable = false;
        for (String line : lines) {
            // Если строка начинается с "|" или "|=", считаем её частью таблицы
            if (line.trim().startsWith("|")) {
                currentTable.append(line).append("\n");
                inTable = true;
            } else {
                // Если вышли из таблицы, сохраняем накопленный блок
                if (inTable && !currentTable.isEmpty()) {
                    tables.add(currentTable.toString().trim());
                    currentTable.setLength(0);
                    inTable = false;
                }
            }
        }
        // Если контент закончился внутри таблицы
        if (inTable && !currentTable.isEmpty()) {
            tables.add(currentTable.toString().trim());
        }
        return tables;
    }
}
