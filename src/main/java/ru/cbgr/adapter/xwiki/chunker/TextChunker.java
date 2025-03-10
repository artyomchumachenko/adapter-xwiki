package ru.cbgr.adapter.xwiki.chunker;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Чанкер для обычного текста.
 * Реализует разбиение на разделы по заголовкам и на чанки по предложениям с перекрытием.
 */
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
                // Если раздел слишком длинный, делим его на чанки по предложениям
                List<String> sectionChunks = chunkSectionBySentences(section, maxChunkSize, overlapSentences);
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
                if (!currentSection.isEmpty()) {
                    sections.add(currentSection.toString().trim());
                    currentSection.setLength(0);
                }
            }
            currentSection.append(line).append("\n");
        }
        if (!currentSection.isEmpty()) {
            sections.add(currentSection.toString().trim());
        }
        return sections;
    }

    /**
     * Разбивает длинный раздел на чанки, используя разбиение на предложения.
     *
     * @param text             Текст раздела.
     * @param maxChunkSize     Максимальное число символов в чанке.
     * @param overlapSentences Количество предложений для перекрытия между чанками.
     * @return Список чанков раздела.
     */
    private List<String> chunkSectionBySentences(String text, int maxChunkSize, int overlapSentences) {
        List<String> chunks = new ArrayList<>();
        List<String> sentences = splitIntoSentences(text);

        int sentenceIndex = 0;
        while (sentenceIndex < sentences.size()) {
            StringBuilder chunk = new StringBuilder();
            int startSentence = sentenceIndex;
            // Собираем предложения, пока длина чанка не достигнет максимума
            while (sentenceIndex < sentences.size() &&
                    chunk.length() + sentences.get(sentenceIndex).length() <= maxChunkSize) {
                chunk.append(sentences.get(sentenceIndex)).append(" ");
                sentenceIndex++;
            }
            chunks.add(chunk.toString().trim());
            // Перекрытие: возвращаемся назад на заданное число предложений
            sentenceIndex = Math.max(startSentence, sentenceIndex - overlapSentences);
        }
        return chunks;
    }

    /**
     * Разбивает текст на предложения с учетом правил языка.
     *
     * @param text Исходный текст.
     * @return Список предложений.
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("ru"));
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
}
