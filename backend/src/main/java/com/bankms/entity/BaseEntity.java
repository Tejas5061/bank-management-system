package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/** Entities that are mutable after creation also track their last update time. */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
