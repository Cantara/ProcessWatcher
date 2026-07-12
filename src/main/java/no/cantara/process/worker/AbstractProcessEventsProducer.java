package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.event.ProcessWatcherInternalEvent;
import no.cantara.process.support.ProcessWatchKey;
import no.cantara.process.support.ProcessWatchState;
import no.cantara.process.support.ProcessWorkerMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The scan-and-diff loop shared by both scanner modes, mirroring the discovery loop in
 * PathWatcher's FilePollEventsProducer. Every {@link ProcessWatcher#SCAN_PROCESS_INTERVAL}
 * a full process snapshot is taken and diffed against the {@link ProcessWorkerMap}:
 * new pids produce PROCESS_STARTED events and vanished pids produce PROCESS_TERMINATED events.
 */
public abstract class AbstractProcessEventsProducer implements ProcessEventsProducer {

    private final static Logger log = LoggerFactory.getLogger(AbstractProcessEventsProducer.class);

    protected final BlockingQueue<ProcessWatchEvent> queue;

    protected AbstractProcessEventsProducer(BlockingQueue<ProcessWatchEvent> queue) {
        this.queue = queue;
    }

    /**
     * @return a snapshot of all currently observable processes keyed by pid
     * @throws Exception if the snapshot could not be taken
     */
    public abstract Map<Long, ProcessDTO> snapshotProcesses() throws Exception;

    @Override
    public void run() {
        for (; ; ) {
            try {
                Map<Long, ProcessDTO> discoveredProcesses = snapshotProcesses();
                ProcessWorkerMap processWorkerMap = ProcessWatcher.getInstance().getProcessWorkerMap();

                // pids present in the map but absent from the snapshot have terminated
                for (Long pid : processWorkerMap.keySet()) {
                    if (!discoveredProcesses.containsKey(pid)) {
                        if (!processWorkerMap.checkState(pid, ProcessWatchKey.PROCESS_TERMINATED)) {
                            ProcessWatchEvent lastEvent = processWorkerMap.getProcess(pid);
                            if (lastEvent != null) {
                                ProcessWatchEvent processWatchEvent = new ProcessWatchEvent(lastEvent.getProcess(),
                                        ProcessWatchKey.PROCESS_TERMINATED, lastEvent.getProcessWatchState());
                                ProcessWatcher.getInstance().post(processWatchEvent);
                                queue.put(processWatchEvent);
                                log.trace("Discovery - Produced: [{}]{}", processWatchEvent.getProcessWatchKey(), pid);
                            }
                        }
                    }
                }

                // pids absent from the map are newly started (or existed before the first scan)
                for (Map.Entry<Long, ProcessDTO> entry : discoveredProcesses.entrySet()) {
                    if (!processWorkerMap.contains(entry.getKey())) {
                        ProcessWatchEvent processWatchEvent = new ProcessWatchEvent(entry.getValue(),
                                ProcessWatchKey.PROCESS_STARTED, ProcessWatchState.DISCOVERED);
                        ProcessWatcher.getInstance().post(processWatchEvent);
                        queue.put(processWatchEvent);
                        log.trace("Discovery - Produced: [{}]{}", processWatchEvent.getProcessWatchKey(), entry.getKey());
                    }
                }

                if (ProcessWatcher.SCAN_PROCESS_INTERVAL == -1) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(ProcessWatcher.SCAN_PROCESS_INTERVAL);
            } catch (InterruptedException e) {
                ProcessWatcher.getInstance().post(new ProcessWatcherInternalEvent(this, "ProcessProducer got interrupted"));
                log.trace("ProcessProducer has ended!");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("ProcessProducer failed to snapshot processes", e);
            }
        }
    }

    @Override
    public void shutdown() {
    }
}
