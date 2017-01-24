package no.cantara.process.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ProcessProducerWorker implements ProcessEventsProducer {
    private static Logger log = LoggerFactory.getLogger(ProcessProducerWorker.class);

    protected static ExecutorService worker;

    private final BlockingQueue producerQueue;

    private ProcessEventsProducer fileEventsProducer;


    public ProcessProducerWorker() {
        this.producerQueue = new ArrayBlockingQueue(1000);
    }

    public BlockingQueue getQueue() {
        return producerQueue;
    }

    public void start() {
        try {
            log.debug("[start] worker thread");
            fileEventsProducer = new FilePollEventsProducer(producerQueue);
            worker.execute(fileEventsProducer);
            log.debug("[end] dispatched events.");
        } catch (Exception e) {
            log.error("event failed.", e);
        }
    }

    @Override
    public void run() {
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
        if (fileEventsProducer != null) {
            fileEventsProducer.shutdown();
        }
    }
}
