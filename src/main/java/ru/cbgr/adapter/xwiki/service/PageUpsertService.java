package ru.cbgr.adapter.xwiki.service;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;
import ru.cbgr.adapter.xwiki.model.Page;
import ru.cbgr.adapter.xwiki.repository.PageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PageUpsertService {

    private final PageRepository pageRepository;

    public Page upsert(PageSummary summary) {
        return pageRepository.findByXwikiId(summary.getId())
                .map(existing -> updateMeta(existing, summary))
                .orElseGet(() -> createMeta(summary));
    }

    private Page updateMeta(Page page, PageSummary summary) {
        page.setXwikiVersion(summary.getVersion());
        page.setXwikiAbsoluteUrl(summary.getXwikiAbsoluteUrl());
        Page updated = pageRepository.save(page);
        log.debug("Обновлена страница {}.", updated.getId());
        return updated;
    }

    private Page createMeta(PageSummary summary) {
        Page page = new Page();
        page.setTitle(summary.getTitle());
        page.setXwikiId(summary.getId());
        page.setXwikiVersion(summary.getVersion());
        page.setXwikiAbsoluteUrl(summary.getXwikiAbsoluteUrl());

        Page saved = pageRepository.save(page);
        saved.markNew(); // сохраняем ваш приём
        log.debug("Создана новая страница {}.", saved.getId());
        return saved;
    }
}
