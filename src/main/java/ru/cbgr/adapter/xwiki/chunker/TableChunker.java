package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Делит wiki-таблицу на порции с сохранением заголовка.
 * Ограничивает чанки и по числу строк, и по размеру (chars).
 */
@Slf4j
public class TableChunker {

    private final int maxRows;
    private final int maxChars;

    public TableChunker(int maxRowsPerChunk, int maxCharsPerChunk) {
        this.maxRows = Math.max(1, maxRowsPerChunk);
        this.maxChars = Math.max(200, maxCharsPerChunk);
    }

    public List<String> chunkTable(String wikiTable) {
        if (wikiTable == null || wikiTable.isBlank()) return List.of();

        List<String> rows = new ArrayList<>(Arrays.asList(wikiTable.split("\\R")));
        rows.removeIf(r -> r == null || r.isBlank());

        if (rows.isEmpty()) return List.of();

        String header = convertRow(rows.getFirst());
        List<String> data = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();

        // Если “таблица” на самом деле из одной строки — вернём как есть (но с hard-limit на уровне Combined)
        if (data.isEmpty()) {
            return List.of(header.strip());
        }

        List<String> out = new ArrayList<>();
        StringBuilder blk = new StringBuilder();
        int rowsInBlock = 0;

        // стартуем первый блок с header
        blk.append(header).append('\n');

        for (String rawRow : data) {
            String row = convertRow(rawRow);

            // если строка сама по себе слишком длинная — режем её на части
            List<String> rowParts = row.length() <= maxChars
                    ? List.of(row)
                    : HardLimiter.splitByCharsWithOverlap(row, maxChars, 0);

            for (String rowPart : rowParts) {
                boolean wouldExceedChars = blk.length() + rowPart.length() + 1 > maxChars;
                boolean wouldExceedRows = rowsInBlock >= maxRows;

                if ((wouldExceedChars || wouldExceedRows) && rowsInBlock > 0) {
                    out.add(blk.toString().strip());
                    blk.setLength(0);
                    blk.append(header).append('\n');
                    rowsInBlock = 0;
                }

                // если даже “пустой блок + header + rowPart” > maxChars, тогда будем резать жестче
                if (blk.length() + rowPart.length() + 1 > maxChars) {
                    // закрываем header как отдельный блок, если нет места
                    if (rowsInBlock == 0) {
                        // режем rowPart уже по maxChars - header
                        int budget = Math.max(50, maxChars - header.length() - 2);
                        List<String> tinyParts = HardLimiter.splitByCharsWithOverlap(rowPart, budget, 0);
                        for (String tp : tinyParts) {
                            out.add((header + "\n" + tp).strip());
                        }
                        continue;
                    }
                }

                blk.append(rowPart).append('\n');
                rowsInBlock++;
            }
        }

        if (!blk.isEmpty()) {
            out.add(blk.toString().strip());
        }

        return out;
    }

    private String convertRow(String raw) {
        String line = raw == null ? "" : raw.strip();
        if (!line.startsWith("|")) {
            return line;
        }

        String[] cells = line.substring(1).split("\\|");
        StringBuilder sb = new StringBuilder("|");
        for (String c : cells) {
            sb.append(' ').append(cleanCell(c)).append(" |");
        }
        return sb.toString();
    }

    private String cleanCell(String cell) {
        String c = cell == null ? "" : cell;

        // XWiki-ссылки: [[text>>url:...]] или [[text>>...]] -> text (url)
        c = c.replaceAll("\\[\\[(.*?)>>\\s*(url:)?(.*?)]]", "$1 ($3)");

        // картинки/макросы {{...}} -> [macro:...]
        c = c.replaceAll("\\{\\{(.*?)}}", "[macro:$1]");

        // схлопнем пробелы
        return c.strip().replaceAll("\\s+", " ");
    }
}
