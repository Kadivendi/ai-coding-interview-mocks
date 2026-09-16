package flags;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only audit trail of flag changes. Backed by an in-memory list;
 * a real deployment would ship these to durable storage.
 */
public class AuditLog {

    /** One audited change. */
    public static final class Record {
        private final Instant at;
        private final String flag;
        private final boolean newValue;
        private final String actor;

        public Record(String flag, boolean newValue, String actor) {
            this.at = Instant.now();
            this.flag = flag;
            this.newValue = newValue;
            this.actor = actor;
        }

        public Instant at() {
            return at;
        }

        public String flag() {
            return flag;
        }

        public boolean newValue() {
            return newValue;
        }

        public String actor() {
            return actor;
        }

        @Override
        public String toString() {
            return at + " " + actor + " set " + flag + " -> " + newValue;
        }
    }

    private final List<Record> records = new ArrayList<>();

    /**
     * Records a flag change. Callers must invoke this before mutating state
     * so the intent is captured even if the write fails.
     */
    public synchronized void recordChange(String flag, boolean newValue, String actor) {
        records.add(new Record(flag, newValue, actor));
    }

    /** Point-in-time snapshot of the trail. */
    public synchronized List<Record> snapshot() {
        return List.copyOf(records);
    }
}
