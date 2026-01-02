package ru.cbgr.adapter.xwiki.service;

import java.util.Objects;
import java.util.Set;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.model.Page;
import ru.cbgr.adapter.xwiki.utils.TitleNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PageProcessor {

    private static final String SELECT_VERSION_SQL =
            "SELECT xwiki_version FROM pages WHERE xwiki_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TitleNormalizer titleNormalizer;
    private final PageUpsertService pageUpsertService;
    private final PageContentService pageContentService;
    private final EmbeddingIngestionService ingestionService;

    @Transactional
    public void processPage(PageSummary summary) {
        if (isSkipProcess(summary, null)) {
            return;
        }

        Page page = pageUpsertService.upsert(summary);

        var chunks = pageContentService.loadAndChunkContent(summary);
        if (chunks.isEmpty()) {
            log.warn("Контент страницы {} пуст – пропуск.", summary.getId());
            return;
        }

        if (!page.isNew()) {
            ingestionService.cleanupOldData(page.getId());
        }

        ingestionService.persistChunksAndEmbeddings(page, chunks);
    }

    /**
     * titles — ожидается уже нормализованный Set (или null).
     * Если передаёте сырые строки, предварительно нормализуйте через TitleNormalizer.
     */
    public boolean isSkipProcess(PageSummary page, Set<String> normalizedTitles) {
        // 1) фильтр по заголовку из CSV
        if (normalizedTitles != null && !normalizedTitles.isEmpty()) {
            String pageTitleNorm = titleNormalizer.normalize(page.getTitle());
            if (!normalizedTitles.contains(pageTitleNorm)) {
                log.debug("Страница {} пропущена: title '{}' отсутствует в CSV-списке.",
                        page.getId(), page.getTitle());
                return true;
            }
        }

        // 2) фильтр по версии
        String currentVersion = null;
        try {
            currentVersion = jdbcTemplate.queryForObject(SELECT_VERSION_SQL, String.class, page.getId());
        } catch (EmptyResultDataAccessException ignored) {
            // страницы ещё нет в БД -> не пропускаем
        }

        boolean skip = Objects.equals(currentVersion, page.getVersion());
        if (skip) {
            log.debug("Версия страницы {} уже актуальна ({}). Пропуск.", page.getId(), currentVersion);
        }
        return skip;
    }
}
