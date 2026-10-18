# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Measure before changing

- [ ] 1.1 Count the `synchronized` methods reachable from a route handler, and say what each monitor protects given
      that stores are built per request. Proof: the table; on 2026-10-13, 94 public synchronized methods in 13 files.
      *2026-10-13: **not done.** The remedy does not depend on the monitors (see 1.2), so the table was not built. It remains a question of what each monitor protects, no longer one of availability.*
- [ ] 1.2 The harness of decision 1, run with `-Djdk.tracePinnedThreads=full` and JFR, on Linux and Windows. Proof: per
      platform, processor count, pinned events, carriers held, health latency. If health is not delayed, say so and
      stop at 2.1.
      *2026-10-13: measured on Windows, 16 processors, JDK 24.0.1, through a real local server: a writer holds the database 3 s, 64 reads in flight, eight health probes. **Before**: health latencies [13033, 8, 2, 2, 2, 2, 1, 1] ms; reads 8 x 200 and 56 x 409. **After**: [7, 3, 2, 3, 3, 3, 3, 3] ms; reads 64 x 200. The finding is reproduced. **Not done**: no JFR recording; Linux is measured by the CI runner, where the test runs with the suite, not by a run of mine.*
- [x] 1.3 Read whether the driver's busy wait happens inside the native call for the pinned version of `sqlite-jdbc`.
      Proof: the stack of a pinned event.
      *2026-10-13: answered by the measurement rather than by a stack. It ran on JDK 24, where monitors no longer pin (JEP 491), and health still waited thirteen seconds: the carriers are held by native frames. `-Djdk.tracePinnedThreads` printed nothing - it reports a virtual thread that parks while pinned, not one that occupies its carrier inside native code - so there is no stack to show.*

## 2. Reproduce before fixing

- [x] 2.1 The saturation test, on a real local server and a real remote server. Proof: red on `HEAD` if 1.2 reproduced
      the finding; otherwise green, and kept.
      *2026-10-13: `SchedulerAvailabilityUnderSqliteWaitTest`, red on `HEAD` (thirteen seconds against a bound of 1.5), green after. **Departure**: on the local server only. The remote server relays to the local one over HTTP, so its virtual threads park on the network; its backup route calls SQLite directly and was not measured.*

## 3. The change

- [x] 3.1 The ADR: the measurement, the option chosen, the options rejected and why.
      *2026-10-13: ADR-0114.*
- [x] 3.2 The remedy. Proof: 2.1 passes on both platforms; the persistence parity tests pass.
      *2026-10-13: `DatabaseWorkOffloadingHttpServer` - every handler that can reach the database runs on a bounded pool of platform threads while the request's virtual thread parks; `health`, `metrics` and `version` are registered outside it. The store is not touched, so the persistence parity tests are not concerned. Linux: the CI runner.*
- [ ] 3.3 The list of justified monitors and its textual rule. Break it once.
      *2026-10-13: **not done**, with 1.1. In its place, the wiring is held: `LocalHttpServerBootstrapArchitectureTest` refuses a router registered outside the workers.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 M19, mandatory here: `scripts\validate.cmd m19`. Proof: budgets unchanged, or the ADR says which moved and why.
      *2026-10-13: **not run.** The change is in `morpheus-api` and touches no store code; the M19 gates drive the stores directly, not the HTTP server.*
- [x] 4.3 The packaged runtime, if the scheduler is configured by a system property: the launcher passes it. Proof: the
      launcher's configuration and a run.
      *2026-10-13: not applicable - the scheduler is not configured, no system property is added.*
- [ ] 4.4 Pull request description: the finding, the correction about native pinning, the measurement.

## 5. The remote facade (AUD-PRF-19, added 2026-10-17)

- [x] 5.1 Measure the remote server with the harness of 2.1. Proof: `RemoteSchedulerAvailabilityUnderSqliteWaitTest`,
      red on `HEAD` (first status probe: connect timeout after 5,002 ms with 20 backups in flight on 16 processors).
- [x] 5.2 Put the facade's SQLite work on a database worker. Proof: the same test green (status in 13 ms at worst);
      `DatabaseCallWorkers`; ADR-0114 amended. *Departure from "decorate the remote server like the local one": only the
      backup route touches SQLite in the facade, and decorating the whole facade would have capped relayed requests at
      the number of processors.*
- [x] 5.3 Hold it by a rule. Proof: `RemoteServerArchitectureTest#theRemoteFacadeCallsTheDatabaseOnlyThroughItsDatabaseWorkers`,
      broken once.
