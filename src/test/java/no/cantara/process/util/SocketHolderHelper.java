package no.cantara.process.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Test helper that spawns a python process holding a raw IP socket, an AF_PACKET capture
 * socket and a listening TCP socket - the socket footprint of a sniffing/probing intruder.
 * Requires Linux, python3 and CAP_NET_RAW (e.g. running as root); returns null when the
 * environment cannot provide that, so tests can skip gracefully.
 */
public class SocketHolderHelper {

    private static final Logger log = LoggerFactory.getLogger(SocketHolderHelper.class);

    private static final String SCRIPT =
            "import socket,time\n" +
            "try:\n" +
            "    r=socket.socket(socket.AF_INET,socket.SOCK_RAW,socket.IPPROTO_ICMP)\n" +
            "    p=socket.socket(socket.AF_PACKET,socket.SOCK_RAW,socket.htons(3))\n" +
            "    l=socket.socket(); l.bind(('127.0.0.1',0)); l.listen(1)\n" +
            "    print('ready',flush=True)\n" +
            "    time.sleep(15)\n" +
            "except Exception as e:\n" +
            "    print('failed: '+str(e),flush=True)\n";

    public static Process spawnSocketHolder() {
        if (!FileSystemSupport.isLinux()) {
            return null;
        }
        try {
            Process process = new ProcessBuilder("python3", "-c", SCRIPT).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (!"ready".equals(line)) {
                log.info("Socket holder helper unavailable in this environment: {}", line);
                process.destroy();
                return null;
            }
            return process;
        } catch (IOException e) {
            log.info("Unable to spawn python3 socket holder: {}", e.toString());
            return null;
        }
    }

}
