package no.cantara.process.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects listening TCP sockets per process by parsing {@code /proc/net/tcp} and
 * {@code /proc/net/tcp6} and resolving socket inodes through {@code /proc/<pid>/fd}.
 * This supports the main README use-case: detecting "listening probes".
 * <p>
 * Only works on Linux, and inode resolution requires permission to read the target
 * process' fd table (own processes, or everything when running as root). On any
 * failure the methods degrade to "not listening" rather than throwing.
 */
public class ListeningSocketSupport {

    private static final Logger log = LoggerFactory.getLogger(ListeningSocketSupport.class);

    private static final String TCP_LISTEN_STATE = "0A";

    private static final Pattern SOCKET_INODE_PATTERN = Pattern.compile("socket:\\[(\\d+)]");

    /**
     * @return socket inode to local port for all TCP sockets in LISTEN state on this host
     */
    public static Map<String, Integer> listeningInodeToPort() {
        Map<String, Integer> inodeToPort = new HashMap<>();
        for (String procNetFile : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            Path path = Paths.get(procNetFile);
            if (!Files.isReadable(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    parseListeningSocketLine(line, inodeToPort);
                }
            } catch (IOException e) {
                log.trace("Unable to read {}: {}", procNetFile, e.toString());
            }
        }
        return inodeToPort;
    }

    /**
     * Parses a /proc/net/tcp line on the form
     * {@code "0: 0100007F:1F90 00000000:0000 0A <queues> <timers> <retrnsmt> <uid> <timeout> <inode> ..."}
     * and records inode to port for sockets in LISTEN state.
     */
    static void parseListeningSocketLine(String line, Map<String, Integer> inodeToPort) {
        String[] columns = line.trim().split("\\s+");
        if (columns.length < 10 || !TCP_LISTEN_STATE.equals(columns[3])) {
            return;
        }
        try {
            String[] localAddress = columns[1].split(":");
            int port = Integer.parseInt(localAddress[localAddress.length - 1], 16);
            inodeToPort.put(columns[9], port);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // not a socket table row (e.g. the header line) - skip
        }
    }

    /**
     * @param pid the process id
     * @return the socket inodes held by the process, or an empty set when the fd table
     * is not readable
     */
    public static Set<String> socketInodesOf(long pid) {
        Set<String> inodes = new HashSet<>();
        Path fdDir = Paths.get("/proc/" + pid + "/fd");
        try (DirectoryStream<Path> fds = Files.newDirectoryStream(fdDir)) {
            for (Path fd : fds) {
                try {
                    Matcher matcher = SOCKET_INODE_PATTERN.matcher(Files.readSymbolicLink(fd).toString());
                    if (matcher.matches()) {
                        inodes.add(matcher.group(1));
                    }
                } catch (IOException e) {
                    // fd may have been closed while iterating - skip
                }
            }
        } catch (IOException e) {
            log.trace("Unable to read fd table of pid {}: {}", pid, e.toString());
        }
        return inodes;
    }

    /**
     * @param pid the process id
     * @return the local TCP ports the process is listening on, empty when it is not
     * listening or the information is not available on this platform
     */
    public static List<Integer> listeningPortsOf(long pid) {
        List<Integer> ports = new ArrayList<>();
        if (!FileSystemSupport.isLinux()) {
            return ports;
        }
        Set<String> socketInodes = socketInodesOf(pid);
        if (socketInodes.isEmpty()) {
            return ports;
        }
        for (Map.Entry<String, Integer> entry : listeningInodeToPort().entrySet()) {
            if (socketInodes.contains(entry.getKey())) {
                ports.add(entry.getValue());
            }
        }
        ports.sort(Integer::compareTo);
        return ports;
    }

}
