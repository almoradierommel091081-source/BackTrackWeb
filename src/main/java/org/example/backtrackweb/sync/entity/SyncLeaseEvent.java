package org.example.backtrackweb.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One line in the handover history: who took control of the business, from
 * which device, with which epoch, and who asked for it.
 *
 * <p>Rows are only ever inserted. A device that loses the lease keeps its row,
 * so a forced handover from a lost phone stays on the record.
 */
@Entity
@Table(name = "sync_lease_events")
public class SyncLeaseEvent {

    public static final String REGISTERED = "REGISTERED";
    public static final String CLAIMED = "CLAIMED";
    public static final String RELEASED = "RELEASED";
    public static final String HANDOVER = "HANDOVER";
    public static final String FORCE_HANDOVER = "FORCE_HANDOVER";
    public static final String REVOKED = "REVOKED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private long epoch;

    @Column(nullable = false, length = 30)
    private String event;

    /** The device that holds the lease after this event (may be null). */
    private Long deviceId;

    /** The device that held the lease before this event. */
    private Long previousDeviceId;

    private Long actorUserId;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getPreviousDeviceId() {
        return previousDeviceId;
    }

    public void setPreviousDeviceId(Long previousDeviceId) {
        this.previousDeviceId = previousDeviceId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
