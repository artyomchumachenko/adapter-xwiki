package ru.cbgr.adapter.xwiki.dto.xwiki.modifications;

import java.util.List;

import lombok.Data;

@Data
public class HistorySummary {
    private List<Link> links;
    private String pageId;
    private String wiki;
    private String space;
    private String name;
    private String version;
    private int majorVersion;
    private int minorVersion;
    private long modified; // время в виде Unix timestamp (миллисекунды)
    private String modifier;
    private String modifierName;
    private String language;
    private String comment;
}
