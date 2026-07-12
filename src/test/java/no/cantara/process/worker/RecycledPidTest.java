package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.support.ProcessWatchKey;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Drives the producer diff loop with scripted snapshots (SCAN_PROCESS_INTERVAL = -1 runs one
 * pass and returns) to verify pid+startTime identity: a pid reused by a different process
 * must be terminated and re-qualified rather than inheriting the old instance's known status.
 */
public class RecycledPidTest {

    private long previousInterval;

    private static class ScriptedProducer extends AbstractProcessEventsProducer {
        private Map<Long, ProcessDTO> snapshot;

        ScriptedProducer(BlockingQueue<ProcessWatchEvent> queue) {
            super(queue);
        }

        void setSnapshot(Map<Long, ProcessDTO> snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Map<Long, ProcessDTO> snapshotProcesses() {
            return snapshot;
        }
    }

    private ProcessDTO proc(long pid, String command, long startTime) {
        ProcessDTO dto = new ProcessDTO();
        dto.setPid(pid);
        dto.setUser("app");
        dto.setCommand(command);
        dto.setCommandLine(command);
        dto.setStartTimeEpochMs(startTime);
        return dto;
    }

    private Map<Long, ProcessDTO> snapshotOf(ProcessDTO... processes) {
        Map<Long, ProcessDTO> map = new HashMap<>();
        for (ProcessDTO process : processes) {
            map.put(process.getPid(), process);
        }
        return map;
    }

    @BeforeMethod
    public void setUp() {
        previousInterval = ProcessWatcher.SCAN_PROCESS_INTERVAL;
        ProcessWatcher.SCAN_PROCESS_INTERVAL = -1; // one pass per run()
        ProcessWatcher.getInstance().getProcessWorkerMap().clear();
    }

    @AfterMethod
    public void tearDown() {
        ProcessWatcher.SCAN_PROCESS_INTERVAL = previousInterval;
        ProcessWatcher.getInstance().getProcessWorkerMap().clear();
    }

    private List<ProcessWatchEvent> drain(BlockingQueue<ProcessWatchEvent> queue) {
        return queue.stream().collect(Collectors.toList());
    }

    @Test
    public void testRecycledPidIsTerminatedAndReQualified() {
        BlockingQueue<ProcessWatchEvent> queue = new ArrayBlockingQueue<>(100);
        ScriptedProducer producer = new ScriptedProducer(queue);

        // pass 1: pid 100 running the app, start time 1000
        ProcessDTO original = proc(100, "/usr/bin/java", 1000);
        producer.setSnapshot(snapshotOf(original));
        producer.run();

        List<ProcessWatchEvent> firstPass = drain(queue);
        assertEquals(firstPass.size(), 1);
        assertEquals(firstPass.get(0).getProcessWatchKey(), ProcessWatchKey.PROCESS_STARTED);
        assertTrue(ProcessWatcher.getInstance().getProcessWorkerMap().contains(100L));
        queue.clear();

        // pass 2: pid 100 now a different process (start time 2000) - recycled
        ProcessDTO recycled = proc(100, "/tmp/evil", 2000);
        producer.setSnapshot(snapshotOf(recycled));
        producer.run();

        List<ProcessWatchEvent> secondPass = drain(queue);
        assertEquals(secondPass.size(), 2, "Recycled pid must produce a TERMINATED and a STARTED");
        assertEquals(secondPass.get(0).getProcessWatchKey(), ProcessWatchKey.PROCESS_TERMINATED);
        assertEquals(secondPass.get(0).getProcess().getCommand(), "/usr/bin/java",
                "Terminated event must carry the stale instance");
        assertEquals(secondPass.get(1).getProcessWatchKey(), ProcessWatchKey.PROCESS_STARTED);
        assertEquals(secondPass.get(1).getProcess().getCommand(), "/tmp/evil",
                "Started event must carry the new instance for re-qualification");
    }

    @Test
    public void testSamePidSameStartTimeIsNotRecycled() {
        BlockingQueue<ProcessWatchEvent> queue = new ArrayBlockingQueue<>(100);
        ScriptedProducer producer = new ScriptedProducer(queue);

        ProcessDTO process = proc(100, "/usr/bin/java", 1000);
        producer.setSnapshot(snapshotOf(process));
        producer.run();
        queue.clear();

        // identical instance observed again - no new events
        producer.setSnapshot(snapshotOf(proc(100, "/usr/bin/java", 1000)));
        producer.run();
        assertTrue(drain(queue).isEmpty(), "An unchanged pid must not produce events");
    }

    @Test
    public void testUnknownStartTimesAreNotTreatedAsRecycled() {
        // ps fallback mode provides no start time (0) - must not thrash as recycled
        BlockingQueue<ProcessWatchEvent> queue = new ArrayBlockingQueue<>(100);
        ScriptedProducer producer = new ScriptedProducer(queue);

        producer.setSnapshot(snapshotOf(proc(100, "/usr/bin/java", 0)));
        producer.run();
        queue.clear();

        producer.setSnapshot(snapshotOf(proc(100, "/usr/bin/java", 0)));
        producer.run();
        assertTrue(drain(queue).isEmpty(), "Zero start times must not be read as a recycled pid");
    }
}
