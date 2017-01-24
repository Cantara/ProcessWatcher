package no.cantara.process.watcher;

import no.cantara.process.FileSystemSupport;
import no.cantara.process.watcher.event.ProcessDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static no.cantara.process.FileSystemSupport.execCmd;


public class ProcessWatcherTest {

    private final static Logger log = LoggerFactory.getLogger(ProcessWatcherTest.class);

    @Test()
    public void testSystemExecFallback() throws Exception {
        log.trace("IsLinux: {}, isLinuxFileSystem: {}, isMacOS: {}, isMacOSFileSystem: {}",
                FileSystemSupport.isLinux(),
                FileSystemSupport.isLinuxFileSystem(),
                FileSystemSupport.isMacOS(),
                FileSystemSupport.isMacOSFileSystem());
        ;
        log.trace("hasJDKEventDrivenFileSystem: {}", FileSystemSupport.hasJDKEventDrivenFileSystem());

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
        }

        log.trace(processList);

    }


}
