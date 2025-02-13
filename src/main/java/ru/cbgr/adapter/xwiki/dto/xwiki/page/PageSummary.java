package ru.cbgr.adapter.xwiki.dto.xwiki.page;

import lombok.Data;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageSummary {
    private List<Link> links;
    private String id;
    private String fullName;
    private String wiki;
    private String space;
    private String name;
    private String title;
    private String rawTitle;
    private String parent;
    private String parentId;
    private String version;
    private String author;
    private String authorName;
    private String xwikiRelativeUrl;
    private String xwikiAbsoluteUrl;
    // Можно добавить поле для переводов, синтаксиса и т.п.
    private String syntax;
}
