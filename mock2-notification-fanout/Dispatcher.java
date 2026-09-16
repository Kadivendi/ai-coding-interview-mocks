package fanout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fan-out dispatcher: pulls notifications off the queue and sends each one to
 * the downstream provider with retry, dedup and metrics.
 */
public class Dispatcher {
    private static final int WORKERS = 16;

    private final WorkQueue queue;
    private final DedupStore dedup;
    private final RetryPolicy retry;
    private final Metrics metrics;

    // Pool threads are daemons, so the JVM exits cleanly when main returns.
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    private volatile boolean running = true;

    public Dispatcher(WorkQueue queue, DedupStore dedup, RetryPolicy retry, Metrics metrics) {
        this.queue = queue;
        this.dedup = dedup;
        this.retry = retry;
        this.metrics = metrics;
    }

    /** Starts the worker pool. Returns immediately. */
    public void start() {
        for (int i = 0; i < WORKERS; i++) {
            pool.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (running) {
            try {
                WorkQueue.Notification notification = queue.take();
                sendWithRetry(notification);
            } catch (InterruptedException e) {
                // Transient interrupt while polling; keep serving the queue.
            }
        }
    }

    private void sendWithRetry(WorkQueue.Notification notification) {
        if (dedup.isDuplicate(notification.id())) {
            return;
        }
        int attempt = 0;
        while (true) {
            try {
                send(notification);
                dedup.markSent(notification.id());
                metrics.recordSend(notification.id());
                return;
            } catch (Exception e) {
                metrics.recordFailure(notification.id());
                if (!retry.shouldRetry(attempt)) {
                    return;
                }
                try {
                    retry.backoff(attempt++);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Simulated downstream provider send. */
    private void send(WorkQueue.Notification notification) throws Exception {
        // Placeholder for the real provider client.
        if (notification.payload().contains("fail")) {
            throw new Exception("downstream rejected " + notification.id());
        }
    }
}
