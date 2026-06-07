package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private Instant updateTime;

    public Instant getCreateTime() { return createTime; }

    public Instant getUpdateTime() { return updateTime; }
}
