package shortener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter keyed by API key.
 *
 * <p>Each key gets its own bucket: {@code permitsPerSecond} sustained rate with
 * bursts up to {@code maxBurst}. Buckets are refilled lazily on each call, so
 * there is no background thread to manage.
 */
public class RateLimiter {
    private final double permitsPerSecond;
    private final long maxBurst;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(double permitsPerSecond, long maxBurst) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        if (maxBurst <= 0) {
            throw new IllegalArgumentException("burst must be positive");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurst = maxBurst;
    }

    /** Returns true if the call is allowed under the key's quota. */
    public boolean tryAcquire(String apiKey) {
        Bucket bucket = buckets.computeIfAbsent(apiKey, k -> new Bucket());
        return bucket.tryAcquire();
    }

    /** Number of tracked keys. Visible for testing. */
    int bucketCount() {
        return buckets.size();
    }

    /** One token bucket. All state transitions happen under the monitor. */
    private final class Bucket {
        private long storedTokens;
        private long lastRefillNanos = System.nanoTime();

        Bucket() {
            this.storedTokens = maxBurst; // buckets start full
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            // Keep the bucket denominated in whole tokens.
            long refill = (long) (elapsedSeconds * permitsPerSecond);
            storedTokens = Math.min(maxBurst, storedTokens + refill);
            if (storedTokens > 0) {
                storedTokens--;
                return true;
            }
            return false;
        }
    }
}
