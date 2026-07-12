package no.cantara.process.worker;

import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static no.cantara.process.util.FileSystemSupport.execCmd;

/**
 * Fallback process discovery by executing {@code ps}. Headers are suppressed and columns
 * requested explicitly ({@code -o pid=,user=,args=}) so parsing does not depend on the
 * platform's default {@code ps} column layout.
 * <p>
 * {@code ps} does not sanitize newlines in a process' argv, so a hostile process could embed
 * a fake {@code "pid user args"} row in its own command line to inject a phantom process.
 * Each parsed pid is therefore reconciled against the OS via {@link ProcessHandle}: a pid that
 * is not a live process is dropped. This defeats phantom-pid injection (baseline poisoning);
 * it cannot detect a phantom row that reuses a genuinely live pid, which is why the native
 * scanner remains the default.
 */
public class ProcessPollEventsProducer extends AbstractProcessEventsProducer {

    private final static Logger log = LoggerFactory.getLogger(ProcessPollEventsProducer.class);

    public ProcessPollEventsProducer(BlockingQueue<ProcessWatchEvent> queue) {
        super(queue);
    }

    @Override
    public Map<Long, ProcessDTO> snapshotProcesses() throws Exception {
        return parseSnapshot(execCmd("ps", "-eo", "pid=,user=,args="));
    }

    /**
     * @param psOutput raw {@code ps -eo pid=,user=,args=} output
     * @return the live processes parsed from it, with phantom (non-existent) pids dropped
     */
    static Map<Long, ProcessDTO> parseSnapshot(String psOutput) {
        Map<Long, ProcessDTO> discoveredProcesses = new HashMap<>();
        for (String line : psOutput.split("\n")) {
            ProcessDTO process = parseProcessLine(line);
            if (process == null) {
                continue;
            }
            if (!isLiveProcess(process.getPid())) {
                log.warn("Dropping ps row for non-existent pid {} (possible output injection): {}",
                        process.getPid(), process.getCommandLine());
                continue;
            }
            discoveredProcesses.put(process.getPid(), process);
        }
        return discoveredProcesses;
    }

    private static boolean isLiveProcess(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /**
     * @param line a {@code ps} output line on the form {@code "  pid user args..."}
     * @return the parsed process, or null for blank or malformed lines
     */
    static ProcessDTO parseProcessLine(String line) {
        String[] columns = line.trim().split("\\s+", 3);
        if (columns.length < 3) {
            return null;
        }
        try {
            ProcessDTO process = new ProcessDTO();
            process.setPid(Long.parseLong(columns[0]));
            process.setUser(columns[1]);
            process.setCommandLine(columns[2]);
            process.setCommand(columns[2].split("\\s+", 2)[0]);
            return process;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
