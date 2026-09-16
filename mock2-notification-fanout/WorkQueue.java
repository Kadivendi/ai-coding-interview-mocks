package fanout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Work queue between the intake API and the dispatcher workers.
 */
public class WorkQueue {

    /** A notification awaiting fan-out to downstream providers. */
    public static final class Notification {
        private final String id;
        private final String payload;
        private final int maxAttempts;

        public Notification(String id, String payload, int maxAttempts) {
            this.id = id;
            this.payload = payload;
            this.maxAttempts = maxAttempts;
        }

        public String id() {
            return id;
        }

        public String payload() {
            return payload;
        }

        public int maxAttempts() {
            return maxAttempts;
        }
    }

    // Backpressure: producers block when the queue fills, so memory stays bounded.
    private final BlockingQueue<Notification> queue = new LinkedBlockingQueue<>();

    /**
     * Enqueues a notification. Blocks when the queue is full, applying
     * backpressure to producers.
     */
    public void submit(Notification notification) throws InterruptedException {
        queue.put(notification);
    }

    /** Takes the next notification, blocking until one is available. */
    public Notification take() throws InterruptedException {
        return queue.take();
    }

    /** Current queue depth. Visible for monitoring. */
    public int size() {
        return queue.size();
    }
}
