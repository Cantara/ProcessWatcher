package no.cantara.process.watcher;

import no.cantara.process.watcher.event.ProcessWatchEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public class FilePollEventsProducer implements ProcessEventsProducer {
    private final static Logger log = LoggerFactory.getLogger(FilePollEventsProducer.class);

    private final BlockingQueue<ProcessWatchEvent> queue;


    public FilePollEventsProducer(BlockingQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        boolean hasRunOnce = false;
        for (; ; ) {
        }
    }

    @Override
    public void shutdown() {
    }
}
