package no.cantara.process.event;

import no.cantara.process.support.ProcessWatchKey;
import no.cantara.process.support.ProcessWatchState;

/**
 * The event delivered to registered handlers, mirroring PathWatcher's FileWatchEvent.
 */
public class ProcessWatchEvent {

    private final ProcessDTO process;
    private final ProcessWatchKey processWatchKey;
    private final ProcessWatchState processWatchState;
    private final DefconEvent defconEvent;
    private final long timestamp;

    public ProcessWatchEvent(ProcessDTO process, ProcessWatchKey processWatchKey, ProcessWatchState processWatchState) {
        this(process, processWatchKey, processWatchState, null);
    }

    public ProcessWatchEvent(ProcessDTO process, ProcessWatchKey processWatchKey, ProcessWatchState processWatchState, DefconEvent defconEvent) {
        this.process = process;
        this.processWatchKey = processWatchKey;
        this.processWatchState = processWatchState;
        this.defconEvent = defconEvent;
        this.timestamp = System.currentTimeMillis();
    }

    public ProcessDTO getProcess() {
        return process;
    }

    public ProcessWatchKey getProcessWatchKey() {
        return processWatchKey;
    }

    public ProcessWatchState getProcessWatchState() {
        return processWatchState;
    }

    /**
     * @return the naive threat-level grading, only present for PROCESS_SUSPICIOUS events
     */
    public DefconEvent getDefconEvent() {
        return defconEvent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("process: {").append(process).append("}");
        buf.append(", processWatchKey: ").append(processWatchKey.name());
        buf.append(", processWatchState: ").append(processWatchState.name());
        if (defconEvent != null) {
            buf.append(", defcon: ").append(defconEvent);
        }
        return buf.toString();
    }

}
