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

    private boolean rawSocket;

    private boolean packetSocket;

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
     * @return true if the suspicious process held a raw IP socket when graded
     * (custom scanners, covert channels)
     */
    public boolean hasRawSocket() {
        return rawSocket;
    }

    /**
     * @return true if the suspicious process held an AF_PACKET (packet capture) socket
     * when graded (sniffers such as tcpdump and libpcap based tools)
     */
    public boolean hasPacketSocket() {
        return packetSocket;
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
     * Two socket signals each escalate the grading one level (floored at DEFCON1):
     * a listening TCP socket (the "listening probe" use-case), and a raw IP or
     * AF_PACKET capture socket (scanners and sniffers).
     */
    public static DefconEvent classify(ProcessDTO process, FingerprintStore store) {
        List<Integer> listeningPorts = ListeningSocketSupport.listeningPortsOf(process.getPid());
        boolean rawSocket = ListeningSocketSupport.hasRawSocket(process.getPid());
        boolean packetSocket = ListeningSocketSupport.hasPacketSocket(process.getPid());
        boolean hasSocketSignal = !listeningPorts.isEmpty() || rawSocket || packetSocket;

        boolean privileged = isPrivileged(process.getUser(), hasSocketSignal);
        boolean probeTool = isProbeTool(process);

        int baseLevel;
        if (probeTool && privileged) {
            baseLevel = 1;
        } else if (probeTool || privileged) {
            baseLevel = 2;
        } else if (store.isKnownCommand(process.getCommand())) {
            baseLevel = 3;
        } else {
            baseLevel = 4;
        }

        int escalations = 0;
        if (!listeningPorts.isEmpty()) {
            escalations++;
        }
        if (rawSocket || packetSocket) {
            escalations++;
        }

        int level = Math.max(1, baseLevel - escalations);
        DefconEvent defconEvent = forLevel(level, process);
        if (level != baseLevel) {
            defconEvent.setEscalatedFromLevel(baseLevel);
        }
        defconEvent.setListeningPorts(listeningPorts);
        defconEvent.rawSocket = rawSocket;
        defconEvent.packetSocket = packetSocket;
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
        escalated.rawSocket = original.rawSocket;
        escalated.packetSocket = original.packetSocket;
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

    /**
     * Naive privilege heuristic. A process is treated as privileged when it runs as root
     * (by name or uid 0), or when its owning user could not be resolved but it holds a
     * network socket - a raw or AF_PACKET socket in particular implies CAP_NET_RAW, which is
     * a privileged capability even without uid 0. This is deliberately a coarse indication.
     * @param user the owning user (name or numeric uid), possibly empty when unresolved
     * @param hasSocketSignal whether the process holds a listening, raw or packet socket
     * @return true if the process should be graded as privileged
     */
    static boolean isPrivileged(String user, boolean hasSocketSignal) {
        return PRIVILEGED_USERS.contains(user) || (user.isEmpty() && hasSocketSignal);
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
        if (rawSocket) {
            buf.append(" (holding a raw IP socket)");
        }
        if (packetSocket) {
            buf.append(" (holding an AF_PACKET capture socket)");
        }
        if (isEscalated()) {
            buf.append(" (escalated from DEFCON").append(escalatedFromLevel).append(")");
        }
        return buf.toString();
    }

}
