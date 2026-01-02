package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Комбинированный чанкер: отделяет wiki-таблицы от обычного текста и
 * возвращает чанки безопасного размера для embedding-моделей.
 *
 * Важно: hard-limit применяется ВСЕГДА, поэтому “input length exceeds context length”
 * устраняется на уровне чанкинга.
 */
@Component
@Slf4j
public class CombinedContentChunker {

    /** Целевой размер текстового чанка (для “красивых” чанков). */
    private static final int TARGET_TEXT_CHARS = 800;

    /** Жесткий максимум символов для любого чанка (под ctx=512). */
    private static final int HARD_MAX_CHARS = 1200;

    /** Перекрытие при принудительном разрезании по символам. */
    private static final int HARD_OVERLAP_CHARS = 100;

    /** Максимум строк данных таблицы (помимо заголовка) в одном чанке. */
    private static final int TABLE_MAX_ROWS = 20;

    /** Жесткий максимум символов для табличного чанка. */
    private static final int TABLE_MAX_CHARS = HARD_MAX_CHARS;

    private final TableChunker tableChunker = new TableChunker(TABLE_MAX_ROWS, TABLE_MAX_CHARS);
    private final TextChunker textChunker = new TextChunker(TARGET_TEXT_CHARS, HARD_MAX_CHARS, HARD_OVERLAP_CHARS);

    /**
     * Разбивает контент на сегменты “таблица/текст” по строкам, затем делит их соответствующими чанкерами.
     */
    public List<String> chunkContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();

        String[] lines = content.split("\\R");
        StringBuilder segment = new StringBuilder();
        Boolean inTable = null;

        for (String raw : lines) {
            boolean isTableLine = isWikiTableLine(raw);

            if (inTable == null) {
                inTable = isTableLine;
            }

            if (isTableLine != inTable) {
                flushSegment(segment, inTable, out);
                segment.setLength(0);
                inTable = isTableLine;
            }

            segment.append(raw).append('\n');
        }

        flushSegment(segment, Boolean.TRUE.equals(inTable), out);

        // Финальная страховка: hard-limit на всякий случай (если кто-то изменит внутренние чанкер-правила)
        return HardLimiter.enforce(out, HARD_MAX_CHARS, HARD_OVERLAP_CHARS);
    }

    private void flushSegment(StringBuilder segment, boolean isTable, List<String> out) {
        if (segment == null || segment.isEmpty()) {
            return;
        }

        String text = segment.toString().strip();
        if (text.isEmpty()) {
            return;
        }

        List<String> chunks = isTable ? tableChunker.chunkTable(text) : textChunker.chunkText(text);
        out.addAll(chunks);
    }

    private boolean isWikiTableLine(String rawLine) {
        if (rawLine == null) return false;
        String t = rawLine.stripLeading();
        return t.startsWith("|");
    }
}
