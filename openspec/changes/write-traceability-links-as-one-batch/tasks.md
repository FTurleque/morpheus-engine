# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Measure before changing

- [x] 1.1 Count the statements `putLinks` prepares for a batch of new links and for a batch of links already defined.
      Proof: the two counts per link; on 2026-10-13, read from the code, four and three.
      *2026-10-13: counted, not read. With the per-link write, a batch of 3 new links prepares 13 statements and one of 650 prepares 2,601 - four per link plus one. A batch of 250 links already defined prepares 751, three per link plus one.*
- [ ] 1.2 List the tests that call `putLinks`, what each asserts, and the production callers. Proof: the list; on
      2026-10-13, three test calls, none on a refusal, and one caller (`ProjectSnapshotImportService.java:129`).
      Re-measure rather than trust either number.
      *2026-10-13: not re-listed.*
- [ ] 1.3 `M19FullPublishPerformanceGate` on `HEAD`, on Windows and Linux, and the share of the publication spent in
      `putLinks`. Proof: `full_publish_p95_ms` per platform, and the share, from a timing around the call.
      *2026-10-13: Windows only, one machine (16 processors, JDK 24.0.1, heap of 768 MiB), the gate run alone: `full_publish_p95_ms=5868` with the per-link write, against a budget of 60,000. The budget was never in danger. The share spent in `putLinks` was not timed.*

## 2. Reproduce before fixing

- [x] 2.1 The four scenarios of the first requirement, against both stores. Proof: green on SQLite; **red on the memory
      store** for the collision in the middle, which stores the links before it.
      *2026-10-13: `verifyBatchRules` in `TraceabilityPersistenceContractTest`, run against both stores - all or nothing, a link already defined joins the snapshot, a link listed twice is stored once, a collision in the store or within the batch refuses it and names the identity. Green on SQLite and **red on the memory store** before the change, as predicted: it kept the links before the one that refused.*
- [x] 2.2 The count test of decision 5. Proof: red on `HEAD`, stating both counts.
      *2026-10-13: `SqliteTraceabilityBatchWriteCostTest`. The store had no seam for a counting connection, so the test could not exist before the change; it was proven afterwards by restoring the per-link write (13 against 2,601).*

## 3. The change

- [x] 3.1 The memory store validates a batch before storing it; the port's default is removed or kept with its reason
      (decision 4). Proof: 2.1 passes on both stores.
      *2026-10-13: the memory store validates the whole batch before storing any of it. The port's `putLinks` is no longer a default: storing the links one by one is not the contract, and the one test double that relied on the default now states what it does.*
- [x] 3.2 Statements prepared once, executed in slices, in the one transaction. Proof: 2.2 passes; 2.1 passes
      unmodified.
      *2026-10-13: three statements prepared once and run in slices of 500 rows, in the one transaction.*
- [x] 3.3 The existence check of decision 3. Proof: the collision scenario still names the identity; a link that
      exists identically is accepted with its evidence compared.
      *2026-10-13: the definitions and evidence already present are read for the whole batch with two statements, each prepared once with a fixed number of placeholders and run per slice of 200 identities. A link that exists is compared field for field, evidence included.*
- [x] 3.4 Break each proof once: restore a statement per link (2.2 red), store before validating (2.1 red). Reverted.
      *2026-10-13: the per-link write restored once - the count test fails with both counts; reverted. 'Store before validating' is the state the memory store was in when 2.1 was red.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests, the publication recovery contract tests of the `m19` package and the
      traceability contract tests. Proof: green unmodified.
- [ ] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof:
      `full_publish_p95_ms` before and after, per platform; the size and retention budgets are unchanged, since the
      rows written are the same.
      *2026-10-13: Windows only, the gate run alone: `full_publish_p95_ms=4031` and, on an earlier run the same day, 5120, after the change, against 5868 before. **The gain is within the spread between two runs of the same code**; what the change is worth is the count, and the time the database is held was not measured.*
- [ ] 4.4 Pull request description: the finding, the counts of 1.1 before and after, the times of 1.3 and 4.3, and
      the gain stated as measured - including if it is within the noise.
