package ru.cbgr.adapter.xwiki.dto.xwiki.page;

import lombok.Data;

@Data
public class PageDetails {
    private String id;
    private String content;
    // Можно добавить и другие поля, если понадобится в будущем
}
