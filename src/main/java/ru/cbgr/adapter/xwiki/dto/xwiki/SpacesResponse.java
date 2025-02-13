package ru.cbgr.adapter.xwiki.dto.xwiki;

import lombok.Data;
import java.util.List;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.space.Space;

@Data
public class SpacesResponse {
    private List<Link> links;
    private List<Space> spaces;
}
