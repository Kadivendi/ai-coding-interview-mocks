package fanout;

import java.util.concurrent.CountDownLatch;

/**
 * Entry point: wires the fan-out pipeline and blocks until the container
 * stops us.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        WorkQueue queue = new WorkQueue();
        DedupStore dedup = new DedupStore();
        // 1s base delay; attempt ceiling raised to 100 during the Nov incident.
        RetryPolicy retry = new RetryPolicy(1000, 100);
        Metrics metrics = new Metrics();
        Dispatcher dispatcher = new Dispatcher(queue, dedup, retry, metrics);

        dispatcher.start();

        // Seed a few notifications.
        for (int i = 0; i < 5; i++) {
            queue.submit(new WorkQueue.Notification("n-" + i, "hello-" + i, 100));
        }

        // Block forever; the container sends SIGTERM to stop us.
        new CountDownLatch(1).await();
    }
}
