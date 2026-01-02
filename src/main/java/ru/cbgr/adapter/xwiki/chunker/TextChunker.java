package ru.cbgr.adapter.xwiki.chunker;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Делит обычный текст на чанки.
 * Гарантирует, что ни один чанк не превышает hardMaxChars.
 */
@Slf4j
public class TextChunker {

    private static final Pattern SENTENCE_SPLIT =
            Pattern.compile("(?<=[.!?…])\\s+"); // простое деление по окончаниям предложений

    private static final Pattern PARAGRAPH_SPLIT =
            Pattern.compile("\\R{2,}"); // пустая строка/несколько переносов

    private final int targetChars;
    private final int hardMaxChars;
    private final int hardOverlapChars;

    public TextChunker(int targetChars, int hardMaxChars, int hardOverlapChars) {
        this.targetChars = Math.max(100, targetChars);
        this.hardMaxChars = Math.max(this.targetChars, hardMaxChars);
        this.hardOverlapChars = Math.max(0, hardOverlapChars);
    }

    public List<String> chunkText(String text) {
        if (text == null || text.isBlank()) return List.of();

        // 1) Абзацы как “крупные блоки”
        String[] paragraphs = PARAGRAPH_SPLIT.split(text.strip());

        List<String> rawChunks = new ArrayList<>();
        StringBuilder acc = new StringBuilder();

        for (String p : paragraphs) {
            String paragraph = p.strip();
            if (paragraph.isEmpty()) continue;

            // если абзац слишком большой — режем его по предложениям/словам
            if (paragraph.length() > hardMaxChars) {
                flushAcc(acc, rawChunks);
                rawChunks.addAll(chunkLargeParagraph(paragraph));
                continue;
            }

            // накапливаем чанки примерно targetChars
            if (acc.isEmpty()) {
                acc.append(paragraph);
            } else if (acc.length() + 2 + paragraph.length() <= targetChars) {
                acc.append("\n\n").append(paragraph);
            } else {
                rawChunks.add(acc.toString().strip());
                acc.setLength(0);
                acc.append(paragraph);
            }
        }

        flushAcc(acc, rawChunks);

        // 2) Финальная гарантия hard-limit
        return HardLimiter.enforce(rawChunks, hardMaxChars, hardOverlapChars);
    }

    private void flushAcc(StringBuilder acc, List<String> out) {
        if (acc != null && !acc.isEmpty()) {
            String s = acc.toString().strip();
            if (!s.isEmpty()) out.add(s);
        }
    }

    private List<String> chunkLargeParagraph(String paragraph) {
        // 2.1) Пытаемся разрезать по предложениям
        String[] sentences = SENTENCE_SPLIT.split(paragraph);
        if (sentences.length > 1) {
            return packToTarget(sentences);
        }

        // 2.2) Если предложений “не видно” (длинная строка), режем по словам
        return splitByWordsThenChars(paragraph);
    }

    private List<String> packToTarget(String[] parts) {
        List<String> out = new ArrayList<>();
        StringBuilder acc = new StringBuilder();

        for (String part : parts) {
            String s = part.strip();
            if (s.isEmpty()) continue;

            if (s.length() > hardMaxChars) {
                // если даже предложение слишком длинное — fallback
                flushAcc(acc, out);
                out.addAll(splitByWordsThenChars(s));
                continue;
            }

            if (acc.isEmpty()) {
                acc.append(s);
            } else if (acc.length() + 1 + s.length() <= targetChars) {
                acc.append(' ').append(s);
            } else {
                out.add(acc.toString().strip());
                acc.setLength(0);
                acc.append(s);
            }
        }

        flushAcc(acc, out);
        return out;
    }

    private List<String> splitByWordsThenChars(String text) {
        String t = text.strip();
        if (t.length() <= hardMaxChars) return List.of(t);

        String[] words = t.split("\\s+");
        if (words.length <= 1) {
            // один “монолитный токен” — режем по символам
            return HardLimiter.splitByCharsWithOverlap(t, hardMaxChars, hardOverlapChars);
        }

        List<String> out = new ArrayList<>();
        StringBuilder acc = new StringBuilder();

        for (String w : words) {
            if (w.isEmpty()) continue;

            if (w.length() > hardMaxChars) {
                // слово само по себе больше лимита — режем по символам
                flushAcc(acc, out);
                out.addAll(HardLimiter.splitByCharsWithOverlap(w, hardMaxChars, hardOverlapChars));
                continue;
            }

            if (acc.isEmpty()) {
                acc.append(w);
            } else if (acc.length() + 1 + w.length() <= hardMaxChars) {
                acc.append(' ').append(w);
            } else {
                out.add(acc.toString().strip());
                acc.setLength(0);
                acc.append(w);
            }
        }

        flushAcc(acc, out);
        return out;
    }
}
