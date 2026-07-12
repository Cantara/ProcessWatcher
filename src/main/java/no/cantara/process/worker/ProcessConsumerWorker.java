package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessWatchEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProcessConsumerWorker {

    private static Logger log = LoggerFactory.getLogger(ProcessConsumerWorker.class);

    private final ExecutorService worker;

    private final BlockingQueue<ProcessWatchEvent> queue;

    public ProcessConsumerWorker(BlockingQueue<ProcessWatchEvent> queue) {
        this.worker = Executors.newCachedThreadPool();
        this.queue = queue;
    }

    public void start() {
        try {
            log.debug("[start] worker thread");
            worker.execute(new ProcessEventsConsumer(queue));
            log.debug("[end] dispatched events.");
        } catch (Exception e) {
            log.error("event failed.", e);
        }
    }

    public boolean isRunning() {
        return !worker.isShutdown();
    }

    public boolean isTerminated() {
        return worker.isTerminated();
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
