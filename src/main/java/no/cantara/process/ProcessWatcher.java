package no.cantara.process;

import com.google.common.collect.Sets;
import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.event.ProcessWatcherInternalEvent;
import no.cantara.process.support.FingerprintStore;
import no.cantara.process.support.ProcessWatchHandler;
import no.cantara.process.support.ProcessWatchScanner;
import no.cantara.process.support.ProcessWorkerMap;
import no.cantara.process.util.ListeningSocketSupport;
import no.cantara.process.worker.EventWorker;
import no.cantara.process.worker.ProcessConsumerWorker;
import no.cantara.process.worker.ProcessProducerWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * A library for watching, fingerprinting and eventing of processes - a simplified process
 * IDS threat notifier. Mirrors the PathWatcher architecture: a singleton facade over a
 * producer (process discovery) - queue - consumer (fingerprint qualification) pipeline
 * with registrable handlers.
 * <p>
 * Usage:
 * <pre>{@code
 * ProcessWatcher pw = ProcessWatcher.getInstance();
 * pw.setFingerprintingPeriod(20 * 60 * 1000); // learn for 20 minutes
 * pw.whitelist(".*logrotate.*");
 * pw.registerSuspiciousProcessHandler(event -> alert(event.getDefconEvent()));
 * pw.start();
 * }</pre>
 */
public class ProcessWatcher {

    private final static Logger log = LoggerFactory.getLogger(ProcessWatcher.class);

    private static ProcessWatcher instance;

    /**
     * How often the process table is scanned, in milliseconds. -1 = scan only once
     */
    public static long SCAN_PROCESS_INTERVAL = 5000;

    /**
     * How long the fingerprinting (learning) phase lasts after start(), in milliseconds.
     * Use a period that covers the natural process variation of the host, e.g. 20 minutes
     * for servers without cronjobs, or 36 hours for systems with daily jobs.
     * {@link FingerprintStore#LEARN_FOREVER} (-1) disables suspicious-process reporting entirely.
     */
    public static long FINGERPRINTING_PERIOD = 20 * 60 * 1000;

    /**
     * A suspicious process that is still alive this many milliseconds after detection is
     * re-reported with a one-level escalated DEFCON grading. -1 disables escalation.
     */
    public static long SUSPICIOUS_ESCALATION_DELAY = 60 * 1000;

    public static long WORKER_SHUTDOWN_TIMEOUT = 150; // used in force shutdownNow hook

    private final EventBus processEventBus;

    private ProcessWorkerMap processWorkerMap;

    private FingerprintStore fingerprintStore;

    private final Set<String> whitelistPatterns = Sets.newConcurrentHashSet();

    private final Set<String> commandLineWhitelistPatterns = Sets.newConcurrentHashSet();

    private final Set<String> extraInterpreters = Sets.newConcurrentHashSet();

    private Path fingerprintBaselineFile;

    private EventWorker eventWorker;

    private ProcessProducerWorker processProducerWorker;

    private ProcessConsumerWorker processConsumerWorker;

    private final Set<ProcessWatchHandler> startedHandler = Sets.newConcurrentHashSet();

    private final Set<ProcessWatchHandler> terminatedHandler = Sets.newConcurrentHashSet();

    private final Set<ProcessWatchHandler> suspiciousHandler = Sets.newConcurrentHashSet();

    private ProcessWatchScanner processWatchScannerMode;

    private boolean running;

    private ProcessWatcher() {
        processWatchScannerMode = ProcessWatchScanner.NATIVE_PROCESS_API;
        processEventBus = new EventBus();
        subscribe(new DeadEventsSubscriber());
        subscribe(this);
    }

    public static class DeadEventsSubscriber {
        @Subscribe
        public void handleDeadEvent(DeadEvent deadEvent) {
            log.error("DEAD EVENT: {}", deadEvent.getEvent());
        }
    }

    /**
     * Register a handler invoked for every process start observed (in any state)
     * @param startedProcessAction the handler for the event
     */
    public void registerProcessStartedHandler(ProcessWatchHandler startedProcessAction) {
        startedHandler.add(startedProcessAction);
    }

    public Set<ProcessWatchHandler> getStartedHandlers() {
        return startedHandler;
    }

    /**
     * Register a handler invoked when a previously observed process terminates
     * @param terminatedProcessAction the handler for the event
     */
    public void registerProcessTerminatedHandler(ProcessWatchHandler terminatedProcessAction) {
        terminatedHandler.add(terminatedProcessAction);
    }

    public Set<ProcessWatchHandler> getTerminatedHandlers() {
        return terminatedHandler;
    }

    /**
     * Register a handler invoked when an unknown, non-whitelisted process is observed after
     * the fingerprinting period. The event carries a naive DEFCON threat-level grading.
     * @param suspiciousProcessAction the handler for the event
     */
    public void registerSuspiciousProcessHandler(ProcessWatchHandler suspiciousProcessAction) {
        suspiciousHandler.add(suspiciousProcessAction);
    }

    public Set<ProcessWatchHandler> getSuspiciousHandlers() {
        return suspiciousHandler;
    }

    /**
     * Whitelist processes whose executable path matches the regex. Matching the executable
     * path is spoof-resistant - a process cannot freely rewrite the path it was executed from.
     * @param regex pattern matched against the command (executable path)
     */
    public void whitelist(String regex) {
        whitelistPatterns.add(regex);
        if (fingerprintStore != null) {
            fingerprintStore.addWhitelistPattern(regex);
        }
    }

    /**
     * Whitelist processes whose full command line matches the regex. Opt-in and to be used
     * with care: a process can set its own argv to match a lax command-line pattern. Prefer
     * {@link #whitelist(String)} where the executable path is sufficient.
     * @param regex pattern matched against the full command line
     */
    public void whitelistCommandLine(String regex) {
        commandLineWhitelistPatterns.add(regex);
        if (fingerprintStore != null) {
            fingerprintStore.addCommandLineWhitelistPattern(regex);
        }
    }

    public void forceProcessScannerMode(ProcessWatchScanner mode) {
        processWatchScannerMode = mode;
    }

    public boolean isNativeEvents() {
        return ProcessWatchScanner.NATIVE_PROCESS_API.equals(processWatchScannerMode);
    }

    public boolean isPollEvents() {
        return ProcessWatchScanner.POLL_PROCESS_EXEC.equals(processWatchScannerMode);
    }

    @Subscribe
    private synchronized void eventHandler(ProcessWatcherInternalEvent event) {
        try {
            if (eventWorker != null) {
                eventWorker.getQueue().put(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Subscribe
    private synchronized void processWatchEvent(ProcessWatchEvent event) {
        if (event != null) {
            getProcessWorkerMap().add(event.getProcess().getPid(), event);
        }
    }

    public synchronized ProcessWorkerMap getProcessWorkerMap() {
        if (processWorkerMap == null) {
            processWorkerMap = new ProcessWorkerMap();
        }
        return processWorkerMap;
    }

    public synchronized FingerprintStore getFingerprintStore() {
        if (fingerprintStore == null) {
            fingerprintStore = createFingerprintStore();
        }
        return fingerprintStore;
    }

    private FingerprintStore createFingerprintStore() {
        FingerprintStore store = null;
        if (fingerprintBaselineFile != null && Files.exists(fingerprintBaselineFile)) {
            try {
                store = FingerprintStore.loadBaseline(fingerprintBaselineFile);
            } catch (IOException e) {
                log.error("Unable to load fingerprint baseline from {} - starting a new learning period",
                        fingerprintBaselineFile, e);
            }
        }
        if (store == null) {
            store = new FingerprintStore(FINGERPRINTING_PERIOD);
            store.setBaselineFile(fingerprintBaselineFile);
        }
        for (String pattern : whitelistPatterns) {
            store.addWhitelistPattern(pattern);
        }
        for (String pattern : commandLineWhitelistPatterns) {
            store.addCommandLineWhitelistPattern(pattern);
        }
        for (String interpreter : extraInterpreters) {
            store.addInterpreter(interpreter);
        }
        return store;
    }

    /**
     * Register an additional interpreter/shell executable (base name) beyond the built-in
     * defaults. Interpreter processes are fingerprinted on their full command line, so a
     * trusted interpreter running an unseen script is not mistaken for a known process.
     * @param interpreterBaseName the executable base name, e.g. "elixir"
     */
    public void addInterpreter(String interpreterBaseName) {
        extraInterpreters.add(interpreterBaseName);
        if (fingerprintStore != null) {
            fingerprintStore.addInterpreter(interpreterBaseName);
        }
    }

    /**
     * Configure a file the learned baseline is persisted to when the fingerprinting period
     * ends. When the file already exists at start(), the baseline is restored from it and
     * no new learning period is needed - a restarted application is immediately armed.
     * @param fingerprintBaselineFile the baseline file
     */
    public void setFingerprintBaselineFile(Path fingerprintBaselineFile) {
        this.fingerprintBaselineFile = fingerprintBaselineFile;
    }

    public void setProcessScanInterval(long scanInterval) {
        SCAN_PROCESS_INTERVAL = scanInterval;
    }

    public void setFingerprintingPeriod(long fingerprintingPeriod) {
        FINGERPRINTING_PERIOD = fingerprintingPeriod;
    }

    public void setSuspiciousEscalationDelay(long suspiciousEscalationDelay) {
        SUSPICIOUS_ESCALATION_DELAY = suspiciousEscalationDelay;
    }

    public void setWorkerShutdownTimeout(long workerShutdownTimeout) {
        WORKER_SHUTDOWN_TIMEOUT = workerShutdownTimeout;
    }

    protected void subscribe(Object object) {
        processEventBus.register(object);
    }

    public void post(ProcessWatcherInternalEvent event) {
        processEventBus.post(event);
    }

    public void post(ProcessWatchEvent event) {
        processEventBus.post(event);
    }

    public boolean isRunning() {
        boolean isRunning = false;
        if (processProducerWorker != null && processProducerWorker.isRunning()) {
            isRunning = true;
        }
        if (processConsumerWorker != null && processConsumerWorker.isRunning()) {
            isRunning = true;
        }
        return running && isRunning;
    }

    public boolean isInterrupted() {
        boolean isTerminated = false;
        if (processProducerWorker != null && processProducerWorker.isTerminated()) {
            isTerminated = true;
        }
        if (processConsumerWorker != null && processConsumerWorker.isTerminated()) {
            isTerminated = true;
        }
        return isTerminated;
    }

    public String getConfigInfo() {
        JsonObject json = new JsonObject();
        json.addProperty("processWatchScannerMode", processWatchScannerMode.toString());
        json.addProperty("scanProcessInterval", SCAN_PROCESS_INTERVAL);
        json.addProperty("fingerprintingPeriod", FINGERPRINTING_PERIOD);
        json.addProperty("whitelist", whitelistPatterns.toString());
        json.addProperty("commandLineWhitelist", commandLineWhitelistPatterns.toString());
        json.addProperty("suspiciousEscalationDelay", SUSPICIOUS_ESCALATION_DELAY);
        json.addProperty("fingerprintBaselineFile", String.valueOf(fingerprintBaselineFile));
        json.addProperty("workerShutdownTimeout", WORKER_SHUTDOWN_TIMEOUT);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(json);
    }

    public void start() {
        if (!isRunning()) {
            fingerprintStore = createFingerprintStore();
            eventWorker = new EventWorker();
            processProducerWorker = new ProcessProducerWorker(processWatchScannerMode);
            processConsumerWorker = new ProcessConsumerWorker(processProducerWorker.getQueue());

            eventWorker.start();
            processProducerWorker.start();
            processConsumerWorker.start();

            running = true;
            warnIfSocketEscalationUnavailable();
            log.trace("ProcessWatcher is started with configuration:\n{}", getConfigInfo());
        } else {
            log.trace("Cannot start ProcessWatcher because it is already running!");
        }
    }

    public void stop() {
        if (isRunning()) {
            processProducerWorker.shutdown();
            processConsumerWorker.shutdown();
            eventWorker.shutdown();
            if (fingerprintStore != null) {
                fingerprintStore.saveBaselineOnce();
            }
            startedHandler.clear();
            terminatedHandler.clear();
            suspiciousHandler.clear();
            whitelistPatterns.clear();
            commandLineWhitelistPatterns.clear();
            fingerprintBaselineFile = null;
            getProcessWorkerMap().clear();
            processWorkerMap = null;
            fingerprintStore = null;
            running = false;
            log.trace("ProcessWatcher is now shutdown!");
        } else {
            log.trace("Cannot stop ProcessWatcher because it is shutdown!");
        }
    }

    /**
     * @return true if socket-based DEFCON escalation (listening probes, raw and packet
     * sockets) can observe processes owned by other users. When false, that escalation is
     * limited to this watcher's own user - run as root or grant CAP_SYS_PTRACE to enable it.
     */
    public boolean isSocketEscalationAvailable() {
        return ListeningSocketSupport.canInspectForeignProcessSockets();
    }

    private void warnIfSocketEscalationUnavailable() {
        if (!isSocketEscalationAvailable()) {
            log.warn("Socket-based threat escalation is DISABLED: cannot read other users' "
                    + "process sockets (needs root or CAP_SYS_PTRACE). Listening-probe, raw-socket "
                    + "and packet-sniffer escalation will only apply to processes owned by '{}'.",
                    System.getProperty("user.name"));
        }
    }

    public static synchronized ProcessWatcher getInstance() {
        if (instance == null) {
            instance = new ProcessWatcher();
        }
        return instance;
    }
}
