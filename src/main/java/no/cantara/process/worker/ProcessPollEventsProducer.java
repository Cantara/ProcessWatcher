package no.cantara.process.worker;

import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.internal.ProcessWatchEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

import static no.cantara.process.util.FileSystemSupport.execCmd;

public class ProcessPollEventsProducer implements ProcessEventsProducer {
    private final static Logger log = LoggerFactory.getLogger(ProcessPollEventsProducer.class);

    private final BlockingQueue<ProcessWatchEvent> queue;


    public ProcessPollEventsProducer(BlockingQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            String processList = execCmd("ps -ef");
            String[] processArray = processList.split("\n");

            String[] headeritems = processArray[0].split(" ");
            int userColumn = 0;
            int pidColumn = 0;
            int cmdColumn = 0;
            int timeColumn = 0;
            int n = 0;
            for (String headerItem : headeritems) {
                switch (headerItem.trim()) {
                    case "UID":
                        userColumn = n;
                        break;
                    case "PID":
                        pidColumn = n;
                        break;
                    case "CMD":
                        cmdColumn = n;
                        break;
                    case "TIME":
                        timeColumn = n;
                    default:
                        break;
                }
                n++;
            }


            ProcessDTO observedProcess = new ProcessDTO();
            for (String processItem : processArray) {
                String[] observedLine = processItem.split(" ");
                int m = 0;
                for (String observedItem : observedLine) {
                    if (m == userColumn) {
                        observedProcess.setUid(observedItem);
                    }
                    if (m == pidColumn) {
                        observedProcess.setPid(observedItem);

                    }
                    if (m == cmdColumn) {
                        observedProcess.setCmd(observedItem);

                    }
                    if (m == timeColumn) {
                        observedProcess.setTime(observedItem);

                    }
                    m++;
                }
                queue.put(new ProcessWatchEvent(observedProcess));
            }

        } catch (Exception e) {
            log.error("Unable to use fallback process exec for process monitoring");
        }
    }

    @Override
    public void shutdown() {
    }
}
