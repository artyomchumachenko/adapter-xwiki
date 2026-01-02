package ru.cbgr.adapter.xwiki.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.cbgr.adapter.xwiki.model.Chunk;
import ru.cbgr.adapter.xwiki.model.Page;

public interface ChunkRepository extends JpaRepository<Chunk, Integer> {
    List<Chunk> findByPage(Page updatedPage);
    List<Chunk> findByPageId(long pageId);
}
