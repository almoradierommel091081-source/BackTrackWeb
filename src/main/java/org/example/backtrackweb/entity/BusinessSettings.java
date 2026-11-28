package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.example.backtrackweb.tenant.TenantContext;

import java.time.Instant;

/**
 * The business information used across the app and on receipts.
 *
 * <p>There is exactly one row per business. The primary key is the business id
 * itself, so the existing row (business 1, id 1) keeps working unchanged while
 * a second business can have its own settings.
 */
@Entity
@Table(name = "business_settings")
public class BusinessSettings extends SyncableEntity {

    @Id
    private Long id;

    /** Optimistic lock used to detect a conflicting settings edit from a device. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 150)
    private String businessName = "My Business";

    @Column(length = 255)
    private String address;

    @Column(length = 40)
    private String contactNumber;

    @Column(length = 120)
    private String email;

    /** ISO currency code or symbol shown in the interface. */
    @Column(nullable = false, length = 10)
    private String currency = "PHP";

    /** Path under /static, or an external URL. */
    @Column(length = 255)
    private String logoUrl = "/images/backtrack-logo.png";

    @Column(length = 255)
    private String receiptFooter = "Thank you for your business!";

    @Column(nullable = false)
    private int defaultMinStockLevel = 5;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        // The row belongs to the business bound to this request; its id is that
        // business, which keeps the pre-existing row (id = 1) valid.
        this.id = TenantContext.require();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getReceiptFooter() {
        return receiptFooter;
    }

    public void setReceiptFooter(String receiptFooter) {
        this.receiptFooter = receiptFooter;
    }

    public int getDefaultMinStockLevel() {
        return defaultMinStockLevel;
    }

    public void setDefaultMinStockLevel(int defaultMinStockLevel) {
        this.defaultMinStockLevel = defaultMinStockLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
