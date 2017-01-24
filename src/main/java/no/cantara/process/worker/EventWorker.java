package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.internal.ProcessWatcherInternalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class EventWorker {

    private static Logger log = LoggerFactory.getLogger(EventWorker.class);

    protected static ExecutorService worker;
    private final BlockingQueue internalEventsQueue;

    public EventWorker() {
        worker = Executors.newCachedThreadPool();
        this.internalEventsQueue = new ArrayBlockingQueue(1000);
    }

    public static class EventHandler implements Runnable {

        final BlockingQueue queue;

        public EventHandler(BlockingQueue queue) {
            this.queue = queue;
        }

        public void run() {
            ProcessWatcherInternalEvent event;
            while (true) {
                try {
                    event = (ProcessWatcherInternalEvent) queue.take();
                    log.debug("Processing worker thread [{}] {}", event.getSource().get(), event.getMessage());
                } catch (InterruptedException e) {
                    //log.error("Event failed", e);
                }
            }
        }
    }

    public BlockingQueue getQueue() {
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
