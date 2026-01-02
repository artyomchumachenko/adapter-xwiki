package ru.cbgr.adapter.xwiki.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class CsvTitlesHolder {

    private static final Path CSV_PATH =
            Path.of("C:\\Users\\achumachenko\\PycharmProjects\\XWiki Tools\\testing\\eval_dataset.csv");

    private static final Set<String> NORMALIZED_TITLES = loadNormalizedTitles();

    private CsvTitlesHolder() {
    }

    public static Set<String> getNormalizedTitles() {
        return NORMALIZED_TITLES;
    }

    private static Set<String> loadNormalizedTitles() {
        if (!Files.exists(CSV_PATH)) {
            // Лучше сразу падать — иначе вы “случайно” пропустите все страницы
            throw new IllegalStateException("CSV file not found: " + CSV_PATH);
        }

        try (BufferedReader br = Files.newBufferedReader(CSV_PATH, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                throw new IllegalStateException("CSV is empty: " + CSV_PATH);
            }

            int titleIdx = findColumnIndex(header, "page_title");
            if (titleIdx < 0) {
                throw new IllegalStateException("CSV column 'page_title' not found in header: " + header);
            }

            Set<String> set = new HashSet<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = splitCsvLine(line);
                if (titleIdx >= cols.length) continue;

                String title = unquote(cols[titleIdx]);
                String norm = normalizeTitleStatic(title);
                if (!norm.isBlank()) set.add(norm);
            }

            return Collections.unmodifiableSet(set);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV: " + CSV_PATH, e);
        }
    }

    private static int findColumnIndex(String headerLine, String colName) {
        String[] cols = splitCsvLine(headerLine);
        for (int i = 0; i < cols.length; i++) {
            String c = unquote(cols[i]).trim();
            if (colName.equalsIgnoreCase(c)) return i;
        }
        return -1;
    }

    /**
     * Минимальный CSV-splitter для типового файла, где поля могут быть в кавычках.
     * Поддерживает:
     * - разделитель запятая
     * - кавычки двойные
     * - запятые внутри кавычек
     * - экранирование "" внутри кавычек
     */
    private static String[] splitCsvLine(String line) {
        // Быстрый ручной парсер без зависимостей
        var out = new java.util.ArrayList<String>();
        var sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // escaped quote
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static String unquote(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t.trim();
    }

    private static String normalizeTitleStatic(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\u00A0', ' ');
        t = t.replaceAll("\\s+", " ");
        t = t.replace('“', '"').replace('”', '"');
        t = t.replace('«', '"').replace('»', '"');
        return t;
    }
}
