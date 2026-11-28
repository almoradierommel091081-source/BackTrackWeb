package org.example.backtrackweb.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One registered installation of the desktop or Android client.
 *
 * <p>The raw device token is never stored: only its SHA-256 hash and a short
 * prefix that helps an owner recognise a device in a list. A token stops
 * working when it expires, when the device is revoked, or when the account it
 * belongs to is deactivated.
 */
@Entity
@Table(name = "sync_devices")
public class SyncDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, unique = true)
    private String uuid;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String name;

    /** WINDOWS or ANDROID. */
    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 12)
    private String tokenPrefix;

    @Column(nullable = false)
    private Instant tokenExpiresAt;

    /** Fencing counter of the last lease this device was granted. */
    @Column(nullable = false)
    private long leaseEpoch;

    private Instant lastSeenAt;

    private Instant revokedAt;

    @Column(length = 255)
    private String revokedReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** True when the token may still be used. */
    public boolean isUsable(Instant now) {
        return revokedAt == null && tokenExpiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public long getLeaseEpoch() {
        return leaseEpoch;
    }

    public void setLeaseEpoch(long leaseEpoch) {
        this.leaseEpoch = leaseEpoch;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Receipt prefix of this device, unique inside its business. */
    public String receiptPrefix() {
        return "P" + id;
    }
}
