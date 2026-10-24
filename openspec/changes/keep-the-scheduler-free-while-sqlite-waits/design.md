# Design

## Context

- JEP 444 (Java 21): a virtual thread is pinned to its carrier while it runs inside a `synchronized` block or method,
  and while it runs a native method or a foreign function. JEP 491 (Java 24) removes the first case, not the second.
- `sqlite-jdbc` calls SQLite through JNI. The busy handler sleeps and retries inside the native call.
- The scheduler's parallelism defaults to the number of processors and is set by
  `jdk.virtualThreadScheduler.parallelism` / `maxPoolSize`.
- The packaged product embeds a Java 21 runtime; the baseline is asserted (ADR-0133, the Enforcer, the workflows).
- `-Djdk.tracePinnedThreads` and the JFR event `jdk.VirtualThreadPinned` report pinning without changing the code.
- The health and readiness routes are served by the same executor as everything else.

## Goals / Non-Goals

**Goals**: know whether the scheduler starves, and when; choose one remedy with that knowledge; hold the result by a
test that fails when the server stops answering.

**Non-Goals**: admission control of the local server (`admit-local-requests-before-they-meet-the-database`, which
depends on this); the connection policy (`own-the-sqlite-connection-policy-in-one-place`); the journal mode - it is a
security decision recorded elsewhere, and it is read here, not reopened.

## Decisions

1. **Measure before choosing.** A harness holds the database with a writer, sends N concurrent reads and a stream of
   health requests, and records pinned events, carriers in use and the latency of the health requests. On Linux and on
   Windows, with the processor count stated. If the health requests are not delayed, the finding is recorded as not
   reproduced and the change stops at the test.

2. **The remedy is chosen by an ADR from that measurement.** The options, with what each can and cannot do:
   - *Replace the monitors with locks.* Cannot unpin a thread that waits inside native code. Useful only if the
     measurement shows time spent blocked on monitors, which per-request stores make unlikely.
   - *Size the scheduler.* A larger `maxPoolSize` bounds the damage and costs platform threads; it documents the limit
     rather than removing it.
   - *A newer JDK.* Lifts monitor pinning, not native pinning; moves the baseline, the Enforcer, CI and the packaged
     runtime.
   - *Run database work on a bounded pool of platform threads.* The request's virtual thread waits on a future and
     yields its carrier; the pool's size is the database's concurrency, stated in one place. It is the only option that
     addresses native pinning, and it couples with the connection policy: the scope is thread-confined.
   The specification does not pre-empt the measurement; the last option is where the reading of the code points.

3. **Count before replacing.** Whatever is chosen, the lot first counts the `synchronized` methods reachable from a
   route handler, and says what each monitor protects given that stores are per request. A monitor that protects
   nothing is removed with its reason; one that protects something keeps a test.

4. **The outcome is a saturation test**, kept in the suite: a writer holds the database for longer than the busy
   timeout, reads are in flight, and a health request is answered within a stated bound. It is a real-server test, not
   a unit test of a lock.

5. **An ADR**, mandatory: the decision touches the concurrency model of both servers.

## Risks / Trade-offs

- A saturation test is timing-sensitive. Its bound is generous, its failure message states what it measured, and it
  runs on both platforms before it is trusted.
- A platform-thread pool for database work changes where the connection scope lives (it is a `ThreadLocal`). That is
  the junction with the connection policy change and must be designed with it, not after.
- Moving the baseline is the most expensive option and the one that fixes least here.
