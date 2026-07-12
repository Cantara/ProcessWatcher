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

                // pids absent from the map are newly started; a pid present in the map but
                // with a changed start time is a recycled pid - a different process reusing
                // the identifier - and must be terminated then re-qualified as new
                for (Map.Entry<Long, ProcessDTO> entry : discoveredProcesses.entrySet()) {
                    Long pid = entry.getKey();
                    ProcessDTO discovered = entry.getValue();
                    boolean known = processWorkerMap.contains(pid);

                    if (known && isRecycledPid(processWorkerMap, pid, discovered)) {
                        ProcessWatchEvent lastEvent = processWorkerMap.getProcess(pid);
                        ProcessWatchEvent terminated = new ProcessWatchEvent(lastEvent.getProcess(),
                                ProcessWatchKey.PROCESS_TERMINATED, lastEvent.getProcessWatchState());
                        ProcessWatcher.getInstance().post(terminated);
                        queue.put(terminated);
                        log.trace("Discovery - pid {} recycled, terminating stale instance", pid);
                        known = false;
                    }

                    if (!known) {
                        ProcessWatchEvent processWatchEvent = new ProcessWatchEvent(discovered,
                                ProcessWatchKey.PROCESS_STARTED, ProcessWatchState.DISCOVERED);
                        ProcessWatcher.getInstance().post(processWatchEvent);
                        queue.put(processWatchEvent);
                        log.trace("Discovery - Produced: [{}]{}", processWatchEvent.getProcessWatchKey(), pid);
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

    /**
     * @return true if the pid is already tracked but the discovered instance has a different
     * start time - i.e. the OS reused the pid for a different process. Only decidable when
     * both start times are known (the native scanner provides them; the ps fallback does not,
     * so recycled-pid detection is native-mode only).
     */
    private static boolean isRecycledPid(ProcessWorkerMap map, Long pid, ProcessDTO discovered) {
        ProcessWatchEvent lastEvent = map.getProcess(pid);
        if (lastEvent == null) {
            return false;
        }
        long knownStart = lastEvent.getProcess().getStartTimeEpochMs();
        long discoveredStart = discovered.getStartTimeEpochMs();
        return knownStart > 0 && discoveredStart > 0 && knownStart != discoveredStart;
    }

    @Override
    public void shutdown() {
    }
}
