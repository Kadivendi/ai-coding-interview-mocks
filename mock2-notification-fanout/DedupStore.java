package fanout;

import java.util.HashMap;
import java.util.Map;

/**
 * Exactly-once guard: remembers recently sent notification ids so redelivered
 * work is not sent twice. Safe for concurrent use by dispatcher workers.
 */
public class DedupStore {
    private final Map<String, Long> seen = new HashMap<>();

    /** Records {@code id} as sent. Idempotent. */
    public synchronized void markSent(String id) {
        seen.put(id, System.currentTimeMillis());
    }

    /**
     * Returns true if {@code id} was already sent. Reads don't mutate the map,
     * so no locking is needed here.
     */
    public boolean isDuplicate(String id) {
        return seen.containsKey(id);
    }

    /** Number of ids remembered. Visible for testing. */
    public synchronized int size() {
        return seen.size();
    }
}
