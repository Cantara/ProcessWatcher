# ProcessWatcher

ProcessWatcher is a java library for watching, fingerprinting and eventing of suspicious processes (A simplified process Intrusion Detection System/IDS threat notifier)

This library will record/fingerprint the system for a configurable period (i.e. 20 minutes for servers with no logrotate or daily cronjobs or 36 hours for systems with daily jobs++). You can also whitelist processes in the configuration.

If unknown processes are discovered after the fingerprinting period which is not fingerprinted or whitelisted, the library will trigger events which the application can intrepret as IDS signals. We will attempt to do a naive DEFCON threat-level mapping of the events but this categorization should only be handled as a simple indication (and thus not trusted) in application reactions.

The main use-case here is to try to detect "listening probes" so that the application/service can act accordingly. This approach will not try to address agressive intrusions.

### Usage

```java
ProcessWatcher pw = ProcessWatcher.getInstance();
pw.setFingerprintingPeriod(20 * 60 * 1000);   // learn the process baseline for 20 minutes
pw.setProcessScanInterval(5000);              // scan the process table every 5 seconds
pw.whitelist(".*logrotate.*");                // regex matched against command and command line

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

Requires Java 17+. Process discovery uses the JDK `ProcessHandle` API by default, with a
`ps` based fallback scanner mode - no native libraries or external dependencies are needed.

### Rationale

We belive that there might be tremendous value in making software services more aware of the threats that surround its running process both in developer awareness (i.e. seeing is believing - borderline security died in the last millenium) and to enable the developers and serices to make distinct actions when their environment gets infiltrated/attacked/breached (i.e. prevent data leakages, protect customer/user data et all). 

This library is intended to implement support to explore these ideas. 

### Status

The core two-phase flow (fingerprinting, then eventing with naive DEFCON grading) is implemented
and covered by tests. Feedback and collaboration is still very much wanted :)


#### A simple diagram showing the two phases and the event flows

![ProcessWatcher event flow](https://raw.githubusercontent.com/Cantara/ProcessWatcher/master/docs/ProcessWatcherPhasesSequences.png)
