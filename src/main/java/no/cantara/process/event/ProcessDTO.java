package no.cantara.process.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessDTO {

    @JsonProperty("pid")
    private long pid;
    @JsonProperty("user")
    private String user = "";
    @JsonProperty("command")
    private String command = "";
    @JsonProperty("commandLine")
    private String commandLine = "";
    @JsonProperty("startTime")
    private long startTimeEpochMs;

    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user == null ? "" : user.trim();
    }

    /**
     * @return the executable, e.g. {@code /usr/bin/sleep}. May be empty for processes
     * the JVM is not permitted to inspect (e.g. kernel threads).
     */
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command.trim();
    }

    /**
     * @return the full command line including arguments, when available
     */
    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine == null ? "" : commandLine.trim();
    }

    public long getStartTimeEpochMs() {
        return startTimeEpochMs;
    }

    public void setStartTimeEpochMs(long startTimeEpochMs) {
        this.startTimeEpochMs = startTimeEpochMs;
    }

    /**
     * The identity used against the {@link no.cantara.process.support.FingerprintStore} baseline.
     * The executable path plus the owning user - deliberately not the full command line, which
     * often contains varying arguments that would cause false alerts.
     */
    public String getFingerprint() {
        return user + "|" + command;
    }

    @Override
    public String toString() {
        return "pid: " + pid + ", user: " + user + ", command: " + command
                + (commandLine.isEmpty() || commandLine.equals(command) ? "" : ", commandLine: " + commandLine);
    }

}
