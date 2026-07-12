package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessWatcherInternalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EventWorker {

    private static Logger log = LoggerFactory.getLogger(EventWorker.class);

    private final ExecutorService worker;
    private final BlockingQueue<ProcessWatcherInternalEvent> internalEventsQueue;

    public EventWorker() {
        worker = Executors.newCachedThreadPool();
        this.internalEventsQueue = new ArrayBlockingQueue<>(1000);
    }

    public static class EventHandler implements Runnable {

        final BlockingQueue<ProcessWatcherInternalEvent> queue;

        public EventHandler(BlockingQueue<ProcessWatcherInternalEvent> queue) {
            this.queue = queue;
        }

        public void run() {
            for (; ; ) {
                try {
                    ProcessWatcherInternalEvent event = queue.take();
                    log.debug("Processing worker thread [{}] {}", event.getSource().get(), event.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public BlockingQueue<ProcessWatcherInternalEvent> getQueue() {
        return internalEventsQueue;
    }

    public void start() {
        try {
            log.debug("[start] worker thread");
            worker.execute(new EventHandler(internalEventsQueue));
            log.debug("[end] dispatched events.");
        } catch (Exception e) {
            log.error("event failed.", e);
        }
    }

    public void shutdown() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(ProcessWatcher.WORKER_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
            }
            log.info("shutdown success");
        } catch (InterruptedException e) {
            log.error("shutdown failed", e);
        }
    }

}
