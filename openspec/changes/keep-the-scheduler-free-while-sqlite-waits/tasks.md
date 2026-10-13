# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Count the `synchronized` methods reachable from a route handler, and say what each monitor protects given
      that stores are built per request. Proof: the table; on 2026-10-13, 94 public synchronized methods in 13 files.
- [ ] 1.2 The harness of decision 1, run with `-Djdk.tracePinnedThreads=full` and JFR, on Linux and Windows. Proof: per
      platform, processor count, pinned events, carriers held, health latency. If health is not delayed, say so and
      stop at 2.1.
- [ ] 1.3 Read whether the driver's busy wait happens inside the native call for the pinned version of `sqlite-jdbc`.
      Proof: the stack of a pinned event.

## 2. Reproduce before fixing

- [ ] 2.1 The saturation test, on a real local server and a real remote server. Proof: red on `HEAD` if 1.2 reproduced
      the finding; otherwise green, and kept.

## 3. The change

- [ ] 3.1 The ADR: the measurement, the option chosen, the options rejected and why.
- [ ] 3.2 The remedy. Proof: 2.1 passes on both platforms; the persistence parity tests pass.
- [ ] 3.3 The list of justified monitors and its textual rule. Break it once.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 M19, mandatory here: `scripts\validate.cmd m19`. Proof: budgets unchanged, or the ADR says which moved and why.
- [ ] 4.3 The packaged runtime, if the scheduler is configured by a system property: the launcher passes it. Proof: the
      launcher's configuration and a run.
- [ ] 4.4 Pull request description: the finding, the correction about native pinning, the measurement.
