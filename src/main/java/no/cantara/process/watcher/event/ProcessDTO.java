package no.cantara.process.watcher.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessDTO {

    @JsonProperty("pid")
    private String pid = "";
    @JsonProperty("cmd")
    private String cmd = "";
    @JsonProperty("time")
    private String time = "";

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @JsonProperty("uid")
    private String uid = "";


}
