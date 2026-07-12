package no.cantara.process.event;

import no.cantara.process.support.FingerprintStore;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

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

}
