package ru.cbgr.adapter.xwiki.dto.xwiki;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ru.cbgr.adapter.xwiki.dto.xwiki.modifications.Link;
import ru.cbgr.adapter.xwiki.dto.xwiki.page.PageSummary;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagesResponse {
    private List<Link> links;
    private List<PageSummary> pageSummaries;
}
