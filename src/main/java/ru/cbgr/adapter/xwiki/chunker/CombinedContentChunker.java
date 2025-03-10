package ru.cbgr.adapter.xwiki.chunker;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Комбинированный чанкер, который анализирует контент построчно.
 * Если найден блок таблицы (строки, начинающиеся с '|'), применяется TableChunker,
 * иначе – TextChunker.
 */
@Component
@Slf4j
public class CombinedContentChunker {

    private final TableChunker tableChunker = new TableChunker();
    private final TextChunker textChunker = new TextChunker();

    /**
     * Разбивает исходный контент на чанки с учетом наличия таблиц.
     *
     * @param content          Исходный текст (включает таблицы и обычный текст).
     * @param maxChunkSize     Максимальное число символов для текстовых чанков.
     * @param overlapSentences Количество предложений для перекрытия между текстовыми чанками.
     * @param tableMaxRows     Максимальное число строк данных в одном чанке таблицы (без заголовка).
     * @return Список чанков.
     */
    public List<String> chunkContent(String content, int maxChunkSize, int overlapSentences, int tableMaxRows) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        StringBuilder segment = new StringBuilder();
        Boolean currentIsTable = null; // null означает, что еще не определен тип

        for (String line : lines) {
            // Определяем, является ли строка частью таблицы
            boolean isTableLine = line.trim().startsWith("|");
            if (currentIsTable == null) {
                currentIsTable = isTableLine;
            }
            // Если тип строки отличается от текущего сегмента, завершаем сегмент и обрабатываем его
            if (isTableLine != currentIsTable) {
                processSegment(segment.toString(), currentIsTable, chunks, maxChunkSize, overlapSentences, tableMaxRows);
                segment.setLength(0);
                currentIsTable = isTableLine;
            }
            segment.append(line).append("\n");
        }
        // Обработка последнего сегмента
        if (!segment.isEmpty() && currentIsTable != null) {
            processSegment(segment.toString(), currentIsTable, chunks, maxChunkSize, overlapSentences, tableMaxRows);
        }
        return chunks;
    }

    /**
     * Обрабатывает один сегмент текста в зависимости от его типа.
     *
     * @param segment          Текст сегмента.
     * @param isTable          Если true, сегмент рассматривается как таблица.
     * @param chunks           Список чанков, куда добавляются результаты.
     * @param maxChunkSize     Максимальный размер текстового чанка.
     * @param overlapSentences Число предложений для перекрытия в текстовом чанке.
     * @param tableMaxRows     Максимальное число строк данных в чанке таблицы.
     */
    private void processSegment(String segment, boolean isTable, List<String> chunks,
            int maxChunkSize, int overlapSentences, int tableMaxRows) {
        String trimmed = segment.trim();
        if (trimmed.isEmpty()) return;
        if (isTable) {
            // Обрабатываем как таблицу
            List<String> tableChunks = tableChunker.chunkTable(trimmed, tableMaxRows);
            chunks.addAll(tableChunks);
        } else {
            // Обрабатываем как обычный текст
            List<String> textChunks = textChunker.chunkText(trimmed, maxChunkSize, overlapSentences);
            chunks.addAll(textChunks);
        }
    }
}
