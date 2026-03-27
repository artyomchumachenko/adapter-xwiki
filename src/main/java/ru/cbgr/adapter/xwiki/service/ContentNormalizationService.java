package ru.cbgr.adapter.xwiki.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentNormalizationService {

    private static final Pattern XWIKI_MACRO_PATTERN =
            Pattern.compile("\\{\\{/?(info|html)\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLL_PERCENT_LINE =
            Pattern.compile("^\\d{1,3}(?:[.,]\\d+)?%.*");

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
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = stripHtml(raw);
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        text = text.replace('\u00A0', ' ');
        text = XWIKI_MACRO_PATTERN.matcher(text).replaceAll("");

        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder(text.length());
        boolean lastWasEmpty = false;

        for (String line : lines) {
            String normalizedLine = line.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
            normalizedLine = processWikiLinks(normalizedLine);
            if (normalizedLine.isEmpty()) {
                if (!lastWasEmpty) {
                    out.append('\n');
                    lastWasEmpty = true;
                }
                continue;
            }

            if (shouldSkipLine(normalizedLine)) {
                continue;
            }

            out.append(normalizedLine).append('\n');
            lastWasEmpty = false;
        }

        return out.toString().trim();
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

    private boolean shouldSkipLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("\u0442\u0435\u0433\u0438:")
                || normalized.startsWith("\u0445\u0430\u0431\u044b:")
                || normalized.startsWith("\u043d\u0440\u0430\u0432\u0438\u0442\u0441\u044f")
                || normalized.startsWith("\u043d\u0435 \u043d\u0440\u0430\u0432\u0438\u0442\u0441\u044f")
                || normalized.startsWith("\u0434\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0432 \u0437\u0430\u043a\u043b\u0430\u0434\u043a\u0438")
                || normalized.startsWith("\u043a\u043e\u043c\u043c\u0435\u043d\u0442\u0430\u0440\u0438\u0438")) {
            return true;
        }

        if (normalized.startsWith("\u0443\u0440\u043e\u0432\u0435\u043d\u044c \u0441\u043b\u043e\u0436\u043d\u043e\u0441\u0442\u0438")
                || normalized.startsWith("\u0432\u0440\u0435\u043c\u044f \u043d\u0430 \u043f\u0440\u043e\u0447\u0442\u0435\u043d\u0438\u0435")
                || normalized.startsWith("\u043e\u0445\u0432\u0430\u0442 \u0438 \u0447\u0438\u0442\u0430\u0442\u0435\u043b\u0438")
                || normalized.startsWith("\u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a (habr):")
                || normalized.startsWith("flow:")
                || normalized.equals("\u043c\u043d\u0435\u043d\u0438\u0435")) {
            return true;
        }

        if (normalized.startsWith("\u0442\u043e\u043b\u044c\u043a\u043e \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0435 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0438 \u043c\u043e\u0433\u0443\u0442 \u0443\u0447\u0430\u0441\u0442\u0432\u043e\u0432\u0430\u0442\u044c \u0432 \u043e\u043f\u0440\u043e\u0441\u0435")) {
            return true;
        }

        if (normalized.startsWith("\u043f\u0440\u043e\u0433\u043e\u043b\u043e\u0441\u043e\u0432\u0430\u043b\u0438 ")
                || normalized.startsWith("\u0432\u043e\u0437\u0434\u0435\u0440\u0436\u0430\u043b\u0438\u0441\u044c ")) {
            return true;
        }

        return POLL_PERCENT_LINE.matcher(normalized).matches();
    }
}
