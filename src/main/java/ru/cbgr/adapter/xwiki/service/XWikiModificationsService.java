package ru.cbgr.adapter.xwiki.service;

import org.springframework.stereotype.Service;

import ru.cbgr.adapter.xwiki.client.XWikiClient;
import ru.cbgr.adapter.xwiki.dto.xwiki.ModificationsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class XWikiModificationsService { // todo Добавить мапперы

    private final XWikiClient xWikiClient;

    public ModificationsResponse getModifications() {
        return xWikiClient.getModifications();
    }
}
