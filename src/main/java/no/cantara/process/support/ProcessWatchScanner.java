package no.cantara.process.support;

/**
 * Scanner backend for discovering processes, mirroring PathWatcher's PathWatchScanner.
 * <p>
 * NATIVE_PROCESS_API uses the JDK {@link java.lang.ProcessHandle} API (Java 9+) and is
 * the preferred mode on all platforms. POLL_PROCESS_EXEC shells out to {@code ps} and
 * exists as a fallback for exotic runtimes where the native API yields no information.
 */
public enum ProcessWatchScanner {
    NATIVE_PROCESS_API,
    POLL_PROCESS_EXEC
}
