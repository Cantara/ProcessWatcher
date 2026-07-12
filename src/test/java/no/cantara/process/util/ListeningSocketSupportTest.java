package no.cantara.process.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ListeningSocketSupportTest {

    private final static Logger log = LoggerFactory.getLogger(ListeningSocketSupportTest.class);

    @Test
    public void testParseListeningSocketLine() {
        Map<String, Integer> inodeToPort = new HashMap<>();

        // header line is ignored
        ListeningSocketSupport.parseListeningSocketLine(
                "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode", inodeToPort);
        assertTrue(inodeToPort.isEmpty());

        // 0100007F:1F90 = 127.0.0.1:8080 in LISTEN (0A) state with inode 424242
        ListeningSocketSupport.parseListeningSocketLine(
                "   0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 424242 1 0000000000000000 100 0 0 10 0", inodeToPort);
        assertEquals(inodeToPort.size(), 1);
        assertEquals(inodeToPort.get("424242"), Integer.valueOf(8080));

        // an ESTABLISHED (01) connection is not a listener
        ListeningSocketSupport.parseListeningSocketLine(
                "   1: 0100007F:1F90 0100007F:C350 01 00000000:00000000 00:00000000 00000000     0        0 424243 1 0000000000000000 100 0 0 10 0", inodeToPort);
        assertEquals(inodeToPort.size(), 1);
    }

    @Test
    public void testForeignSocketInspectionCapabilityProbe() {
        boolean capable = ListeningSocketSupport.canInspectForeignProcessSockets();
        log.info("canInspectForeignProcessSockets = {}", capable);

        if (!FileSystemSupport.isLinux()) {
            assertFalse(capable, "Capability must be false on non-Linux platforms");
            return;
        }
        // the probe must agree with the ground truth of whether pid 1's fd table is readable
        boolean pid1Readable;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> s =
                     java.nio.file.Files.newDirectoryStream(java.nio.file.Paths.get("/proc/1/fd"))) {
            s.iterator();
            pid1Readable = true;
        } catch (Exception e) {
            pid1Readable = false;
        }
        if (pid1Readable) {
            assertTrue(capable, "Probe must report capable when a root-owned process' fd table is readable");
        }
    }

    @Test
    public void testDetectRawAndPacketSockets() throws Exception {
        Process socketHolder = SocketHolderHelper.spawnSocketHolder();
        if (socketHolder == null) {
            log.info("Skipping raw/packet socket test - helper unavailable on {}", FileSystemSupport.getOSString());
            return;
        }
        try {
            long helperPid = socketHolder.pid();
            assertTrue(ListeningSocketSupport.hasRawSocket(helperPid),
                    "Expected the helper to be detected holding a raw IP socket");
            assertTrue(ListeningSocketSupport.hasPacketSocket(helperPid),
                    "Expected the helper to be detected holding an AF_PACKET socket");

            long ownPid = ProcessHandle.current().pid();
            assertFalse(ListeningSocketSupport.hasRawSocket(ownPid),
                    "The test JVM should not hold a raw IP socket");
            assertFalse(ListeningSocketSupport.hasPacketSocket(ownPid),
                    "The test JVM should not hold an AF_PACKET socket");
        } finally {
            socketHolder.destroy();
        }
    }

    @Test
    public void testDetectOwnListeningSocket() throws Exception {
        if (!FileSystemSupport.isLinux()) {
            log.info("Skipping /proc based listener test on {}", FileSystemSupport.getOSString());
            return;
        }
        long ownPid = ProcessHandle.current().pid();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            List<Integer> ports = ListeningSocketSupport.listeningPortsOf(ownPid);
            log.trace("Own JVM (pid {}) is listening on: {}", ownPid, ports);
            assertTrue(ports.contains(port),
                    "Expected own listening port " + port + " to be detected, got: " + ports);
        }
    }

}
