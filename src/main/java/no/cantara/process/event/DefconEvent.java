package no.cantara.process.event;

import no.cantara.process.support.FingerprintStore;
import no.cantara.process.util.ListeningSocketSupport;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    private List<Integer> listeningPorts = Collections.emptyList();

    private int escalatedFromLevel;

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
     * @return the local TCP ports the suspicious process was listening on when graded,
     * empty when it was not listening or the information is unavailable on this platform
     */
    public List<Integer> getListeningPorts() {
        return listeningPorts;
    }

    void setListeningPorts(List<Integer> listeningPorts) {
        this.listeningPorts = listeningPorts;
    }

    /**
     * @return true if this grading was escalated from a previous grading, either because
     * the process holds a listening socket or because it remained alive (see
     * {@link #escalate(DefconEvent)})
     */
    public boolean isEscalated() {
        return escalatedFromLevel > 0;
    }

    public int getEscalatedFromLevel() {
        return escalatedFromLevel;
    }

    private void setEscalatedFromLevel(int escalatedFromLevel) {
        this.escalatedFromLevel = escalatedFromLevel;
    }

    /**
     * Grade an unknown (not fingerprinted, not whitelisted) process:
     * <ul>
     * <li>DEFCON1 - known probing tool running privileged</li>
     * <li>DEFCON2 - known probing tool, or an unknown process running privileged</li>
     * <li>DEFCON3 - known command but unknown fingerprint (same executable, new user)</li>
     * <li>DEFCON4 - unknown process under a regular user</li>
     * </ul>
     * A process holding a listening TCP socket (the "listening probe" use-case) is
     * escalated one level.
     */
    public static DefconEvent classify(ProcessDTO process, FingerprintStore store) {
        boolean privileged = PRIVILEGED_USERS.contains(process.getUser());
        boolean probeTool = isProbeTool(process);

        int level;
        if (probeTool && privileged) {
            level = 1;
        } else if (probeTool || privileged) {
            level = 2;
        } else if (store.isKnownCommand(process.getCommand())) {
            level = 3;
        } else {
            level = 4;
        }

        List<Integer> listeningPorts = ListeningSocketSupport.listeningPortsOf(process.getPid());
        DefconEvent defconEvent;
        if (!listeningPorts.isEmpty() && level > 1) {
            defconEvent = forLevel(level - 1, process);
            defconEvent.setEscalatedFromLevel(level);
        } else {
            defconEvent = forLevel(level, process);
        }
        defconEvent.setListeningPorts(listeningPorts);
        return defconEvent;
    }

    /**
     * Escalate a previous grading one level (used when a suspicious process remains alive
     * beyond the configured escalation delay). DEFCON1 cannot be escalated further.
     */
    public static DefconEvent escalate(DefconEvent original) {
        DefconEvent escalated = forLevel(Math.max(1, original.getLevel() - 1), original.getSuspiciousProcess());
        escalated.setEscalatedFromLevel(original.getLevel());
        escalated.setListeningPorts(new ArrayList<>(original.getListeningPorts()));
        return escalated;
    }

    public static DefconEvent forLevel(int level, ProcessDTO process) {
        switch (level) {
            case 1:
                return new DEFCON1(process);
            case 2:
                return new DEFCON2(process);
            case 3:
                return new DEFCON3(process);
            case 4:
                return new DEFCON4(process);
            default:
                return new DEFCON5(process);
        }
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
        StringBuilder buf = new StringBuilder();
        buf.append("DEFCON").append(getLevel()).append(" - ").append(getInformation());
        if (!listeningPorts.isEmpty()) {
            buf.append(" (listening on TCP ports ").append(listeningPorts).append(")");
        }
        if (isEscalated()) {
            buf.append(" (escalated from DEFCON").append(escalatedFromLevel).append(")");
        }
        return buf.toString();
    }

}
