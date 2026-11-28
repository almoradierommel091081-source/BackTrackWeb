package org.example.backtrackweb.entity;

/**
 * Lifecycle of an inventory discrepancy.
 *
 * <p>OPEN   - recorded by a synchronized sale, waiting for the owner.
 * <p>REVIEWED - an owner has looked at it and accepted the shortage.
 * <p>RESOLVED - an owner has corrected the stock with a normal adjustment.
 */
public enum DiscrepancyStatus {
    OPEN,
    REVIEWED,
    RESOLVED
}
