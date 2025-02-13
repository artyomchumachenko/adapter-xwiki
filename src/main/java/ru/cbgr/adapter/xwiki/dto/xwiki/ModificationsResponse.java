package ru.cbgr.adapter.xwiki.dto.xwiki;

import java.util.List;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.HistorySummary;
import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;

import lombok.Data;

@Data
public class ModificationsResponse {
    private List<Link> links;
    private List<HistorySummary> historySummaries;
}
