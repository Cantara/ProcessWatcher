package no.cantara.process.event;

import no.cantara.process.support.FingerprintStore;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A naive DEFCON threat-level mapping of a suspicious process observation. As stated in the
 * README this categorization is only a simple indication and should not be trusted as an
 * authoritative severity in application reactions.
 * <p>
 * Level 1 is the most severe, level 5 means normal readiness.
 */
public abstract class DefconEvent {

    /**
     * Commonly abused network probing/listening tools, used as a naive escalation heuristic
     * for the main use-case of detecting listening probes.
     */
    private static final Set<String> PROBE_TOOLS = new HashSet<>(Arrays.asList(
            "nc", "ncat", "netcat", "socat", "nmap", "masscan", "tcpdump", "ngrep"
    ));

    private static final Set<String> PRIVILEGED_USERS = new HashSet<>(Arrays.asList(
            "root", "0"
    ));

    protected final ProcessDTO suspiciousProcess;

    protected DefconEvent(ProcessDTO process) {
        this.suspiciousProcess = process;
    }

    /**
     * @return DEFCON level between 1 (most severe) and 5 (normal readiness)
     */
    public abstract int getLevel();

    public String getInformation() {
        return "No specific defcon information available";
    }

    public ProcessDTO getSuspiciousProcess() {
        return suspiciousProcess;
    }

    /**
     * Grade an unknown (not fingerprinted, not whitelisted) process:
     * <ul>
     * <li>DEFCON1 - known probing tool running privileged</li>
     * <li>DEFCON2 - known probing tool, or an unknown process running privileged</li>
     * <li>DEFCON3 - known command but unknown fingerprint (same executable, new user)</li>
     * <li>DEFCON4 - unknown process under a regular user</li>
     * </ul>
     */
    public static DefconEvent classify(ProcessDTO process, FingerprintStore store) {
        boolean privileged = PRIVILEGED_USERS.contains(process.getUser());
        boolean probeTool = isProbeTool(process);
        if (probeTool && privileged) {
            return new DEFCON1(process);
        }
        if (probeTool || privileged) {
            return new DEFCON2(process);
        }
        if (store.isKnownCommand(process.getCommand())) {
            return new DEFCON3(process);
        }
        return new DEFCON4(process);
    }

    private static boolean isProbeTool(ProcessDTO process) {
        if (process.getCommand().isEmpty()) {
            return false;
        }
        String baseName = Paths.get(process.getCommand()).getFileName().toString();
        return PROBE_TOOLS.contains(baseName);
    }

    @Override
    public String toString() {
        return "DEFCON" + getLevel() + " - " + getInformation();
    }

}
