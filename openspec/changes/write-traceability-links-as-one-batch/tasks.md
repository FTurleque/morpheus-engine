# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Count the statements `putLinks` prepares for a batch of new links and for a batch of links already defined.
      Proof: the two counts per link; on 2026-10-13, read from the code, four and three.
- [ ] 1.2 List the tests that call `putLinks`, what each asserts, and the production callers. Proof: the list; on
      2026-10-13, three test calls, none on a refusal, and one caller (`ProjectSnapshotImportService.java:129`).
      Re-measure rather than trust either number.
- [ ] 1.3 `M19FullPublishPerformanceGate` on `HEAD`, on Windows and Linux, and the share of the publication spent in
      `putLinks`. Proof: `full_publish_p95_ms` per platform, and the share, from a timing around the call.

## 2. Reproduce before fixing

- [ ] 2.1 The four scenarios of the first requirement, against both stores. Proof: green on SQLite; **red on the memory
      store** for the collision in the middle, which stores the links before it.
- [ ] 2.2 The count test of decision 5. Proof: red on `HEAD`, stating both counts.

## 3. The change

- [ ] 3.1 The memory store validates a batch before storing it; the port's default is removed or kept with its reason
      (decision 4). Proof: 2.1 passes on both stores.
- [ ] 3.2 Statements prepared once, executed in slices, in the one transaction. Proof: 2.2 passes; 2.1 passes
      unmodified.
- [ ] 3.3 The existence check of decision 3. Proof: the collision scenario still names the identity; a link that
      exists identically is accepted with its evidence compared.
- [ ] 3.4 Break each proof once: restore a statement per link (2.2 red), store before validating (2.1 red). Reverted.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests, the publication recovery contract tests of the `m19` package and the
      traceability contract tests. Proof: green unmodified.
- [ ] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof:
      `full_publish_p95_ms` before and after, per platform; the size and retention budgets are unchanged, since the
      rows written are the same.
- [ ] 4.4 Pull request description: the finding, the counts of 1.1 before and after, the times of 1.3 and 4.3, and
      the gain stated as measured - including if it is within the noise.
