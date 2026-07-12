# ProcessWatcher

**[📖 Documentation &amp; guide → cantara.github.io/ProcessWatcher](https://cantara.github.io/ProcessWatcher/)**

ProcessWatcher is a java library for watching, fingerprinting and eventing of suspicious processes (A simplified process Intrusion Detection System/IDS threat notifier)

This library will record/fingerprint the system for a configurable period (i.e. 20 minutes for servers with no logrotate or daily cronjobs or 36 hours for systems with daily jobs++). You can also whitelist processes in the configuration.

If unknown processes are discovered after the fingerprinting period which is not fingerprinted or whitelisted, the library will trigger events which the application can intrepret as IDS signals. We will attempt to do a naive DEFCON threat-level mapping of the events but this categorization should only be handled as a simple indication (and thus not trusted) in application reactions.

The main use-case here is to try to detect "listening probes" so that the application/service can act accordingly. This approach will not try to address agressive intrusions.

### Usage

```java
ProcessWatcher pw = ProcessWatcher.getInstance();
pw.setFingerprintingPeriod(20 * 60 * 1000);   // learn the process baseline for 20 minutes
pw.setProcessScanInterval(5000);              // scan the process table every 5 seconds
pw.setSuspiciousEscalationDelay(60 * 1000);   // re-report one level higher if still alive after 1 minute
pw.setFingerprintBaselineFile(Paths.get("baseline.txt")); // skip re-learning on restart
pw.whitelist(".*logrotate.*");                // regex matched against the executable path
// pw.whitelistCommandLine(".*--allow.*");    // opt-in: matches the (spoofable) command line

pw.registerSuspiciousProcessHandler(event -> {
    // an unknown, non-whitelisted process appeared after the fingerprinting period
    System.err.println("IDS signal: " + event.getDefconEvent() + " - " + event.getProcess());
});
pw.registerProcessStartedHandler(event -> { /* every observed process start */ });
pw.registerProcessTerminatedHandler(event -> { /* every observed process termination */ });

pw.start();
```

A process fingerprint is `user|command` - a known executable started by a different user is
not considered known (and is graded DEFCON3). The suspicious event grading is a naive DEFCON
mapping: probing tools (nc, nmap, socat, ...) running privileged map to DEFCON1, unknown
privileged processes or probing tools to DEFCON2, known commands under new users to DEFCON3
and other unknown processes to DEFCON4.

Three escalation heuristics raise a grading (each one level, floored at DEFCON1):

* **Listening probes**: on Linux, a suspicious process holding a listening TCP socket
  (detected through `/proc/net/tcp` and `/proc/<pid>/fd`) is escalated immediately, and the
  event exposes the ports through `DefconEvent.getListeningPorts()`.
* **Scanners and sniffers**: a suspicious process holding a raw IP socket (`/proc/net/raw`)
  or an AF_PACKET capture socket (`/proc/net/packet`) - the footprint of packet sniffers,
  custom port scanners and covert channels - is escalated immediately
  (`DefconEvent.hasRawSocket()` / `hasPacketSocket()`).
* **Long-lived intruders**: a suspicious process still alive after the configured escalation
  delay is re-reported with an escalated grading (`DefconEvent.isEscalated()`).

When a fingerprint baseline file is configured, the learned baseline is persisted once the
fingerprinting period ends (and on `stop()`). If the file exists at `start()` the baseline
is restored and the watcher is armed immediately - a restarted service does not need a new
learning period, during which an intruder would have been learned as normal.

Requires Java 17+. Process discovery uses the JDK `ProcessHandle` API by default, with a
`ps` based fallback scanner mode - no native libraries or external dependencies are needed.

### Security limitations

ProcessWatcher is a naive, best-effort threat notifier, not a hardened IDS. Known limits to
plan around:

* **Trust on first use**: the fingerprinting period learns whatever is running. An intruder
  already resident when learning starts is learned as normal and never reported. Configure a
  baseline file (above) and manage it so learning happens on a known-clean host.
* **Privilege is required for socket signals**: reading another process' sockets needs root or
  `CAP_NET_RAW`/`CAP_SYS_PTRACE`. Running as an unprivileged service user, listening-probe,
  raw-socket and sniffer escalation only apply to the watcher's own user. `start()` logs a
  warning and `isSocketEscalationAvailable()` reports this.
* **Polling has a blind spot**: a process that starts and exits within one scan interval may
  never be observed. Lower `setProcessScanInterval` to narrow the window; it cannot be closed
  by polling alone.
* **The DEFCON grading is a coarse indication**, including the privilege heuristic (root by
  name/uid, or an unresolved user holding a socket). Do not treat the level as authoritative.
* **The baseline file is trusted input**: anyone who can write it can self-whitelist a
  fingerprint. Store it with restrictive permissions (e.g. `0600`, owned by the service user).
* **Poll (`ps`) mode is best-effort**: phantom-pid injection is filtered by reconciling against
  live pids, but the native scanner is the default and more robust.

### Rationale

We belive that there might be tremendous value in making software services more aware of the threats that surround its running process both in developer awareness (i.e. seeing is believing - borderline security died in the last millenium) and to enable the developers and serices to make distinct actions when their environment gets infiltrated/attacked/breached (i.e. prevent data leakages, protect customer/user data et all). 

This library is intended to implement support to explore these ideas. 

### Status

The core two-phase flow (fingerprinting, then eventing with naive DEFCON grading) is implemented
and covered by tests. Feedback and collaboration is still very much wanted :)


#### A simple diagram showing the two phases and the event flows

![ProcessWatcher event flow](https://raw.githubusercontent.com/Cantara/ProcessWatcher/master/docs/ProcessWatcherPhasesSequences.png)
