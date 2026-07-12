package no.cantara.process.event;

public class DEFCON2 extends DefconEvent {

    public DEFCON2(ProcessDTO process) {
        super(process);
    }

    @Override
    public int getLevel() {
        return 2;
    }

    @Override
    public String getInformation() {
        return "Unknown network probing tool, or unknown process running with elevated privileges";
    }
}
