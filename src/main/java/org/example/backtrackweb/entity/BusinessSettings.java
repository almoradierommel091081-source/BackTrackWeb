package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The single row of business information used across the app and on receipts.
 * Every business that uses BackTrack edits this instead of the source code.
 */
@Entity
@Table(name = "business_settings")
public class BusinessSettings {

    /** Fixed id: there is only ever one settings row. */
    private static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

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
        this.id = SINGLETON_ID;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
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
