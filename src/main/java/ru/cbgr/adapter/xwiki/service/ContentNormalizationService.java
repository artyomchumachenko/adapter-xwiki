package ru.cbgr.adapter.xwiki.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.Jsoup;
import java.text.Normalizer;

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
        // Разбиваем чанк на строки
        String[] lines = chunk.split("\\r?\\n");
        StringBuilder normalizedBuilder = new StringBuilder();

        for (String line : lines) {
            // Обрезаем пробелы по краям и заменяем несколько пробелов на один
            String normalizedLine = line.trim().replaceAll("\\s+", " ");
            // Применяем нормализацию wiki-ссылок (если присутствуют)
            normalizedLine = processWikiLinks(normalizedLine);
            normalizedBuilder.append(normalizedLine).append("\n");
        }
        return normalizedBuilder.toString().trim();
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
