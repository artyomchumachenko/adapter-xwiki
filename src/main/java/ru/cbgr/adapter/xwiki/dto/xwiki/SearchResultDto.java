package ru.cbgr.adapter.xwiki.dto.xwiki;


public record SearchResultDto(
        String title,       // Заголовок страницы
        String pageUrl,     // Полный URL вида https://…/xwiki/bin/view/Space/Page
        String snippet,     // Фрагмент текста с <mark>…</mark> (HTML)
        String space,       // Имя пространства (например, "Main" или "HR")
        String updatedAt    // Отформатированная дата последнего обновления, например "2025-08-04"
) {}
