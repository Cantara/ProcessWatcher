package no.cantara.process.worker;

import no.cantara.process.ProcessWatcher;
import no.cantara.process.event.DefconEvent;
import no.cantara.process.event.ProcessDTO;
import no.cantara.process.event.ProcessWatchEvent;
import no.cantara.process.event.ProcessWatcherInternalEvent;
import no.cantara.process.support.FingerprintStore;
import no.cantara.process.support.ProcessWatchHandler;
import no.cantara.process.support.ProcessWatchKey;
import no.cantara.process.support.ProcessWatchState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Qualifies discovered processes against the fingerprint baseline and invokes the
 * registered handlers, mirroring PathWatcher's FilePollEventsConsumer.
 */
public class ProcessEventsConsumer implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(ProcessEventsConsumer.class);

    private final BlockingQueue<ProcessWatchEvent> queue;

    private final ScheduledExecutorService escalationScheduler;

    public ProcessEventsConsumer(BlockingQueue<ProcessWatchEvent> queue, ScheduledExecutorService escalationScheduler) {
        this.queue = queue;
        this.escalationScheduler = escalationScheduler;
    }

    @Override
    public void run() {
        for (; ; ) {
            try {
                ProcessWatchEvent event = queue.take();
                log.trace("Consumed: [{}]{}", event.getProcessWatchKey(), event);

                if (ProcessWatchKey.PROCESS_STARTED.equals(event.getProcessWatchKey())) {
                    handleStartedProcess(event);
                } else if (ProcessWatchKey.PROCESS_TERMINATED.equals(event.getProcessWatchKey())) {
                    invokeHandlers(event, ProcessWatcher.getInstance().getTerminatedHandlers());
                    ProcessWatcher.getInstance().getProcessWorkerMap().remove(event.getProcess().getPid());
                }
            } catch (InterruptedException e) {
                ProcessWatcher.getInstance().post(new ProcessWatcherInternalEvent(this, "ProcessConsumer got interrupted"));
                log.trace("ProcessConsumer has ended!");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("ProcessConsumer failed", e);
            }
        }
    }

    private void handleStartedProcess(ProcessWatchEvent event) {
        FingerprintStore fingerprintStore = ProcessWatcher.getInstance().getFingerprintStore();
        ProcessDTO process = event.getProcess();

        ProcessWatchState state;
        DefconEvent defconEvent = null;
        if (fingerprintStore.isWhitelisted(process)) {
            state = ProcessWatchState.WHITELISTED;
        } else if (fingerprintStore.isLearning()) {
            fingerprintStore.learn(process);
            state = ProcessWatchState.LEARNING;
        } else if (fingerprintStore.isKnown(process)) {
            state = ProcessWatchState.KNOWN;
        } else {
            state = ProcessWatchState.SUSPICIOUS;
            defconEvent = DefconEvent.classify(process, fingerprintStore);
        }

        // the baseline is complete once the learning period has ended - persist it if configured
        if (!fingerprintStore.isLearning()) {
            fingerprintStore.saveBaselineOnce();
        }

        ProcessWatchEvent qualifiedEvent = new ProcessWatchEvent(process, ProcessWatchKey.PROCESS_STARTED, state, defconEvent);
        ProcessWatcher.getInstance().post(qualifiedEvent);
        invokeHandlers(qualifiedEvent, ProcessWatcher.getInstance().getStartedHandlers());

        if (ProcessWatchState.SUSPICIOUS.equals(state)) {
            ProcessWatchEvent suspiciousEvent = new ProcessWatchEvent(process, ProcessWatchKey.PROCESS_SUSPICIOUS, state, defconEvent);
            ProcessWatcher.getInstance().post(suspiciousEvent);
            log.warn("Suspicious process detected: {}", suspiciousEvent);
            invokeHandlers(suspiciousEvent, ProcessWatcher.getInstance().getSuspiciousHandlers());
            scheduleEscalation(process, defconEvent);
        }
    }

    /**
     * A suspicious process that remains alive beyond the escalation delay is re-reported
     * with a one-level escalated grading - the process equivalent of PathWatcher's
     * delayed FileCompletelyCreated qualification.
     */
    private void scheduleEscalation(ProcessDTO process, DefconEvent defconEvent) {
        long delay = ProcessWatcher.SUSPICIOUS_ESCALATION_DELAY;
        if (delay <= 0 || defconEvent.getLevel() <= 1) {
            return;
        }
        try {
            escalationScheduler.schedule(() -> {
                boolean alive = ProcessHandle.of(process.getPid())
                        .map(ProcessHandle::isAlive)
                        .orElse(false);
                if (!alive) {
                    return;
                }
                FingerprintStore store = ProcessWatcher.getInstance().getFingerprintStore();
                if (store.isKnown(process) || store.isWhitelisted(process)) {
                    return;
                }
                DefconEvent escalated = DefconEvent.escalate(defconEvent);
                ProcessWatchEvent escalatedEvent = new ProcessWatchEvent(process,
                        ProcessWatchKey.PROCESS_SUSPICIOUS, ProcessWatchState.SUSPICIOUS, escalated);
                ProcessWatcher.getInstance().post(escalatedEvent);
                log.warn("Suspicious process still alive after {}ms, escalating: {}", delay, escalatedEvent);
                invokeHandlers(escalatedEvent, ProcessWatcher.getInstance().getSuspiciousHandlers());
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the watcher is shutting down - nothing to escalate
        }
    }

    private void invokeHandlers(ProcessWatchEvent processWatchEvent, Set<ProcessWatchHandler> actions) {
        for (ProcessWatchHandler action : actions) {
            try {
                action.invoke(processWatchEvent);
            } catch (Exception e) {
                log.error("Handler exception from event type {}:\n{}", processWatchEvent.getProcessWatchKey().toString(), e);
            }
        }
    }
}
