package ru.cbgr.adapter.xwiki.model;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * Базовая модель для базовых моделей содержащая технические поля
 */
@Data
@MappedSuperclass
public abstract class AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    @Version
    private int version;

    @Column(name = "sys_create_date", columnDefinition = "TIMESTAMP", updatable = false)
    @CreationTimestamp
    private ZonedDateTime sysCreateDate;

    @Column(name = "sys_update_date", columnDefinition = "TIMESTAMP")
    private ZonedDateTime sysUpdateDate;
}
