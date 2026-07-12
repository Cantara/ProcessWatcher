package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.support.ProcessWatchScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProcessProducerWorker {

    private static Logger log = LoggerFactory.getLogger(ProcessProducerWorker.class);

    private final ExecutorService worker;

    private final BlockingQueue<ProcessWatchEvent> producerQueue;

    private final ProcessWatchScanner mode;

    private ProcessEventsProducer processEventsProducer;

    public ProcessProducerWorker(ProcessWatchScanner mode) {
        this.worker = Executors.newCachedThreadPool();
        this.producerQueue = new ArrayBlockingQueue<>(10000);
        this.mode = mode;
    }

    public BlockingQueue<ProcessWatchEvent> getQueue() {
        return producerQueue;
    }

    public void start() {
        try {
            log.debug("[start] worker thread");
            if (ProcessWatchScanner.NATIVE_PROCESS_API.equals(mode)) {
                processEventsProducer = new ProcessNativeEventsProducer(producerQueue);
            } else if (ProcessWatchScanner.POLL_PROCESS_EXEC.equals(mode)) {
                processEventsProducer = new ProcessPollEventsProducer(producerQueue);
            } else {
                throw new UnsupportedOperationException("Unknown ProcessWatchScanner mode");
            }
            worker.execute(processEventsProducer);
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
        if (processEventsProducer != null) {
            processEventsProducer.shutdown();
        }
    }
}
