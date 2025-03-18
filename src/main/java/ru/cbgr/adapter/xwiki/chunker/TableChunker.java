package ru.cbgr.adapter.xwiki.chunker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

}
