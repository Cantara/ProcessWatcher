package no.cantara.process.support;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import no.cantara.process.event.ProcessWatchEvent;

import java.util.Collection;

/**
 * Tracks the last observed events per pid, mirroring PathWatcher's FileWorkerMap.
 * The producers diff live process snapshots against this map to synthesize
 * PROCESS_STARTED and PROCESS_TERMINATED events.
 */
public class ProcessWorkerMap {

    private final Multimap<Long, ProcessWatchEvent> map = ArrayListMultimap.create();

    public ProcessWorkerMap() {
    }

    public synchronized ImmutableList<Long> keySet() {
        return ImmutableList.copyOf(map.keySet());
    }

    public synchronized void add(Long pid, ProcessWatchEvent processWatchEvent) {
        map.put(pid, processWatchEvent);
    }

    public synchronized void remove(Long pid) {
        map.removeAll(pid);
    }

    public synchronized int size() {
        return map.keySet().size();
    }

    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized boolean contains(Long pid) {
        return map.containsKey(pid);
    }

    public synchronized boolean checkState(Long pid, ProcessWatchState state) {
        for (ProcessWatchEvent value : map.get(pid)) {
            if (state.equals(value.getProcessWatchState())) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean checkState(Long pid, ProcessWatchKey processWatchKey) {
        for (ProcessWatchEvent value : map.get(pid)) {
            if (processWatchKey.equals(value.getProcessWatchKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param pid the process id
     * @return the last observed event for the pid, or null if the pid is unknown
     */
    public synchronized ProcessWatchEvent getProcess(Long pid) {
        Collection<ProcessWatchEvent> entries = map.get(pid);
        ProcessWatchEvent last = null;
        for (ProcessWatchEvent value : entries) {
            last = value;
        }
        return last;
    }

    public synchronized Collection<ProcessWatchEvent> getProcessEntries(Long pid) {
        return map.get(pid);
    }

}
