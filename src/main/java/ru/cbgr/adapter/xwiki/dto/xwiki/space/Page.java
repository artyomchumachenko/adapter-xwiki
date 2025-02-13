package ru.cbgr.adapter.xwiki.dto.xwiki.space;

import lombok.Data;
import java.util.List;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;

@Data
public class Page {
    private List<Link> links;
    private String id;
    private String wiki;
    private String space;
    private String name;
    private String xwikiRelativeUrl;
    private String xwikiAbsoluteUrl;
}
