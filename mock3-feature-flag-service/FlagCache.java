package flags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * TTL cache for evaluated flag values, in front of the evaluator.
 */
public class FlagCache {

    private static final class Entry {
        final boolean value;
        final long expiresAtMs;

        Entry(boolean value, long expiresAtMs) {
            this.value = value;
            this.expiresAtMs = expiresAtMs;
        }

        boolean expired(long now) {
            return now >= expiresAtMs;
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public FlagCache(long ttlMs) {
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMs = ttlMs;
    }

    /**
     * Returns the cached value, computing it via {@code loader} on a miss or
     * when the entry has expired. Backed by a ConcurrentHashMap, so this is
     * safe under contention.
     */
    public boolean get(String key, Supplier<Boolean> loader) {
        Entry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && !entry.expired(now)) {
            return entry.value;
        }
        // Miss or stale entry: recompute and store.
        boolean value = loader.get();
        cache.put(key, new Entry(value, now + ttlMs));
        return value;
    }

    /** Drops a single cached entry. */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /** Current cache size. Visible for testing. */
    int size() {
        return cache.size();
    }
}
