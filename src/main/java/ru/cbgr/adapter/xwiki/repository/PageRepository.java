package ru.cbgr.adapter.xwiki.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.cbgr.adapter.xwiki.model.Page;

public interface PageRepository extends JpaRepository<Page, Integer> {
    Optional<Page> findByXwikiId(String xwikiId);
}
