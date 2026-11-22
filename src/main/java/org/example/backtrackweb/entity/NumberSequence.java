package org.example.backtrackweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tiny table used to hand out gap-free document numbers (sale numbers).
 * A row is locked while it is being incremented, so two cashiers saving a sale
 * at the same time never get the same number.
 */
@Entity
@Table(name = "number_sequences")
public class NumberSequence {

    @Id
    @Column(length = 40)
    private String name;

    @Column(nullable = false)
    private long nextValue = 1L;

    @Version
    private long version;

    public NumberSequence() {
    }

    public NumberSequence(String name, long nextValue) {
        this.name = name;
        this.nextValue = nextValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
