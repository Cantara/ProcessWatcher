package no.cantara.process.support;

public enum ProcessWatchState {
    /**
     * Seen by a producer, not yet qualified against the fingerprint store
     */
    DISCOVERED,
    /**
     * Observed during the fingerprinting (learning) period and added to the baseline
     */
    LEARNING,
    /**
     * Fingerprint matches the learned baseline
     */
    KNOWN,
    /**
     * Matches a configured whitelist pattern
     */
    WHITELISTED,
    /**
     * Unknown process observed after the fingerprinting period ended
     */
    SUSPICIOUS
}
