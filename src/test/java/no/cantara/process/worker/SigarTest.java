package no.cantara.process.worker;

import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.ProcCred;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by oranheim on 24/01/2017.
 */
public class SigarTest {

    private static final Logger log = LoggerFactory.getLogger(SigarTest.class);

    private long getCurrentUid() {
        try {
            String userName = System.getProperty("user.name");
            String command = "id -u "+userName;
            Process child = Runtime.getRuntime().exec(command);

            // Get the input stream and read from it
            InputStream in = child.getInputStream();
            java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
            String val = "";
            if (s.hasNext()) {
                val = s.next();
            } else {
                val = "";
            }
            return Long.valueOf(val.replaceAll("\n", ""));

        } catch (IOException e) {
        }
        return -1;
    }

    @Test
    public void testSigar() throws Exception {
        long currentUid = getCurrentUid();
        log.trace("Current UID: {}", currentUid);
        long[] procList = null;
        Sigar sigar = null;
        try {
            sigar = new Sigar();
            procList = sigar.getProcList();
        } catch (UnsatisfiedLinkError e) {
            log.warn("Unable to use sigar native API", e);
            return;
        }
        for(long pid : procList) {
            try {
                ProcCred cred = sigar.getProcCred(pid);
                if (currentUid != cred.getUid()) {
                    log.trace("pid: {} - skipped - not owned by you", pid);
                    continue;
                }
                log.trace("pid: {}", pid);

                ProcCpu cpu = sigar.getProcCpu(pid);
                log.trace("---> cpu: {}", cpu.toString());
                String[] args = sigar.getProcArgs(pid);
                StringBuffer buf = new StringBuffer();
                for(String arg : args) {
                    buf.append(arg).append(" ");
                }
                log.trace("---> args: {}", buf.toString());

            } catch (SigarException e) {
                log.error("<--- pid: {} - {}", pid, e.getMessage());
            }
        }
    }
}
