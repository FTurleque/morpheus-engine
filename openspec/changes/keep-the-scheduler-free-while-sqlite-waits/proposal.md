# Proposal

## Why

AUD-PRF-18 (sprint 5, High) reports that SQLite work pins the carrier threads of the virtual-thread scheduler. Re-read on
`develop` at `bd2294b6` on 2026-10-13, the structure the finding describes **holds**, and its proposed remedy needs a correction:

- 94 `public synchronized` methods in 13 files of `morpheus-store-sqlite`, all doing blocking JDBC; 195 occurrences of
  `synchronized` in the product's sources.
- Both HTTP servers run every request on a virtual thread: `MorpheusLocalHttpServerBootstrap.java:165` and
  `MorpheusRemoteHttpServerBootstrap.java:109`, `Executors.newVirtualThreadPerTaskExecutor()`.
- The baseline is Java 21 (`pom.xml:39`), where a virtual thread that blocks while holding a monitor stays mounted on
  its carrier. The carrier pool is sized to the number of processors.
- `SqliteDatabaseSecurity` sets a busy timeout of five seconds on a rollback journal (`PERSIST`): a reader that meets a
  writer waits, up to that long, inside the database call.

**The correction.** The audit's first option is to replace the monitors with `ReentrantLock`. But the SQLite driver runs
in native code, and on Java 21 a virtual thread inside a native frame is pinned whether or not it holds a monitor. The
wait on a busy database happens inside the native call. Removing the monitors would therefore leave the carrier pinned
for exactly the wait the finding is about. Two further facts weaken the monitors' role: each request builds its own
stores (`new ApiRuntime(` at 32 sites), so a store's monitor is not contended between requests - what it protects has
to be established before it is replaced.

None of this was observed running: the audit reasons on documented semantics, and so does this re-reading. The cost is
real if it exists - requests that never touch SQLite wait for a carrier, so the whole server stops answering, health
included - and it has to be measured first.

## What Changes

- The pinning is measured: how many carriers a busy database holds, for how long, and whether a request that touches no
  database is delayed.
- One remedy is chosen by a decision record from that measurement, among: bounding or enlarging the scheduler, running
  database work on a bounded pool of platform threads, moving the baseline to a JDK that lifts monitor pinning, or
  replacing monitors.
- A saturation test holds the outcome: while a writer holds the database, a request that needs no database is answered.

## Capabilities

### New Capabilities

- `scheduler-availability`: a database that makes its callers wait does not stop the server from answering what does
  not need it.

### Modified Capabilities

(none)

## Impact

- `morpheus-store-sqlite` (the 13 stores, the transaction runner), `morpheus-api` (both bootstraps), possibly the
  baseline (`pom.xml`, CI, the packaged runtime) if the decision is a newer JDK.
- An ADR. The M19 budgets, which measure the store.
- First of sprint 5, with `own-the-sqlite-connection-policy-in-one-place`; both precede
  `admit-local-requests-before-they-meet-the-database`.
