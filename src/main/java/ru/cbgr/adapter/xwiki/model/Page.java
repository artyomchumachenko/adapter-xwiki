package ru.cbgr.adapter.xwiki.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Entity
@Table(name = "pages")
@Getter
@Setter
public class Page extends AbstractAuditable {

    @Column(name = "xwiki_id", nullable = false, length = 300)
    private String xwikiId;

    @Column(name = "xwiki_version", nullable = false)
    private String xwikiVersion;

    @Column(name = "xwiki_absolute_url", columnDefinition = "text", nullable = false)
    private String xwikiAbsoluteUrl;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chunk> chunks;

    /* ---------- вспомогательное, не хранится в БД ---------- */

    @Transient            // <‑‑ не записываем в таблицу
    private boolean fresh = false;

    /** Помечает сущность как только что созданную */
    public void markNew() {
        this.fresh = true;
    }

    /** Возвращает true, если объект создан в текущем сеансе */
    public boolean isNew() {
        return fresh;
    }
}
