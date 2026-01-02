package ru.cbgr.adapter.xwiki.utils;

import org.springframework.stereotype.Component;

@Component
public class TitleNormalizer {

    public String normalize(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\u00A0', ' ');              // NBSP
        t = t.replaceAll("\\s+", " ");             // collapse whitespace
        t = t.replace('“', '"').replace('”', '"'); // fancy quotes -> "
        t = t.replace('«', '"').replace('»', '"'); // russian quotes -> "
        return t;
    }
}
