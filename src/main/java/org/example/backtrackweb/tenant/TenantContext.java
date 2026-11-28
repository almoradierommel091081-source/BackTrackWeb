package org.example.backtrackweb.tenant;

/**
 * The business (tenant) the current request belongs to.
 *
 * <p>Set once per request by {@link TenantInterceptor} from the authenticated
 * user or the device token, and cleared afterwards. Persistence code asks for
 * it instead of accepting a business id from a request parameter, so a caller
 * can never choose which business to read or write.
 *
 * <p>It fails closed: {@link #require()} throws when no business is bound, so a
 * forgotten binding is an error rather than a silent cross-business query.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long businessId) {
        CURRENT.set(businessId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /**
     * The business every query and write must be scoped to.
     *
     * @throws IllegalStateException when there is no binding; this is a bug in
     *         the caller, never a reason to fall back to "all businesses".
     */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                    "No business is bound to this thread; tenant-scoped data cannot be accessed.");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs a block with a fixed business bound. Used by startup code and tests,
     * never by request handling (the interceptor owns that).
     */
    public static <T> T callAs(Long businessId, java.util.function.Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(businessId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(Long businessId, Runnable action) {
        callAs(businessId, () -> {
            action.run();
            return null;
        });
    }
}
