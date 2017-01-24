package no.cantara.process.event;

public class DefconEvent {

    private ProcessDTO suspiciousDTO;


    public DefconEvent(ProcessDTO process) {
        suspiciousDTO = process;
    }

    private String getInformation() {
        return "No specific defcon information available";
    }
}
