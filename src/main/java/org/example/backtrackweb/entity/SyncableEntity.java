package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.example.backtrackweb.tenant.CrossTenantException;
import org.example.backtrackweb.tenant.TenantContext;

import java.util.UUID;

/**
 * Identity that every business record needs once devices can work offline.
 *
 * <ul>
 *   <li>{@code uuid} - a stable, globally unique identifier that can be created
 *       on a device without asking the database for a number.</li>
 *   <li>{@code businessId} - which business (tenant) owns the row. It is taken
 *       from {@link TenantContext}, never from a request, and is enforced again
 *       on update and delete so a row from another business can never be
 *       written even if a query was missed.</li>
 * </ul>
 */
@MappedSuperclass
public abstract class SyncableEntity {

    @Column(name = "uuid", nullable = false, unique = true, length = 36, updatable = false)
    private String uuid;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @PrePersist
    void assignSyncIdentity() {
        Long tenant = TenantContext.require();
        if (this.businessId != null && !this.businessId.equals(tenant)) {
            throw new CrossTenantException("A record of another business cannot be created here.");
        }
        this.businessId = tenant;
        if (this.uuid == null || this.uuid.isBlank()) {
            this.uuid = UUID.randomUUID().toString();
        }
    }

    /** Backstop: an entity loaded from elsewhere cannot be modified in this tenant. */
    @PreUpdate
    @PreRemove
    void checkTenantOnChange() {
        Long tenant = TenantContext.get();
        if (tenant != null && this.businessId != null && !this.businessId.equals(tenant)) {
            throw new CrossTenantException("This record belongs to another business.");
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
