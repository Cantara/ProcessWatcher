package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.support.ProcessWatchScanner;
import no.cantara.process.support.ProcessWatchState;
import no.cantara.process.util.FileSystemSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ProcessWatcherTest {

    private final static Logger log = LoggerFactory.getLogger(ProcessWatcherTest.class);

    /**
     * Copies the sleep binary to a unique path so the spawned canary process is guaranteed
     * to have a fingerprint that was not learned during the fingerprinting period.
     */
    private Path createCanaryBinary(String name) throws IOException {
        Path sleepBinary = Paths.get("/usr/bin/sleep");
        if (!Files.exists(sleepBinary)) {
            sleepBinary = Paths.get("/bin/sleep");
        }
        Path targetDir = Paths.get("target");
        Files.createDirectories(targetDir);
        Path canary = targetDir.resolve(name).toAbsolutePath();
        Files.copy(sleepBinary, canary, StandardCopyOption.REPLACE_EXISTING);
        canary.toFile().setExecutable(true);
        return canary;
    }

    @Test
    public void testSuspiciousProcessDetection() throws Exception {
        Path canary = createCanaryBinary("pw-canary-suspicious");

        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.setProcessScanInterval(200);
        pw.setFingerprintingPeriod(500);

        CountDownLatch suspiciousLatch = new CountDownLatch(1);
        CountDownLatch terminatedLatch = new CountDownLatch(1);
        AtomicReference<ProcessWatchEvent> suspiciousEvent = new AtomicReference<>();

        pw.registerSuspiciousProcessHandler(event -> {
            log.trace("Suspicious: {}", event);
            if (event.getProcess().getCommand().contains("pw-canary-suspicious")) {
                suspiciousEvent.set(event);
                suspiciousLatch.countDown();
            }
        });
        pw.registerProcessTerminatedHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-suspicious")) {
                terminatedLatch.countDown();
            }
        });

        pw.start();
        assertTrue(pw.isRunning());

        // let the fingerprinting period expire (all pre-existing processes are learned)
        Thread.sleep(1200);

        Process canaryProcess = new ProcessBuilder(canary.toString(), "5").start();
        try {
            assertTrue(suspiciousLatch.await(20, TimeUnit.SECONDS),
                    "Expected a suspicious process event for the canary");

            ProcessWatchEvent event = suspiciousEvent.get();
            assertEquals(event.getProcessWatchState(), ProcessWatchState.SUSPICIOUS);
            assertNotNull(event.getDefconEvent(), "A suspicious event must carry a defcon grading");
            int level = event.getDefconEvent().getLevel();
            assertTrue(level >= 1 && level <= 4, "Unexpected defcon level: " + level);
            log.info("Canary was graded: {}", event.getDefconEvent());
        } finally {
            canaryProcess.destroy();
        }

        assertTrue(terminatedLatch.await(20, TimeUnit.SECONDS),
                "Expected a terminated process event for the canary");

        pw.stop();
        assertFalse(pw.isRunning());
    }

    @Test(dependsOnMethods = "testSuspiciousProcessDetection")
    public void testWhitelistedProcessIsNotSuspicious() throws Exception {
        Path canary = createCanaryBinary("pw-canary-whitelisted");

        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.setProcessScanInterval(200);
        pw.setFingerprintingPeriod(500);
        pw.whitelist(".*pw-canary-whitelisted.*");

        CountDownLatch whitelistedLatch = new CountDownLatch(1);
        ConcurrentLinkedQueue<ProcessWatchEvent> suspiciousCanaryEvents = new ConcurrentLinkedQueue<>();

        pw.registerProcessStartedHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-whitelisted")
                    && ProcessWatchState.WHITELISTED.equals(event.getProcessWatchState())) {
                whitelistedLatch.countDown();
            }
        });
        pw.registerSuspiciousProcessHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-whitelisted")) {
                suspiciousCanaryEvents.add(event);
            }
        });

        pw.start();
        assertTrue(pw.isRunning());

        Thread.sleep(1200);

        Process canaryProcess = new ProcessBuilder(canary.toString(), "5").start();
        try {
            assertTrue(whitelistedLatch.await(20, TimeUnit.SECONDS),
                    "Expected a started event with WHITELISTED state for the canary");
            assertTrue(suspiciousCanaryEvents.isEmpty(),
                    "A whitelisted process must not produce suspicious events: " + suspiciousCanaryEvents);
        } finally {
            canaryProcess.destroy();
        }

        pw.stop();
    }

    @Test(dependsOnMethods = "testWhitelistedProcessIsNotSuspicious")
    public void testLongLivedSuspiciousProcessIsEscalated() throws Exception {
        Path canary = createCanaryBinary("pw-canary-escalation");

        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.setProcessScanInterval(200);
        pw.setFingerprintingPeriod(500);
        pw.setSuspiciousEscalationDelay(800);

        CountDownLatch escalationLatch = new CountDownLatch(2);
        ConcurrentLinkedQueue<ProcessWatchEvent> suspiciousEvents = new ConcurrentLinkedQueue<>();

        pw.registerSuspiciousProcessHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-escalation")) {
                suspiciousEvents.add(event);
                escalationLatch.countDown();
            }
        });

        pw.start();
        Thread.sleep(1200);

        Process canaryProcess = new ProcessBuilder(canary.toString(), "15").start();
        try {
            assertTrue(escalationLatch.await(20, TimeUnit.SECONDS),
                    "Expected both the initial and the escalated suspicious event");

            ProcessWatchEvent[] events = suspiciousEvents.toArray(new ProcessWatchEvent[0]);
            assertFalse(events[0].getDefconEvent().isEscalated(), "First grading must not be escalated");
            assertTrue(events[1].getDefconEvent().isEscalated(), "Second grading must be escalated");
            assertEquals(events[1].getDefconEvent().getLevel(), events[0].getDefconEvent().getLevel() - 1,
                    "Escalation must raise the threat exactly one level");
            log.info("Canary was escalated: {} -> {}", events[0].getDefconEvent(), events[1].getDefconEvent());
        } finally {
            canaryProcess.destroy();
        }

        pw.stop();
        pw.setSuspiciousEscalationDelay(60 * 1000);
    }

    @Test(dependsOnMethods = "testLongLivedSuspiciousProcessIsEscalated")
    public void testBaselineFileMakesRestartedWatcherArmed() throws Exception {
        Path canary = createCanaryBinary("pw-canary-baseline");
        Path baselineFile = Paths.get("target", "pw-baseline-integration.txt").toAbsolutePath();
        Files.deleteIfExists(baselineFile);

        // simulate a completed earlier run: a baseline containing only the canary fingerprint
        String user = System.getProperty("user.name");
        Files.write(baselineFile, java.util.Arrays.asList("F\t" + user + "|" + canary, "C\t" + canary));

        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.setProcessScanInterval(200);
        pw.setFingerprintingPeriod(60 * 1000); // must be ignored when the baseline is restored
        pw.setSuspiciousEscalationDelay(-1);
        pw.setFingerprintBaselineFile(baselineFile);

        CountDownLatch knownLatch = new CountDownLatch(1);
        ConcurrentLinkedQueue<ProcessWatchEvent> suspiciousCanaryEvents = new ConcurrentLinkedQueue<>();

        pw.registerProcessStartedHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-baseline")
                    && ProcessWatchState.KNOWN.equals(event.getProcessWatchState())) {
                knownLatch.countDown();
            }
        });
        pw.registerSuspiciousProcessHandler(event -> {
            if (event.getProcess().getCommand().contains("pw-canary-baseline")) {
                suspiciousCanaryEvents.add(event);
            }
        });

        pw.start();
        assertFalse(pw.getFingerprintStore().isLearning(),
                "A watcher started with an existing baseline file must be armed immediately");

        Process canaryProcess = new ProcessBuilder(canary.toString(), "5").start();
        try {
            assertTrue(knownLatch.await(20, TimeUnit.SECONDS),
                    "Expected the canary to be KNOWN through the restored baseline");
            assertTrue(suspiciousCanaryEvents.isEmpty(),
                    "A baselined process must not produce suspicious events: " + suspiciousCanaryEvents);
        } finally {
            canaryProcess.destroy();
        }

        pw.stop();
        pw.setSuspiciousEscalationDelay(60 * 1000);
    }

    @Test(dependsOnMethods = "testBaselineFileMakesRestartedWatcherArmed")
    public void testPollEventsScannerMode() throws Exception {
        if (!FileSystemSupport.isLinux() && !FileSystemSupport.isMacOS()) {
            log.info("Skipping ps based scanner test on {}", FileSystemSupport.getOSString());
            return;
        }

        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.forceProcessScannerMode(ProcessWatchScanner.POLL_PROCESS_EXEC);
        assertTrue(pw.isPollEvents());
        pw.setProcessScanInterval(200);
        pw.setFingerprintingPeriod(60 * 1000);

        CountDownLatch startedLatch = new CountDownLatch(1);
        pw.registerProcessStartedHandler(event -> {
            if (ProcessWatchState.LEARNING.equals(event.getProcessWatchState())) {
                startedLatch.countDown();
            }
        });

        pw.start();
        assertTrue(pw.isRunning());

        assertTrue(startedLatch.await(20, TimeUnit.SECONDS),
                "Expected started events with LEARNING state from the ps based scanner");
        assertTrue(pw.getFingerprintStore().size() > 0, "Expected fingerprints to be learned");

        pw.stop();
        pw.forceProcessScannerMode(ProcessWatchScanner.NATIVE_PROCESS_API);
    }

}
