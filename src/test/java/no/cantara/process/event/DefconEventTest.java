package no.cantara.process.event;

import no.cantara.process.support.FingerprintStore;
import no.cantara.process.util.FileSystemSupport;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DefconEventTest {

    private ProcessDTO process(String user, String command) {
        ProcessDTO process = new ProcessDTO();
        process.setPid(4242);
        process.setUser(user);
        process.setCommand(command);
        process.setCommandLine(command);
        return process;
    }

    @Test
    public void testDefconClassification() {
        FingerprintStore store = new FingerprintStore(0);
        store.learn(process("app", "/usr/bin/java"));

        // probing tool running privileged
        assertEquals(DefconEvent.classify(process("root", "/usr/bin/nc"), store).getLevel(), 1);

        // probing tool under a regular user
        assertEquals(DefconEvent.classify(process("app", "/usr/bin/nmap"), store).getLevel(), 2);

        // unknown process running privileged
        assertEquals(DefconEvent.classify(process("root", "/opt/strange/daemon"), store).getLevel(), 2);

        // known command but new user
        assertEquals(DefconEvent.classify(process("nobody", "/usr/bin/java"), store).getLevel(), 3);

        // unknown process under a regular user
        assertEquals(DefconEvent.classify(process("app", "/opt/strange/daemon"), store).getLevel(), 4);
    }

    @Test
    public void testEscalation() {
        DefconEvent original = DefconEvent.forLevel(4, process("app", "/opt/strange/daemon"));

        DefconEvent escalated = DefconEvent.escalate(original);
        assertEquals(escalated.getLevel(), 3);
        assertTrue(escalated.isEscalated());
        assertEquals(escalated.getEscalatedFromLevel(), 4);
        assertEquals(escalated.getSuspiciousProcess(), original.getSuspiciousProcess());

        // DEFCON1 cannot be escalated further
        DefconEvent defcon1 = DefconEvent.escalate(DefconEvent.escalate(escalated));
        assertEquals(defcon1.getLevel(), 1);
        assertEquals(DefconEvent.escalate(defcon1).getLevel(), 1);
    }

    @Test
    public void testListeningProcessIsEscalated() throws Exception {
        if (!FileSystemSupport.isLinux()) {
            return;
        }
        FingerprintStore store = new FingerprintStore(0);

        // grade our own (listening) JVM as an unknown regular-user process
        ProcessDTO ownJvm = process("app", "/opt/strange/daemon");
        ownJvm.setPid(ProcessHandle.current().pid());

        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(0)) {
            DefconEvent graded = DefconEvent.classify(ownJvm, store);
            assertEquals(graded.getLevel(), 3, "A listening unknown process should be escalated one level");
            assertTrue(graded.isEscalated());
            assertEquals(graded.getEscalatedFromLevel(), 4);
            assertTrue(graded.getListeningPorts().contains(serverSocket.getLocalPort()));
        }
    }

}
