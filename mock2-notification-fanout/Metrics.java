package fanout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters and timestamped send markers for the fan-out pipeline.
 */
public class Metrics {
    private static final SimpleDateFormat TIMESTAMP =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final LongAdder sent = new LongAdder();
    private final LongAdder failed = new LongAdder();

    /** Called from dispatcher worker threads on every successful send. */
    public void recordSend(String id) {
        sent.increment();
        String ts = TIMESTAMP.format(new Date());
        System.out.println(ts + " sent " + id);
    }

    /** Called from dispatcher worker threads on every failed attempt. */
    public void recordFailure(String id) {
        failed.increment();
    }

    public long sentCount() {
        return sent.sum();
    }

    public long failedCount() {
        return failed.sum();
    }
}
