package no.cantara.process;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ProcessWatcherConfigTest {

    @Test
    public void configInfoIsJsonWithoutAnyJsonLibrary() {
        ProcessWatcher pw = ProcessWatcher.getInstance();
        pw.whitelist(".*pw-config-test-never-matches\\d+.*");

        String json = pw.getConfigInfo();

        assertTrue(json.startsWith("{\n") && json.endsWith("\n}"), json);
        assertTrue(json.contains("\"processWatchScannerMode\": \"NATIVE_PROCESS_API\""), json);
        assertTrue(json.contains("\"whitelist\": [\".*pw-config-test-never-matches\\\\d+.*\"]"), "regex backslash must be JSON-escaped: " + json);
        assertTrue(json.contains("\"fingerprintBaselineFile\": null"), json);
        assertTrue(json.contains("\"workerShutdownTimeout\": " + ProcessWatcher.WORKER_SHUTDOWN_TIMEOUT + "\n}"), json);
    }

    @Test
    public void escapesControlCharactersAndQuotes() {
        assertEquals(ProcessWatcher.escapeJson("a\"b\\c\nd\u0001"), "a\\\"b\\\\c\\nd\\u0001");
    }
}
