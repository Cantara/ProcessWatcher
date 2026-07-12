package no.cantara.process.support;

import no.cantara.process.event.ProcessWatchEvent;

public interface ProcessWatchHandler {

    void invoke(ProcessWatchEvent event);

}
