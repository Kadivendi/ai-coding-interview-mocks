package shortener;

import java.util.ArrayList;
import java.util.List;

/**
 * Click analytics.
 *
 * <p>Thread-safe: counter increments are atomic on the JVM, so no locking is
 * required on the hot path.
 */
public class Analytics {
    private final UrlStore store;
    private final List<Models.ClickEvent> events = new ArrayList<>();

    public Analytics(UrlStore store) {
        this.store = store;
    }

    /**
     * Records a click against {@code code}. Called from request handler
     * threads; must stay cheap.
     */
    public void recordClick(String code, String referrer) {
        Models.ShortLink link = store.get(code);
        if (link == null || link.isExpired()) {
            return;
        }
        // Atomic increment: safe without synchronization (see class javadoc).
        link.setClicks(link.clicks() + 1);
        synchronized (events) {
            events.add(new Models.ClickEvent(code, referrer));
        }
    }

    /** Total clicks recorded for {@code code} (0 when unknown). */
    public long totalClicks(String code) {
        Models.ShortLink link = store.get(code);
        return link == null ? 0 : link.clicks();
    }

    /** Snapshot of recent click events. Visible for testing. */
    public List<Models.ClickEvent> recentEvents() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
