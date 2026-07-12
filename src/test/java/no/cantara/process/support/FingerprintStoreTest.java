package no.cantara.process.support;

import no.cantara.process.event.ProcessDTO;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class FingerprintStoreTest {

    private ProcessDTO process(String user, String command, String commandLine) {
        ProcessDTO process = new ProcessDTO();
        process.setPid(4242);
        process.setUser(user);
        process.setCommand(command);
        process.setCommandLine(commandLine);
        return process;
    }

    @Test
    public void testLearningWindowExpires() throws Exception {
        FingerprintStore store = new FingerprintStore(200);
        assertTrue(store.isLearning());
        Thread.sleep(300);
        assertFalse(store.isLearning());
    }

    @Test
    public void testLearnForever() {
        FingerprintStore store = new FingerprintStore(FingerprintStore.LEARN_FOREVER);
        assertTrue(store.isLearning());
    }

    @Test
    public void testZeroPeriodMeansNoLearning() {
        FingerprintStore store = new FingerprintStore(0);
        assertFalse(store.isLearning());
    }

    @Test
    public void testLearnAndRecognizeFingerprint() {
        FingerprintStore store = new FingerprintStore(0);
        ProcessDTO learned = process("app", "/usr/bin/java", "/usr/bin/java -jar app.jar");

        assertFalse(store.isKnown(learned));
        store.learn(learned);
        assertTrue(store.isKnown(learned));
        assertTrue(store.isKnownCommand("/usr/bin/java"));

        // same command line but a different user is a different fingerprint
        ProcessDTO otherUser = process("root", "/usr/bin/java", "/usr/bin/java -jar app.jar");
        assertFalse(store.isKnown(otherUser));

        // varying arguments do not change the fingerprint
        ProcessDTO otherArgs = process("app", "/usr/bin/java", "/usr/bin/java -jar other.jar");
        assertTrue(store.isKnown(otherArgs));
    }

    @Test
    public void testBaselineSaveAndLoadRoundTrip() throws Exception {
        java.nio.file.Path baselineFile = java.nio.file.Paths.get("target", "fingerprint-baseline-test.txt");
        java.nio.file.Files.deleteIfExists(baselineFile);

        FingerprintStore store = new FingerprintStore(0);
        store.learn(process("app", "/usr/bin/java", "/usr/bin/java -jar app.jar"));
        store.learn(process("root", "/usr/sbin/sshd", "/usr/sbin/sshd -D"));
        store.saveBaseline(baselineFile);

        FingerprintStore restored = FingerprintStore.loadBaseline(baselineFile);
        assertFalse(restored.isLearning(), "A restored baseline must not need a new learning period");
        assertEquals(restored.size(), 2);
        assertTrue(restored.isKnown(process("app", "/usr/bin/java", "/usr/bin/java -jar app.jar")));
        assertTrue(restored.isKnown(process("root", "/usr/sbin/sshd", "/usr/sbin/sshd -D")));
        assertTrue(restored.isKnownCommand("/usr/bin/java"));
        assertFalse(restored.isKnown(process("evil", "/usr/bin/java", "/usr/bin/java -jar app.jar")));
    }

    @Test
    public void testSaveBaselineOnceOnlyAfterLearning() throws Exception {
        java.nio.file.Path baselineFile = java.nio.file.Paths.get("target", "fingerprint-baseline-once-test.txt");
        java.nio.file.Files.deleteIfExists(baselineFile);

        FingerprintStore learningStore = new FingerprintStore(60 * 1000);
        learningStore.setBaselineFile(baselineFile);
        learningStore.learn(process("app", "/usr/bin/java", "/usr/bin/java"));
        learningStore.saveBaselineOnce();
        assertFalse(java.nio.file.Files.exists(baselineFile), "Baseline must not be saved while still learning");

        FingerprintStore completedStore = new FingerprintStore(0);
        completedStore.setBaselineFile(baselineFile);
        completedStore.learn(process("app", "/usr/bin/java", "/usr/bin/java"));
        completedStore.saveBaselineOnce();
        assertTrue(java.nio.file.Files.exists(baselineFile), "Baseline must be saved once learning has ended");
    }

    @Test
    public void testInterpreterFingerprintIncludesCommandLine() {
        FingerprintStore store = new FingerprintStore(0);

        // a python interpreter learned running one script
        ProcessDTO learned = process("app", "/usr/bin/python3", "/usr/bin/python3 /opt/app/server.py");
        store.learn(learned);
        assertTrue(store.isKnown(learned));

        // same interpreter and user, but a different script/args is NOT known (LOTL evasion closed)
        ProcessDTO reverseShell = process("app", "/usr/bin/python3", "/usr/bin/python3 -c import socket,os");
        assertFalse(store.isKnown(reverseShell),
                "An interpreter running an unseen command line must not match the baseline");

        // versioned interpreter names normalize (python3.11 -> python)
        assertTrue(store.isInterpreter("/usr/bin/python3.11"));
        assertTrue(store.isInterpreter("perl5.36"));
        assertTrue(store.isInterpreter("/bin/bash"));

        // a non-interpreter binary still fingerprints on the executable path, ignoring args
        ProcessDTO javaLearned = process("app", "/usr/bin/java", "/usr/bin/java -jar app.jar");
        store.learn(javaLearned);
        assertFalse(store.isInterpreter("/usr/bin/java"));
        assertTrue(store.isKnown(process("app", "/usr/bin/java", "/usr/bin/java -jar other.jar")));
    }

    @Test
    public void testCustomInterpreterRegistration() {
        FingerprintStore store = new FingerprintStore(0);
        assertFalse(store.isInterpreter("/usr/local/bin/elixir"));
        store.addInterpreter("elixir");
        assertTrue(store.isInterpreter("/usr/local/bin/elixir"));
    }

    @Test
    public void testInterpreterBaselineRoundTrip() throws Exception {
        java.nio.file.Path baselineFile = java.nio.file.Paths.get("target", "fingerprint-interpreter-test.txt");
        java.nio.file.Files.deleteIfExists(baselineFile);

        FingerprintStore store = new FingerprintStore(0);
        ProcessDTO learned = process("app", "/usr/bin/python3", "/usr/bin/python3 /opt/app/server.py");
        store.learn(learned);
        store.saveBaseline(baselineFile);

        FingerprintStore restored = FingerprintStore.loadBaseline(baselineFile);
        assertTrue(restored.isKnown(learned));
        assertFalse(restored.isKnown(process("app", "/usr/bin/python3", "/usr/bin/python3 -c evil")));
    }

    @Test
    public void testWhitelist() {
        FingerprintStore store = new FingerprintStore(0);
        store.addWhitelistPattern(".*logrotate.*");

        assertTrue(store.isWhitelisted(process("root", "/usr/sbin/logrotate", "/usr/sbin/logrotate /etc/logrotate.conf")));
        assertTrue(store.isWhitelisted(process("root", "/bin/sh", "/bin/sh -c /usr/sbin/logrotate")));
        assertFalse(store.isWhitelisted(process("root", "/usr/bin/nc", "/usr/bin/nc -l 4444")));
    }

}
