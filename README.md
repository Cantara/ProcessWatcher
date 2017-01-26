# ProcessWatcher

ProcessWatcher is a java library for watching, fingerprinting and eventing of suspicious processes (A simplified process Intrusion Detection System/IDS threat notifier)

This library will record/fingerprint the system for a configurable period (i.e. 20 minutes for servers with no logrotate or daily cronjobs or 36 hours for systems with daily jobs++). You can also whilelist processes in the configuration.

If unknown processes are discovered after the fingerprinting period which is not fingerprinted or whitelisted, the library will trigger events which the application can intrepret as IDS signals. We will attempt to do a naive DEFCON threat-level mapping of the events but this categorization should only be handled as a simple indication (and thus not trusted) in application reactions.

The main use-case here is to try to detect "listening probes" so that the application/service can act accordingly. This approach will not try to address agressive intrusions.

### Rationale

We belive that there might be tremendous value in making software services more aware of the threats that surround its running process both in developer awareness (i.e. seeing is believing - borderline security died in the last millenium) and to enable the developers and serices to make distinct actions when their environment gets infiltrated/attacked/breached (i.e. prevent data leakages, protect customer/user data et all). 

This library is intended to implement support to explore these ideas. 

### Status

The current status of this library is:  WORK IN PROGRESSS - COLLABORATION WANTED :)


#### A simple diagram showing the two phases and the event flows

![ProcessWatcher event flow](https://raw.githubusercontent.com/Cantara/ProcessWatcher/master/docs/ProcessWatcherPhasesSequences.png)
