package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Комбинированный чанкер: текст / таблицы.
 * Поддерживает «ровные» чанки ~TARGET_SIZE символов.
 */
@Component
@Slf4j
public class CombinedContentChunker {

    /* ——— НАСТРОЙКИ ——— */
    private static final int TARGET_SIZE          = 800; // оптимальный размер чанка
    private static final int SIZE_TOLERANCE       = (int) (TARGET_SIZE * 0.15); // ±15 %
    private static final int OVERLAP_SENT         = 2;   // перекрытие предложений
    private static final int TABLE_MAX_ROWS       = 20;  // строк данных на чанк
    private static final int MIN_TEXT_CHUNK_SIZE  = TARGET_SIZE / 2;

    private final TableChunker tableChunker = new TableChunker(TABLE_MAX_ROWS);
    private final TextChunker  textChunker  = new TextChunker(TARGET_SIZE, SIZE_TOLERANCE, OVERLAP_SENT);

    /** Разбивает XWiki‑страницу на чанки, учитывая таблицы. */
    public List<String> chunkContent(String content) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\\R");          // любая новая строка
        StringBuilder segment = new StringBuilder();
        Boolean inTable = null;

        for (String raw : lines) {
            boolean isTableLine = raw.trim().startsWith("|");
            if (inTable == null) inTable = isTableLine;

            if (isTableLine != inTable) {               // сменился режим
                processSegment(segment.toString(), inTable, chunks);
                segment.setLength(0);
                inTable = isTableLine;
            }
            segment.append(raw).append('\n');
        }
        if (!segment.isEmpty()) processSegment(segment.toString(), inTable, chunks);

        return chunks;
    }

    /* ================================================================= */

    private void processSegment(String segment, boolean isTable, List<String> out) {
        segment = segment.strip();
        if (segment.isEmpty()) return;

        if (isTable) {
            out.addAll(tableChunker.chunkTable(segment));
        } else {
            List<String> textChunks = textChunker.chunkText(segment);
            out.addAll(mergeSmallTextChunks(textChunks));
        }
    }

    /** Склеиваем мелочь, чтобы не было очень коротких чанков (< MIN_TEXT_CHUNK_SIZE). */
    private List<String> mergeSmallTextChunks(List<String> src) {
        if (src.size() <= 1) return src;

        List<String> merged = new ArrayList<>();
        StringBuilder acc = new StringBuilder();

        for (String part : src) {
            if (acc.isEmpty()) {
                acc.append(part);
            } else if (acc.length() + 1 + part.length() < MIN_TEXT_CHUNK_SIZE) {
                acc.append(' ').append(part);
            } else {
                merged.add(acc.toString());
                acc.setLength(0);
                acc.append(part);
            }
        }
        if (!acc.isEmpty()) merged.add(acc.toString());
        return merged;
    }
}
