# ProcessWatcher
A java library for watching, fingerprinting and eventing of processes (Process IDS watcher)

This library will record/fingerprint the system for a configurable period (i.e. 20 minutes for servers with no logrotate or daily cronjobs or 36 hours for systems with daily jobs++). You can also whilelist processes in the configuration.

If unknown processes are discovered after the fingerprinting period which is not fingerprinted or whitelisted, the library will trigger events which the application can intrepret as IDS signals. We will attempt to do a naive DEFCON threat-level mapping of the events but this categorization should only be handled as a simple indication (and thus not trusted) in application reactions.

The main use-case here is to try to detect "listening probes" so that the application/service can act accordingly. This approach will not try to address agressive intrusions.


### A simple diagram showing the two phases and the event flows

(https://raw.githubusercontent.com/Cantara/ProcessWatcher/master/docs/ProcessWatcherPhasesSequences.png)