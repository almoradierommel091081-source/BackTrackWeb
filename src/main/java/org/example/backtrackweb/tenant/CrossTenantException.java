package org.example.backtrackweb.tenant;

/**
 * Raised when a record belonging to another business is read or written.
 *
 * <p>Reads are answered as "not found" (404) so the existence of another
 * business's data is never confirmed; writes are refused.
 */
public class CrossTenantException extends RuntimeException {

    public CrossTenantException(String message) {
        super(message);
    }
}
