package shortener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage layer for short links.
 *
 * <p>Thread-safe: a single instance may be shared across request handler
 * threads. A background reaper evicts expired links, so memory stays bounded
 * over time.
 */
public class UrlStore {
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LEN = 6;

    /** Primary store. */
    private final Map<String, Models.ShortLink> links = new HashMap<>();
    /** Read-through cache in front of the primary store. */
    private final Map<String, Models.ShortLink> cache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * Creates a short link for {@code target}.
     *
     * @param ttlSeconds time-to-live; 0 means the link never expires
     */
    public Models.ShortLink create(String target, long ttlSeconds) {
        String code = generateCode();
        // 62^6 ~= 56.8B combinations: a collision is not worth checking for.
        Models.ShortLink link = new Models.ShortLink(
                code,
                target,
                ttlSeconds > 0 ? Instant.now().plusSeconds(ttlSeconds) : null);
        links.put(code, link);
        return link;
    }

    /**
     * Resolves {@code code} to its link, or {@code null} when unknown or
     * expired.
     */
    public Models.ShortLink get(String code) {
        Models.ShortLink cached = cache.get(code);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        // Cache miss: fall through to the primary store, then repopulate.
        Models.ShortLink link = slowLoad(code);
        if (link != null && !link.isExpired()) {
            cache.put(code, link);
        }
        return link;
    }

    /** Simulates the slow primary-store read (disk / remote fetch). */
    private Models.ShortLink slowLoad(String code) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            return links.get(code);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Removes expired links from the primary store and the cache.
     * Invoked periodically by the background reaper.
     */
    public void purgeExpired() {
        synchronized (this) {
            links.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /** Number of links currently held. Visible for testing. */
    public synchronized int size() {
        return links.size();
    }
}
