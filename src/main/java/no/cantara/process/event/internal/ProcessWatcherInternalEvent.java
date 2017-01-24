package no.cantara.process.event.internal;

import java.lang.ref.WeakReference;

public class ProcessWatcherInternalEvent {

    private final WeakReference<Runnable> source;
    private final String message;

    public ProcessWatcherInternalEvent(Runnable source, String message) {
        this.source = new WeakReference<>(source);
        this.message = message;
    }

    public WeakReference<Runnable> getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }
}
