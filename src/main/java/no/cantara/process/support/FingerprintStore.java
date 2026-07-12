package no.cantara.process.support;

import no.cantara.process.event.ProcessDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * The learned process baseline that implements ProcessWatcher's two phases:
 * <ol>
 * <li>During the fingerprinting period every observed process is recorded (learned).</li>
 * <li>After the period has elapsed, processes whose fingerprint is not in the baseline
 * and does not match a whitelist pattern are considered suspicious.</li>
 * </ol>
 * A fingerprint is {@code user|command} (see {@link ProcessDTO#getFingerprint()}), so a known
 * executable started by a new user is NOT considered known. The set of known commands is kept
 * separately to allow that distinction when grading threat levels.
 * <p>
 * A baseline file may be configured: once the learning period ends the baseline is written
 * to it, and {@link #loadBaseline(Path)} restores a completed baseline so a restarted
 * application does not need a new learning period (during which an intruder would be learned
 * as normal).
 * <p>
 * Note that qualification is naive by design (see README): a process that starts in the very
 * last moments of the fingerprinting period may be qualified just after it ends and thus be
 * reported as suspicious.
 */
public class FingerprintStore {

    private static final Logger log = LoggerFactory.getLogger(FingerprintStore.class);

    /**
     * Fingerprinting period value meaning "learn forever" (never report anything suspicious)
     */
    public static final long LEARN_FOREVER = -1;

    private static final String FINGERPRINT_LINE_PREFIX = "F\t";
    private static final String COMMAND_LINE_PREFIX = "C\t";

    private final long fingerprintingPeriodMs;
    private final long learningStartedAt;
    private final Set<String> fingerprints = ConcurrentHashMap.newKeySet();
    private final Set<String> knownCommands = ConcurrentHashMap.newKeySet();
    private final Set<Pattern> whitelist = ConcurrentHashMap.newKeySet();

    private volatile Path baselineFile;
    private final AtomicBoolean baselineSaved = new AtomicBoolean(false);

    public FingerprintStore(long fingerprintingPeriodMs) {
        this.fingerprintingPeriodMs = fingerprintingPeriodMs;
        this.learningStartedAt = System.currentTimeMillis();
    }

    /**
     * @return true while the fingerprinting (learning) period is still running
     */
    public boolean isLearning() {
        if (fingerprintingPeriodMs == LEARN_FOREVER) {
            return true;
        }
        return (System.currentTimeMillis() - learningStartedAt) < fingerprintingPeriodMs;
    }

    public void learn(ProcessDTO process) {
        fingerprints.add(process.getFingerprint());
        if (!process.getCommand().isEmpty()) {
            knownCommands.add(process.getCommand());
        }
    }

    public boolean isKnown(ProcessDTO process) {
        return fingerprints.contains(process.getFingerprint());
    }

    public boolean isKnownCommand(String command) {
        return knownCommands.contains(command);
    }

    /**
     * @param regex pattern matched against both the command and the full command line
     */
    public void addWhitelistPattern(String regex) {
        whitelist.add(Pattern.compile(regex));
    }

    public boolean isWhitelisted(ProcessDTO process) {
        for (Pattern pattern : whitelist) {
            if (pattern.matcher(process.getCommand()).matches()
                    || pattern.matcher(process.getCommandLine()).matches()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return fingerprints.size();
    }

    /**
     * @param baselineFile the file the baseline is written to when the learning period ends
     */
    public void setBaselineFile(Path baselineFile) {
        this.baselineFile = baselineFile;
    }

    public Path getBaselineFile() {
        return baselineFile;
    }

    /**
     * Writes the baseline to the configured baseline file once. Called by the consumer when
     * it observes that the learning period has ended, and on ProcessWatcher shutdown.
     * A no-op when no baseline file is configured, while still learning, or already saved.
     */
    public void saveBaselineOnce() {
        if (baselineFile == null || isLearning() || fingerprints.isEmpty()) {
            return;
        }
        if (!baselineSaved.compareAndSet(false, true)) {
            return;
        }
        try {
            saveBaseline(baselineFile);
            log.info("Saved fingerprint baseline of {} fingerprints to {}", fingerprints.size(), baselineFile);
        } catch (IOException e) {
            log.error("Unable to save fingerprint baseline to {}", baselineFile, e);
            baselineSaved.set(false);
        }
    }

    /**
     * Writes the baseline atomically (write to temp file, then move) to the given file.
     * @param file the file to write the baseline to
     * @throws IOException if writing fails
     */
    public synchronized void saveBaseline(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String fingerprint : fingerprints) {
            lines.add(FINGERPRINT_LINE_PREFIX + fingerprint);
        }
        for (String command : knownCommands) {
            lines.add(COMMAND_LINE_PREFIX + command);
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tempFile, lines, StandardCharsets.UTF_8);
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Restores a completed baseline from file. The returned store is not learning - all
     * processes not in the baseline (or whitelisted) are qualified immediately.
     * @param file the baseline file written by {@link #saveBaseline(Path)}
     * @return the restored store
     * @throws IOException if the file cannot be read
     */
    public static FingerprintStore loadBaseline(Path file) throws IOException {
        FingerprintStore store = new FingerprintStore(0);
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith(FINGERPRINT_LINE_PREFIX)) {
                store.fingerprints.add(line.substring(FINGERPRINT_LINE_PREFIX.length()));
            } else if (line.startsWith(COMMAND_LINE_PREFIX)) {
                store.knownCommands.add(line.substring(COMMAND_LINE_PREFIX.length()));
            }
        }
        store.baselineFile = file;
        store.baselineSaved.set(true);
        log.info("Loaded fingerprint baseline of {} fingerprints from {}", store.fingerprints.size(), file);
        return store;
    }

}
