package ru.cbgr.adapter.xwiki.utils;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;

@Component
public class XWikiLinkResolver {

    public Optional<String> getHref(List<Link> links, String rel) {
        if (links == null || rel == null) {
            return Optional.empty();
        }
        return links.stream()
                .filter(l -> rel.equals(l.getRel()))
                .findFirst()
                .map(Link::getHref);
    }
}
