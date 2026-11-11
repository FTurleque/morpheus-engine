# Proposal

## Why

AUD-PRF-03 (sprint 5) reports that the local HTTP server has no admission control. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding
**holds**:

- `MorpheusLocalHttpServerBootstrap.java:165` runs every request on a new virtual thread; the file holds no semaphore.
- Every operation opens a physical connection (`SqliteConnectionScope.open` in `ApiRuntime`, built at 32 sites).
- `SqliteDatabaseSecurity` sets a busy timeout of five seconds and the `PERSIST` journal mode: a writer takes an
  exclusive lock on the whole database and blocks the readers.
- The remote server holds four semaphores (`MorpheusRemoteHttpServer.java:54-57`): authentication, requests, privileged
  requests, observability. The local server holds none, and the asymmetry is written nowhere.
- The risk is registered as RT-01, with observability (`SqliteContentionMetrics`) and no control.
- The local OpenAPI file declares no `429`.

Without a ceiling, N simultaneous local requests open N connections that contend for one database and fail with
`SQLITE_BUSY` after five seconds: a peak becomes failures instead of a queue. The audit lowered the severity to Medium -
a loopback server for one user - and the gap with the remote server remains.

## What Changes

- The local server admits a bounded number of requests at once; beyond it a request waits for a bounded time, and is
  then refused by name.
- The routes that tell whether the server is alive are admitted apart, so a peak does not make the server look dead.
- RT-01 records the decision.

## Capabilities

### New Capabilities

- `local-admission-control`: a peak of local requests queues, then is refused by name; it does not turn into database
  contention failures.

### Modified Capabilities

(none)

## Impact

- `morpheus-api` (the local bootstrap and server), `docs/developer/reference/openapi/morpheus-v1*.yaml` (a `429`),
  `contracts/public-surfaces.tsv` if a line's notes mention the status, the risk register, the operability page.
- Last of sprint 5's database changes: after `keep-the-scheduler-free-while-sqlite-waits` and
  `own-the-sqlite-connection-policy-in-one-place`, whose outcomes size the ceiling.
