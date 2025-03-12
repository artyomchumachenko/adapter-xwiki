package ru.cbgr.adapter.xwiki.chunker;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextChunker {

    /**
     * Разбивает контент на чанки с учётом заголовков и ограничений по размеру.
     *
     * @param content          Исходный текст.
     * @param maxChunkSize     Максимальное число символов в чанке.
     * @param overlapSentences Количество предложений для перекрытия между чанками.
     * @return Список текстовых чанков.
     */
    public List<String> chunkText(String content, int maxChunkSize, int overlapSentences) {
        // Сначала разбиваем на разделы по заголовкам
        List<String> sections = splitByHeaders(content);
        List<String> chunks = new ArrayList<>();

        for (String section : sections) {
            if (section.length() <= maxChunkSize) {
                chunks.add(section);
            } else {
                // Если раздел слишком длинный, делим его на чанки по предложениям с оптимизированной реализацией
                List<String> sectionChunks = chunkSectionBySentencesOptimized(section, maxChunkSize, overlapSentences);
                chunks.addAll(sectionChunks);
            }
        }
        return chunks;
    }

    /**
     * Разбивает контент на разделы по строкам, начинающимся с маркеров заголовков.
     *
     * @param content Исходный текст.
     * @return Список разделов.
     */
    private List<String> splitByHeaders(String content) {
        List<String> sections = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        StringBuilder currentSection = new StringBuilder();

        for (String line : lines) {
            // Если строка начинается с типичного заголовка ("**" или "==")
            if (line.trim().startsWith("**") || line.trim().startsWith("==")) {
                if (currentSection.length() > 0) {
                    sections.add(currentSection.toString().trim());
                    currentSection.setLength(0);
                }
            }
            currentSection.append(line).append("\n");
        }
        if (currentSection.length() > 0) {
            sections.add(currentSection.toString().trim());
        }
        return sections;
    }

    /**
     * Оптимизированное разбиение длинного раздела на чанки по предложениям с учетом перекрытия.
     * Вместо предварительного создания списка строк-предложений формируем список границ предложений.
     *
     * @param text             Текст раздела.
     * @param maxChunkSize     Максимальное число символов в чанке.
     * @param overlapSentences Количество предложений для перекрытия между чанками.
     * @return Список чанков раздела.
     */
    private List<String> chunkSectionBySentencesOptimized(String text, int maxChunkSize, int overlapSentences) {
        List<String> chunks = new ArrayList<>();

        // Используем BreakIterator для определения границ предложений без создания отдельной строки для каждого предложения
        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("ru"));
        iterator.setText(text);
        int start = iterator.first();
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(start);
        int end = iterator.next();
        while (end != BreakIterator.DONE) {
            boundaries.add(end);
            end = iterator.next();
        }

        int sentenceCount = boundaries.size() - 1;
        int sentenceIndex = 0;
        while (sentenceIndex < sentenceCount) {
            int chunkStart = boundaries.get(sentenceIndex);
            int lastSentence = sentenceIndex;
            // Находим, сколько предложений можно добавить, не превышая maxChunkSize
            while (lastSentence + 1 < sentenceCount && (boundaries.get(lastSentence + 1) - chunkStart) <= maxChunkSize) {
                lastSentence++;
            }
            // Формируем чанк, используя границы предложения
            String chunk = text.substring(chunkStart, boundaries.get(lastSentence)).trim();
            chunks.add(chunk);
            // Сдвигаемся назад на overlapSentences для перекрытия между чанками
            sentenceIndex = Math.max(sentenceIndex + 1, lastSentence - overlapSentences + 1);
        }
        return chunks;
    }
}
