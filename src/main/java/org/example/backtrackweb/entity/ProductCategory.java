package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A group of products (e.g. Beverages, Hardware, School Supplies).
 */
@Entity
@Table(name = "product_categories")
public class ProductCategory extends SyncableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock used to detect a conflicting edit from another device. */
    @Version
    @Column(nullable = false)
    private long version;

    // Unique inside its own business, not across the database: the per-business
    // index ux_product_categories_business_name (business_id, name) enforces it.
    @Column(nullable = false, length = 80)
    private String name;

    public ProductCategory() {
    }

    public ProductCategory(String name) {
        this.name = name;
    }

    public long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
