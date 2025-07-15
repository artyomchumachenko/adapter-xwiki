package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Делит wiki‑таблицу на порции с сохранением заголовка. */
@Slf4j
public class TableChunker {

    private final int maxRows;

    public TableChunker(int maxRowsPerChunk) {
        this.maxRows = Math.max(1, maxRowsPerChunk);
    }

    public List<String> chunkTable(String wikiTable) {
        List<String> out = new ArrayList<>();
        List<String> rows = new ArrayList<>(Arrays.asList(wikiTable.split("\\R")));
        if (rows.isEmpty()) return out;

        String header = convertRow(rows.getFirst());      // первая строка — заголовок
        List<String> data = rows.subList(1, rows.size());

        for (int i = 0; i < data.size(); i += maxRows) {
            int end = Math.min(i + maxRows, data.size());
            StringBuilder blk = new StringBuilder(header).append('\n');
            for (int j = i; j < end; j++)
                blk.append(convertRow(data.get(j))).append('\n');
            out.add(blk.toString().strip());
        }
        return out;
    }

    /* ——— Markdown‑friendly преобразование строки таблицы ——— */
    private String convertRow(String raw) {
        String line = raw.strip();
        if (!line.startsWith("|")) return line;

        // убираем первый | и делим по |
        String[] cells = line.substring(1).split("\\|");
        StringBuilder sb = new StringBuilder("|");
        for (String c : cells) sb.append(' ').append(cleanCell(c)).append(" |");
        return sb.toString();
    }

    private String cleanCell(String cell) {
        // заменяем XWiki‑ссылки [[text>>url]] → text (url)
        return cell.replaceAll("\\[\\[(.*?)>>(.*?)]]", "$1 ($2)")
                // картинки {{image reference}} → image:reference
                .replaceAll("\\{\\{(.*?)}}", "image:$1")
                .strip();
    }
}
