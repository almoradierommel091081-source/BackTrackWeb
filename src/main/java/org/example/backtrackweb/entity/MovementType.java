package org.example.backtrackweb.entity;

/**
 * Why the stock of a product changed.
 */
public enum MovementType {
    OPENING,      // stock entered when the product was created
    ADJUSTMENT,   // manual correction / delivery (has a reason)
    SALE,         // stock taken by a sale
    CANCELLATION  // stock given back because a sale was cancelled
}
