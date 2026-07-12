package no.cantara.process.event;

/**
 * Created by totto on 24.01.17.
 */
public class DEFCON3 extends DefconEvent {

    public DEFCON3(ProcessDTO process) {
        super(process);
    }

    @Override
    public int getLevel() {
        return 3;
    }

    @Override
    public String getInformation() {
        return "Known command observed with an unknown fingerprint (started by a new user)";
    }
}
