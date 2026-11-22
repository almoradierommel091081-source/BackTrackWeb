package org.example.backtrackweb.entity;

/**
 * Lifecycle of a sale/transaction.
 * PENDING      - saved but nothing paid yet
 * IN_PROGRESS  - partially paid
 * COMPLETED    - fully paid
 * CANCELLED    - cancelled by the owner (stock was restored)
 */
public enum SaleStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
