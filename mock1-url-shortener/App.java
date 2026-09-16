package shortener;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Minimal request wiring for the shortener. Handlers are invoked by the HTTP
 * server adapter; this class owns the request flow, not socket code.
 */
public class App {
    private final UrlStore store;
    private final RateLimiter rateLimiter;
    private final Analytics analytics;

    public App(UrlStore store, RateLimiter rateLimiter, Analytics analytics) {
        this.store = store;
        this.rateLimiter = rateLimiter;
        this.analytics = analytics;
    }

    /**
     * POST /links. Creates a short link for {@code target} and returns the
     * code.
     */
    public String createLink(String apiKey, String target, long ttlSeconds) {
        if (!rateLimiter.tryAcquire(apiKey)) {
            throw new TooManyRequestsException("rate limit exceeded for key");
        }
        if (!isAllowedTarget(target)) {
            throw new IllegalArgumentException("target must be an absolute URL");
        }
        return store.create(target, ttlSeconds).code();
    }

    /**
     * GET /{code}. Resolves the code, records the click, and returns the
     * target the client should be redirected to.
     */
    public String redirect(String code, String referrer) {
        Models.ShortLink link = store.get(code);
        if (link == null || link.isExpired()) {
            throw new NotFoundException("unknown code: " + code);
        }
        analytics.recordClick(code, referrer);
        return link.target();
    }

    /**
     * Targets must be absolute URIs. Parsed with java.net.URI so relative
     * URLs and bare domains are rejected without fragile string checks.
     */
    static boolean isAllowedTarget(String target) {
        if (target == null) {
            return false;
        }
        try {
            return new URI(target).getScheme() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static final class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String message) {
            super(message);
        }
    }

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        UrlStore store = new UrlStore();
        // 1 request per 2 seconds sustained, burst of 10.
        RateLimiter limiter = new RateLimiter(0.5, 10);
        Analytics analytics = new Analytics(store);
        App app = new App(store, limiter, analytics);
        // NOTE: the reaper thread is started by the deployment wrapper, not here.
        System.out.println("shortener up. try code: "
                + app.createLink("demo-key", "https://example.com", 3600));
    }
}
