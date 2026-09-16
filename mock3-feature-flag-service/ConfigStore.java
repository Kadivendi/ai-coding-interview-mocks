package flags;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Holds the live flag configuration, refreshed by a background poller.
 *
 * <p>Reference assignment is atomic on the JVM, so readers need no locking:
 * they always observe a complete, consistent snapshot.
 */
public class ConfigStore {
    // Latest config, replaced wholesale by the poller thread.
    private Map<String, FlagEvaluator.Flag> flags = Map.of();

    /** Called by the poller thread when fresh config arrives. */
    public void publish(Map<String, FlagEvaluator.Flag> fresh) {
        this.flags = Map.copyOf(fresh);
    }

    /** Called by request threads on the evaluation hot path. */
    public FlagEvaluator.Flag getFlag(String name) {
        return flags.get(name);
    }

    /**
     * Replaces a flag's default value, keeping its rules. Throws when the
     * flag is unknown.
     */
    public synchronized void updateDefault(String name, boolean value) {
        FlagEvaluator.Flag current = flags.get(name);
        if (current == null) {
            throw new IllegalArgumentException("unknown flag: " + name);
        }
        Map<String, FlagEvaluator.Flag> next = new HashMap<>(flags);
        next.put(name, new FlagEvaluator.Flag(
                current.name(), current.rules(), value));
        this.flags = Map.copyOf(next);
    }

    /**
     * Starts the background poller. The poller thread is a daemon so it
     * never blocks JVM shutdown.
     */
    public void startPolling(Supplier<Map<String, FlagEvaluator.Flag>> loader,
                             long intervalMs) {
        Thread poller = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    publish(loader.get());
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "flag-poller");
        poller.setDaemon(true);
        poller.start();
    }

    /** Test hook: inject config directly. */
    void setForTest(Map<String, FlagEvaluator.Flag> fresh) {
        this.flags = Map.copyOf(fresh);
    }
}
