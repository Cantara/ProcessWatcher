package no.cantara.process.support;

import no.cantara.process.event.ProcessDTO;
import org.testng.annotations.Test;

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
    public void testWhitelist() {
        FingerprintStore store = new FingerprintStore(0);
        store.addWhitelistPattern(".*logrotate.*");

        assertTrue(store.isWhitelisted(process("root", "/usr/sbin/logrotate", "/usr/sbin/logrotate /etc/logrotate.conf")));
        assertTrue(store.isWhitelisted(process("root", "/bin/sh", "/bin/sh -c /usr/sbin/logrotate")));
        assertFalse(store.isWhitelisted(process("root", "/usr/bin/nc", "/usr/bin/nc -l 4444")));
    }

}
