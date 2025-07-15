package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Делит текст на чёткие чанки по предложениям/абзацам. */
@Slf4j
public class TextChunker {

    private final int target;
    private final int tolerance;
    private final int overlapSent;

    public TextChunker(int targetSize, int tolerance, int overlapSent) {
        this.target = targetSize;
        this.tolerance = tolerance;
        this.overlapSent = overlapSent;
    }

    public List<String> chunkText(String content) {
        // Сначала режем по верхнеуровневым заголовкам, чтобы не рвать большие блоки
        List<String> sections = splitByHeaders(content);
        List<String> out = new ArrayList<>();

        for (String section : sections) {
            if (fits(section)) {
                out.add(section);
            } else {
                out.addAll(sliceBySentences(section));
            }
        }
        return out;
    }

    /* ——— private ——— */

    /** true, если строка «в диапазоне» [target - tol ; target + tol] */
    private boolean fits(String s) {
        int len = s.length();
        return len >= target - tolerance && len <= target + tolerance;
    }

    private List<String> splitByHeaders(String text) {
        List<String> res = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (String line : text.split("\\R")) {
            if (isHeader(line)) {
                if (!buf.isEmpty()) {
                    res.add(buf.toString().strip());
                    buf.setLength(0);
                }
            }
            buf.append(line).append('\n');
        }
        if (!buf.isEmpty()) res.add(buf.toString().strip());
        return res;
    }

    private boolean isHeader(String line) {
        String t = line.strip();
        return t.startsWith("**") || t.startsWith("==");
    }

    /** Скользящее окно предложений до «идеального» размера. */
    private List<String> sliceBySentences(String text) {
        List<String> res = new ArrayList<>();
        BreakIterator it = BreakIterator.getSentenceInstance(new Locale("ru"));
        it.setText(text);

        List<Integer> bounds = new ArrayList<>();
        for (int p = it.first(); p != BreakIterator.DONE; p = it.next()) bounds.add(p);

        int sentCnt = bounds.size() - 1;
        int idx = 0;
        while (idx < sentCnt) {
            int chunkStart = bounds.get(idx);
            int last = idx;

            // расширяемся, пока не превысим target+tolerance
            while (last + 1 < sentCnt &&
                    bounds.get(last + 1) - chunkStart <= target + tolerance) {
                last++;
            }
            // если всё ещё слишком маленький чанк – добавляем ещё предложение
            if (bounds.get(last) - chunkStart < target - tolerance && last + 1 < sentCnt) last++;

            res.add(text.substring(chunkStart, bounds.get(last)).strip());
            idx = Math.max(idx + 1, last - overlapSent + 1);
        }
        return res;
    }
}
