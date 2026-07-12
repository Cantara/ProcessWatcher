package no.cantara.process.support;

import no.cantara.process.event.ProcessDTO;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * Note that qualification is naive by design (see README): a process that starts in the very
 * last moments of the fingerprinting period may be qualified just after it ends and thus be
 * reported as suspicious.
 */
public class FingerprintStore {

    /**
     * Fingerprinting period value meaning "learn forever" (never report anything suspicious)
     */
    public static final long LEARN_FOREVER = -1;

    private final long fingerprintingPeriodMs;
    private final long learningStartedAt;
    private final Set<String> fingerprints = ConcurrentHashMap.newKeySet();
    private final Set<String> knownCommands = ConcurrentHashMap.newKeySet();
    private final Set<Pattern> whitelist = ConcurrentHashMap.newKeySet();

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

}
