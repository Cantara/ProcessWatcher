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
import java.util.Arrays;
import java.util.HashSet;
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

    /**
     * Interpreter/shell executables (base names, version suffixes normalized away) whose
     * fingerprint includes the command line, so that a trusted interpreter running an unseen
     * script or arguments is not mistaken for a known process (living-off-the-land).
     */
    private static final Set<String> DEFAULT_INTERPRETERS = new HashSet<>(Arrays.asList(
            "bash", "sh", "dash", "zsh", "ash", "ksh", "csh", "tcsh", "fish",
            "python", "perl", "ruby", "node", "php", "lua", "tclsh", "pwsh", "powershell"
    ));

    private final long fingerprintingPeriodMs;
    private final long learningStartedAt;
    private final Set<String> fingerprints = ConcurrentHashMap.newKeySet();
    private final Set<String> knownCommands = ConcurrentHashMap.newKeySet();
    private final Set<Pattern> whitelist = ConcurrentHashMap.newKeySet();
    private final Set<Pattern> commandLineWhitelist = ConcurrentHashMap.newKeySet();
    private final Set<String> interpreters = ConcurrentHashMap.newKeySet();

    private volatile Path baselineFile;
    private final AtomicBoolean baselineSaved = new AtomicBoolean(false);

    public FingerprintStore(long fingerprintingPeriodMs) {
        this.fingerprintingPeriodMs = fingerprintingPeriodMs;
        this.learningStartedAt = System.currentTimeMillis();
        this.interpreters.addAll(DEFAULT_INTERPRETERS);
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
        fingerprints.add(fingerprintOf(process));
        if (!process.getCommand().isEmpty()) {
            knownCommands.add(process.getCommand());
        }
    }

    public boolean isKnown(ProcessDTO process) {
        return fingerprints.contains(fingerprintOf(process));
    }

    /**
     * The effective baseline identity of a process: {@code user|command} normally, but
     * {@code user|command|commandLine} for interpreters and shells so that a trusted
     * interpreter running an unseen script or arguments does not match the baseline.
     */
    public String fingerprintOf(ProcessDTO process) {
        String base = process.getFingerprint();
        if (isInterpreter(process.getCommand())) {
            return base + "|" + process.getCommandLine();
        }
        return base;
    }

    /**
     * Register an additional interpreter/shell by executable base name (e.g. "elixir").
     * @param interpreterBaseName the executable base name, without path or version suffix
     */
    public void addInterpreter(String interpreterBaseName) {
        interpreters.add(interpreterBaseName);
    }

    /**
     * @param command an executable path (or base name)
     * @return true if the executable is a known interpreter/shell
     */
    public boolean isInterpreter(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String baseName = command.substring(command.lastIndexOf('/') + 1);
        if (interpreters.contains(baseName)) {
            return true;
        }
        // normalize version suffixes: python3, python3.11, perl5.36 -> python, perl
        String normalized = baseName.replaceAll("[0-9.]+$", "");
        return !normalized.equals(baseName) && interpreters.contains(normalized);
    }

    public boolean isKnownCommand(String command) {
        return knownCommands.contains(command);
    }

    /**
     * Whitelist processes whose executable path ({@link ProcessDTO#getCommand()}) matches the
     * pattern. Matching the executable path is spoof-resistant: unlike the command line, a
     * process cannot freely rewrite the path it was executed from.
     * @param regex pattern matched against the command (executable path)
     */
    public void addWhitelistPattern(String regex) {
        whitelist.add(Pattern.compile(regex));
    }

    /**
     * Whitelist processes whose full command line matches the pattern. Opt-in, because a
     * process can set its own argv - an attacker can craft a command line to match a lax
     * command-line whitelist. Prefer {@link #addWhitelistPattern(String)} where possible.
     * @param regex pattern matched against the full command line
     */
    public void addCommandLineWhitelistPattern(String regex) {
        commandLineWhitelist.add(Pattern.compile(regex));
    }

    public boolean isWhitelisted(ProcessDTO process) {
        for (Pattern pattern : whitelist) {
            if (pattern.matcher(process.getCommand()).matches()) {
                return true;
            }
        }
        for (Pattern pattern : commandLineWhitelist) {
            if (pattern.matcher(process.getCommandLine()).matches()) {
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
