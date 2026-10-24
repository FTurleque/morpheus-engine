# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Measure before changing

- [ ] 1.1 With the harness of `keep-the-scheduler-free-while-sqlite-waits`: at which concurrency do local requests begin
      to fail with a busy failure, with and without a writer? Proof: the curve; it sizes the ceiling.
      *2026-10-13: one point of the curve, not the curve. With the harness of the scheduler change, 16 processors, a writer holding the database 3 s: 64 simultaneous reads without a ceiling gave 56 busy failures; with 16 at a time, none. The concurrency at which failures begin was not searched for.*
- [ ] 1.2 Read the four ceilings of the remote facade and how each is derived. Proof: the lines.
- [x] 1.3 Read which local operations declare which statuses in the OpenAPI files. Proof: the list of operations that
      gain `429`.
      *2026-10-13: the local OpenAPI file declares error statuses on one operation only (400 and 415). No operation lists the statuses it can answer, so none gains 429: the contract does not move.*

## 2. Reproduce before fixing

- [x] 2.1 A burst beyond the ceiling on a real local server: all answered. A request that waits past the bound: `429`,
      named, with `Retry-After`. Proof: red on `HEAD` - the first shows busy failures, the second has no bound.
      *2026-10-13: `LocalAdmissionTest`, through a real HTTP server decorated with two workers: a burst of 24 is answered in full with never more than two inside; a request that waits its bound is refused 429, named, with `Retry-After` and the headers of every local response. The busy failures of a burst on `HEAD` are the measurement of 1.1. **Departure**: the test drives the decorator with handlers of its own, not the product's routes.*
- [x] 2.2 Health under a saturated ceiling. Proof: red or green on `HEAD`, stated.
      *2026-10-13: `SchedulerAvailabilityUnderSqliteWaitTest` - red on the `HEAD` of the scheduler change, green since. `health`, `metrics` and `version` take no place; `readiness` asks the database and does, which departs from decision 3.*
- [ ] 2.3 The comparison of the two ceilings in remote mode.
      *2026-10-13: **not done, and decision 4 is not applied.** The facade's ceiling is an operator setting; matching it locally would bring back the contention the measurement showed. A request the facade admitted can wait for a worker, within the same bound. Recorded in ADR-0114.*

## 3. The change

- [x] 3.1 The admission, the bound, the refusal. Proof: 2.1 to 2.3 pass.
      *2026-10-13: a fair semaphore of one permit per worker in `DatabaseWorkOffloadingHttpServer`, a wait of ten seconds, then `429 LOCAL_SERVER_BUSY`. **Not done**: refusals are not counted.*
- [ ] 3.2 The OpenAPI files; `PublicHttpRouteConvergenceTest` green. The risk register and the operability page.
      *2026-10-13: no OpenAPI change (1.3). The operability page and ADR-0114. The risk register is not updated.*
- [x] 3.3 Break once, then revert: remove the permit release on a failed request. Proof: a test that the ceiling is not
      leaked fails.
      *2026-10-13: with the release of the place removed, the three tests of `LocalAdmissionTest` fail; reverted.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The remote load profile the repository already has, before and after. Proof: the two profiles.
- [ ] 4.3 Release note; pull request description: the finding, the curve of 1.1, the contract change and its reason.
