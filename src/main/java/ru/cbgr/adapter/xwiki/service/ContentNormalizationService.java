package ru.cbgr.adapter.xwiki.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentNormalizationService {

    /**
     * Нормализует переданный чанк.
     *  - Обрезает лишние пробелы в начале и конце каждой строки.
     *  - Заменяет последовательности пробелов на одинарный пробел.
     *  - Преобразует wiki-ссылки в более читаемый формат.
     *
     * @param chunk исходный текст чанка
     * @return нормализованный текст чанка
     */
    public String normalize(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return "";
        }

        // 1) Очистка от HTML-разметки
        String plain = stripHtml(chunk);

        // 2) Разбиваем на строки и применяем текущую нормализацию
        String[] lines = plain.split("\\r?\\n");
        StringBuilder normalizedBuilder = new StringBuilder(plain.length());

        for (String line : lines) {
            String normalizedLine = line.trim().replaceAll("\\s+", " ");
            normalizedLine = processWikiLinks(normalizedLine);

            if (!normalizedLine.isBlank()) {
                normalizedBuilder.append(normalizedLine).append('\n');
            }
        }

        return normalizedBuilder.toString().trim();
    }

    public String normalizeForChunking(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // 1) HTML -> text (как обсуждали ранее)
        String text = stripHtml(raw);

        // 2) Легкая унификация пробелов/переносов (без агрессивных replaceAll по строкам)
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        text = text.replace('\u00A0', ' '); // NBSP -> space

        return text.trim();
    }

    /**
     * Превращает HTML в plain text.
     * - удаляет теги
     * - декодирует entities (&nbsp; &amp; и т.п.)
     * - старается сохранить переносы строк для <br>, <p>, <div>
     */
    private String stripHtml(String input) {
        // Если вы хотите чистить ТОЛЬКО когда есть HTML — можно добавить быстрый эвристический if.
         if (!looksLikeHtml(input)) {
             return input;
         }

        Document doc = Jsoup.parse(input);

        // Чтобы <br> и </p> не склеивали слова:
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        doc.select("br").append("\\n");
        doc.select("p").append("\\n");
        doc.select("div").append("\\n");

        String text = doc.text();

        // Возвращаем реальные переносы строк
        text = text.replace("\\n", "\n");

        // NBSP (часто прилетает из HTML) -> обычный пробел
        return text.replace('\u00A0', ' ');
    }

    // Опционально: эвристика "похоже на HTML"
    @SuppressWarnings("unused")
    private boolean looksLikeHtml(String s) {
        // минимальная эвристика: есть теги вида <...>
        return s.indexOf('<') >= 0 && s.indexOf('>') >= 0;
    }

    /**
     * Обрабатывает wiki-ссылки, заменяя сложную разметку на читаемый формат.
     * Пример преобразования:
     * [[~[SCC3-204~] Создание макетов для работы с Группами пользователей - CBGR JIRA>>url:https://jira.dev.cbgr.ru/jira/browse/SCC3-204]]
     * преобразуется в:
     * "Создание макетов для работы с Группами пользователей - CBGR JIRA (https://jira.dev.cbgr.ru/jira/browse/SCC3-204)"
     *
     * @param line строка, возможно содержащая wiki-ссылку
     * @return строка с нормализованной ссылкой
     */
    private String processWikiLinks(String line) {
        if (line.contains(">>url:")) {
            // Удаляем служебную часть, например, "[[~[SCC3-204~]"
            line = line.replaceAll("\\[\\~\\[.*?~\\]", "");
            // Удаляем начальные и конечные двойные квадратные скобки
            line = line.replaceAll("\\[\\[", "");
            line = line.replaceAll("]]", "");
            // Заменяем маркер ссылки на открывающую скобку
            line = line.replace(">>url:", " (");
            // Если отсутствует закрывающая скобка, добавляем её
            if (!line.endsWith(")")) {
                line = line + ")";
            }
        }
        return line;
    }
}
