package no.cantara.process.event;

public class DEFCON1 extends DefconEvent {

    public DEFCON1(ProcessDTO process) {
        super(process);
    }

    @Override
    public int getLevel() {
        return 1;
    }

    @Override
    public String getInformation() {
        return "Unknown network probing tool running with elevated privileges";
    }
}
