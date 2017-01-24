package no.cantara.process.event.internal;

import no.cantara.process.event.ProcessDTO;

public class ProcessWatchEvent {

    private ProcessDTO suspiciousDTO;


    public ProcessWatchEvent(ProcessDTO process) {
        suspiciousDTO = process;
    }

}
