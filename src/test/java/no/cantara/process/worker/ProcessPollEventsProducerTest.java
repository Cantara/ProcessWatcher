package no.cantara.process.worker;

import no.cantara.process.event.ProcessDTO;
import no.cantara.process.util.FileSystemSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ProcessPollEventsProducerTest {

    private final static Logger log = LoggerFactory.getLogger(ProcessPollEventsProducerTest.class);

    @Test
    public void testParseProcessLine() {
        ProcessDTO process = ProcessPollEventsProducer.parseProcessLine("  4242 root     /usr/bin/java -jar app.jar");
        assertEquals(process.getPid(), 4242);
        assertEquals(process.getUser(), "root");
        assertEquals(process.getCommand(), "/usr/bin/java");
        assertEquals(process.getCommandLine(), "/usr/bin/java -jar app.jar");
        assertEquals(process.getFingerprint(), "root|/usr/bin/java");

        assertNull(ProcessPollEventsProducer.parseProcessLine(""));
        assertNull(ProcessPollEventsProducer.parseProcessLine("   "));
        assertNull(ProcessPollEventsProducer.parseProcessLine("PID USER COMMAND")); // header safety
    }

    @Test
    public void testParseSnapshotDropsInjectedPhantomPid() {
        long ownPid = ProcessHandle.current().pid();
        // a hostile process embeds a newline + fake row in its argv; pid 999999 does not exist
        String psOutput = ""
                + "  " + ownPid + " app     /usr/bin/java -jar app.jar\n"
                + "999999 root    /usr/bin/nc -l 4444\n";

        Map<Long, ProcessDTO> snapshot = ProcessPollEventsProducer.parseSnapshot(psOutput);

        assertTrue(snapshot.containsKey(ownPid), "The real process must be kept");
        assertFalse(snapshot.containsKey(999999L), "The injected phantom pid must be dropped");
    }

    @Test
    public void testSnapshotProcessesWithPs() throws Exception {
        if (!FileSystemSupport.isLinux() && !FileSystemSupport.isMacOS()) {
            log.info("Skipping ps snapshot test on {}", FileSystemSupport.getOSString());
            return;
        }
        ProcessPollEventsProducer producer = new ProcessPollEventsProducer(new ArrayBlockingQueue<>(10));
        Map<Long, ProcessDTO> snapshot = producer.snapshotProcesses();
        assertFalse(snapshot.isEmpty(), "Expected at least one process from ps");
        ProcessDTO anyProcess = snapshot.values().iterator().next();
        assertTrue(anyProcess.getPid() > 0);
        assertFalse(anyProcess.getUser().isEmpty());
        assertFalse(anyProcess.getCommand().isEmpty());
        log.trace("Snapshot of {} processes, e.g: {}", snapshot.size(), anyProcess);
    }

    @Test
    public void testNativeSnapshotSeesOwnJvm() {
        ProcessNativeEventsProducer producer = new ProcessNativeEventsProducer(new ArrayBlockingQueue<>(10));
        Map<Long, ProcessDTO> snapshot = producer.snapshotProcesses();
        long ownPid = ProcessHandle.current().pid();
        assertTrue(snapshot.containsKey(ownPid), "Native snapshot should contain this JVM (pid " + ownPid + ")");
        assertFalse(snapshot.get(ownPid).getCommand().isEmpty(), "Own JVM command should be readable");
    }

}
