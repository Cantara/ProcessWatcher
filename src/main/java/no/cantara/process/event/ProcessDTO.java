package no.cantara.process.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessDTO {

    @JsonProperty("pid")
    private String pid = "";
    @JsonProperty("cmd")
    private String cmd = "";
    @JsonProperty("time")
    private String time = "";
    @JsonProperty("uid")
    private String uid = "";

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid.trim();
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd.trim();
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time.trim();
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid.trim();
    }

    public String getFingerPrint() {
        return cmd + uid;
    }


}
