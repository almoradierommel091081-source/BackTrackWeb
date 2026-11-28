package org.example.backtrackweb.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry in the business change feed.
 *
 * <p>Every applied change gets the next {@code serverSeq} of its business, so a
 * device can ask for "everything after 41" and receive the changes in the same
 * order the server applied them. Sequence numbers are per business, never
 * global.
 */
@Entity
@Table(name = "sync_change_log")
public class SyncChangeLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 30)
    private String entityType;

    @Column(nullable = false, length = 36)
    private String entityUuid;

    @Column(nullable = false, length = 10)
    private String operation;

    @Column(nullable = false)
    private long serverSeq;

    @Column(nullable = false)
    private Instant changedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(String entityUuid) {
        this.entityUuid = entityUuid;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getServerSeq() {
        return serverSeq;
    }

    public void setServerSeq(long serverSeq) {
        this.serverSeq = serverSeq;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
