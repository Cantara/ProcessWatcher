package no.cantara.process.worker;

import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Process discovery through the JDK {@link ProcessHandle} API (Java 9+). This is the
 * "native" scanner mode - no external commands and no native libraries required.
 */
public class ProcessNativeEventsProducer extends AbstractProcessEventsProducer {

    public ProcessNativeEventsProducer(BlockingQueue<ProcessWatchEvent> queue) {
        super(queue);
    }

    @Override
    public Map<Long, ProcessDTO> snapshotProcesses() {
        Map<Long, ProcessDTO> discoveredProcesses = new HashMap<>();
        ProcessHandle.allProcesses().forEach(processHandle -> {
            try {
                ProcessHandle.Info info = processHandle.info();
                ProcessDTO process = new ProcessDTO();
                process.setPid(processHandle.pid());
                process.setUser(info.user().orElse(""));
                process.setCommand(info.command().orElse(""));
                process.setCommandLine(info.commandLine().orElse(info.command().orElse("")));
                info.startInstant().ifPresent(instant -> process.setStartTimeEpochMs(instant.toEpochMilli()));
                discoveredProcesses.put(processHandle.pid(), process);
            } catch (RuntimeException e) {
                // the process may have vanished while being inspected - skip it
            }
        });
        return discoveredProcesses;
    }
}
