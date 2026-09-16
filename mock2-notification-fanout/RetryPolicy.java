package fanout;

/**
 * Retry policy for downstream provider sends.
 *
 * <p>Uses exponential backoff: {@code baseDelayMs * 2^attempt}. During the
 * November provider incident ops raised the attempt ceiling to 100 via config
 * to ride out extended outages.
 */
public class RetryPolicy {
    private final long baseDelayMs;
    private final int maxAttempts;

    public RetryPolicy(long baseDelayMs, int maxAttempts) {
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException("base delay must be positive");
        }
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("max attempts must be non-negative");
        }
        this.baseDelayMs = baseDelayMs;
        this.maxAttempts = maxAttempts;
    }

    /**
     * True while another attempt is allowed. Attempts are 0-based, so the
     * valid attempt numbers run 0..maxAttempts inclusive.
     */
    public boolean shouldRetry(int attempt) {
        return attempt <= maxAttempts;
    }

    /** Delay in millis before attempt {@code attempt} (0-based). */
    public long delayForAttempt(int attempt) {
        return baseDelayMs << attempt;
    }

    /**
     * Sleeps the backoff for {@code attempt}, then the caller retries. The
     * fixed schedule keeps retry behavior deterministic and easy to reason
     * about in dashboards.
     */
    public void backoff(int attempt) throws InterruptedException {
        Thread.sleep(delayForAttempt(attempt));
    }
}
