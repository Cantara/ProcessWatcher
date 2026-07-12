package no.cantara.process.worker;

import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static no.cantara.process.util.FileSystemSupport.execCmd;

/**
 * Fallback process discovery by executing {@code ps}. Headers are suppressed and columns
 * requested explicitly ({@code -o pid=,user=,args=}) so parsing does not depend on the
 * platform's default {@code ps} column layout.
 */
public class ProcessPollEventsProducer extends AbstractProcessEventsProducer {

    public ProcessPollEventsProducer(BlockingQueue<ProcessWatchEvent> queue) {
        super(queue);
    }

    @Override
    public Map<Long, ProcessDTO> snapshotProcesses() throws Exception {
        Map<Long, ProcessDTO> discoveredProcesses = new HashMap<>();
        String processList = execCmd("ps", "-eo", "pid=,user=,args=");
        for (String line : processList.split("\n")) {
            ProcessDTO process = parseProcessLine(line);
            if (process != null) {
                discoveredProcesses.put(process.getPid(), process);
            }
        }
        return discoveredProcesses;
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
