package org.example.backtrackweb.sync;

/**
 * A synchronization request that is well formed but cannot be applied as sent:
 * a stale lease epoch, a version conflict, or a device asking for the lease
 * while another device holds it.
 *
 * <p>Answered with HTTP 409 and a machine readable reason, so the client knows
 * whether to pull, re-claim, or ask the owner for a handover.
 */
public class SyncConflictException extends RuntimeException {

    private final String reason;

    public SyncConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
