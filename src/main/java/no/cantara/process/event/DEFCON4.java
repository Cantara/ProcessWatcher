package no.cantara.process.event;

public class DEFCON4 extends DefconEvent {

    public DEFCON4(ProcessDTO process) {
        super(process);
    }

    @Override
    public int getLevel() {
        return 4;
    }

    @Override
    public String getInformation() {
        return "Unknown process observed after the fingerprinting period";
    }
}
