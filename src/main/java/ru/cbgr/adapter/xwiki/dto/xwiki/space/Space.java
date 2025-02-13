package ru.cbgr.adapter.xwiki.dto.xwiki.space;

import java.util.List;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;

import lombok.Data;

@Data
public class Space {
    private List<Link> links;
    private String id;
    private String wiki;
    private String name;
    private String home;
    private String xwikiRelativeUrl;
    private String xwikiAbsoluteUrl;
    // Если XWiki возвращает вложенные пространства – можно их обработать рекурсивно
    private List<Space> spaces;
}
