package no.cantara.process.watcher;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessWatcher {

    private final static Logger log = LoggerFactory.getLogger(ProcessWatcher.class);

    private static ProcessWatcher instance;

    public static long POLL_INTERVAL = 100;


    public static long WORKER_SHUTDOWN_TIMEOUT = 150; // used in force shutdownNow hook


    private final EventBus processEventBus;
    private EventWorker eventWorker;




    private boolean running;

    private ProcessWatcher() {
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



    public void setThreadPollInterval(int threadPollInterval) {
        POLL_INTERVAL = threadPollInterval;
    }

    public void setWorkerShutdownTimeout(long workerShutdownTimeout) {
        WORKER_SHUTDOWN_TIMEOUT = workerShutdownTimeout;
    }

    protected void subscribe(Object object) {
        // processEventBus.register(object);
    }


    public String getConfigInfo() {
        JsonObject json = new JsonObject();
        json.addProperty("workerShutdownTimeout", WORKER_SHUTDOWN_TIMEOUT);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(json);
    }


    private boolean isRunning(){
        return running;
    }



    public void start() {
        if (!isRunning()) {
            // eventWorker = new EventWorker();
            eventWorker = new EventWorker();
            // processProducerWorker = new ProcessProducerWorker();

            eventWorker.start();
            // processProducerWorker.start();

            running = true;
            log.trace("ProcessWatcher is started with configuration:\n{}", getConfigInfo());
        } else {
            log.trace("Cannot start PathWatcher because it is already running!");
        }
    }

    public void stop() {
        if (isRunning()) {
            running = false;
            log.trace("ProcessWatcher is now shutdown!");
        } else {
            log.trace("Cannot stop PathWatcher because it is shutdown!");
        }
    }

    public static ProcessWatcher getInstance() {
        if (instance == null) {
            instance = new ProcessWatcher();
        }
        return instance;
    }
}
