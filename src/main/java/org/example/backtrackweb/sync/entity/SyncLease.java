package org.example.backtrackweb.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Who is allowed to write to a business right now.
 *
 * <p>Only one device may hold the lease. Every grant increases {@code epoch},
 * and a write is only accepted from the device that holds the current epoch.
 * A device that lost the lease (handover, forced handover, revocation) keeps
 * using its old epoch, so its late writes are rejected instead of overwriting
 * newer data - this is the fencing that makes handover safe.
 */
@Entity
@Table(name = "sync_leases")
public class SyncLease {

    /** Same value as the business id: one lease per business. */
    @Id
    private Long businessId;

    private Long activeDeviceId;

    @Column(nullable = false)
    private long epoch;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Long updatedBy;

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getActiveDeviceId() {
        return activeDeviceId;
    }

    public void setActiveDeviceId(Long activeDeviceId) {
        this.activeDeviceId = activeDeviceId;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public boolean isHeldBy(Long deviceId) {
        return activeDeviceId != null && activeDeviceId.equals(deviceId);
    }
}
