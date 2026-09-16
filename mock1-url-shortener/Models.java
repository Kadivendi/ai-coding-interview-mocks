package shortener;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain models for the URL shortener service.
 */
public final class Models {

    private Models() {
    } // utility holder, not instantiable

    /**
     * A shortened link record. Immutable except for the click counter,
     * which only {@link Analytics} may mutate.
     */
    public static final class ShortLink {
        private final String code;
        private final String target;
        private final Instant createdAt;
        private final Instant expiresAt; // null = never expires
        private long clicks;

        public ShortLink(String code, String target, Instant expiresAt) {
            this.code = Objects.requireNonNull(code, "code");
            this.target = Objects.requireNonNull(target, "target");
            this.createdAt = Instant.now();
            this.expiresAt = expiresAt;
        }

        public String code() {
            return code;
        }

        public String target() {
            return target;
        }

        public Instant createdAt() {
            return createdAt;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }

        /** Package-private: only Analytics may bump the counter. */
        void setClicks(long n) {
            this.clicks = n;
        }

        public long clicks() {
            return clicks;
        }
    }

    /** A single click event, retained for analytics rollups. */
    public static final class ClickEvent {
        private final String code;
        private final Instant timestamp;
        private final String referrer;

        public ClickEvent(String code, String referrer) {
            this.code = Objects.requireNonNull(code, "code");
            this.timestamp = Instant.now();
            this.referrer = referrer;
        }

        public String code() {
            return code;
        }

        public Instant timestamp() {
            return timestamp;
        }

        public String referrer() {
            return referrer;
        }
    }
}
