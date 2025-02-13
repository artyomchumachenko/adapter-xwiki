package ru.cbgr.adapter.xwiki.dto.xwiki.modifications;

import lombok.Data;

@Data
public class Link {
    private String href;
    private String rel;
    private String type;
    private String hrefLang;
}
