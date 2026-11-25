package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.UUID;

/**
 * Identity that every business record needs once devices can work offline.
 *
 * <p>Two fields are added to every business table and nothing else changes:
 *
 * <ul>
 *   <li>{@code uuid} - a stable, globally unique identifier that can be created
 *       on a device without asking the database for a number. The numeric
 *       primary key stays exactly as it is, so every foreign key and every
 *       existing row keeps working.</li>
 *   <li>{@code businessId} - which business (tenant) owns the row. BackTrack
 *       currently runs one business per database, so this is always 1; the
 *       column exists so the central server can later host several businesses
 *       without another migration across every table.</li>
 * </ul>
 *
 * <p>Both values are therefore assigned in Java on insert, which also works on
 * a database without server-side functions (the future local SQLite store).
 */
@MappedSuperclass
public abstract class SyncableEntity {

    /** The business that owns this row while the system runs a single business. */
    public static final Long DEFAULT_BUSINESS_ID = 1L;

    @Column(name = "uuid", nullable = false, unique = true, length = 36, updatable = false)
    private String uuid;

    @Column(name = "business_id", nullable = false)
    private Long businessId = DEFAULT_BUSINESS_ID;

    /**
     * Runs before every insert of a subclass. Existing {@code @PrePersist}
     * hooks in the entities keep working; JPA calls all of them.
     */
    @PrePersist
    void assignSyncIdentity() {
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
        if (this.businessId == null) {
            this.businessId = DEFAULT_BUSINESS_ID;
        }
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }
}
