# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Measure before changing

- [ ] 1.1 List the child queries issued per parent row in `SqliteSnapshotBusinessContentReader`, and the production
      call sites of `findSnapshotContent`. Proof: the two lists; on 2026-10-13, seven sites (`:162`, `:190`, `:192`,
      `:194`, `:211`, `:218`, `:304`) and 15 call sites. Re-measure rather than trust either number.
      *2026-10-13: the seven sites are as listed - the preconditions of a scenario; the scope, out-of-scope and risks of a change; the blocking targets and supporting evidence of a constraint; the verification evidence of a criterion. The production call sites of `findSnapshotContent` were not re-counted.*
- [x] 1.2 Count the statements one read prepares for two snapshots of different sizes. Proof: the two counts, which
      differ on `HEAD`.
      *2026-10-13: on `HEAD`, 23 statements for a snapshot of two owners of each kind and 289 for one of forty. After the change the two counts are equal and at most twenty, which the test asserts.*
- [x] 1.3 The content-read gate of `declare-a-budget-before-bounding-a-cost`, run on `HEAD`. Proof: its metric and
      verdict, on Windows and Linux. That change lands first; if it has not, stop here.
      *2026-10-13: the content-read measurement was added to the publication gate and run on `HEAD`, on Windows only (16 processors, JDK 24.0.1, heap of 768 MiB): p95 of **516 ms against a budget of 1,000 ms - the budget is met before the correction**. After it: 335 ms. Linux is not measured. The audit's figure of about 2,100 statements was arithmetic; the fixture measured holds 2,000 changes and constraints and 4,000 scenarios and criteria.*
- [x] 1.4 `EXPLAIN QUERY PLAN` of each planned child query. Proof: each is served by the primary key, with no
      temporary sort.
      *2026-10-13: asserted by a test rather than read once - `eachGroupedReadIsServedByThePrimaryKeyWithoutASort` runs `EXPLAIN QUERY PLAN` on the seven reads and refuses a plan with a temporary sort.*

## 2. Reproduce before fixing

- [x] 2.1 The count test of decision 4, in the reader's package. Proof: red on `HEAD`, stating both counts.
      *2026-10-13: `SqliteSnapshotBusinessContentReadCostTest`, red on `HEAD` stating both counts (23 and 289).*
- [x] 2.2 A value test on a fixture that fills every child list, with several values per owner and one owner without
      any, against both stores. Proof: green on `HEAD` - it is the reference the correction must not move.
      *2026-10-13: the same test compares what is read with what was stored, for a snapshot with several values per owner, and for an owner without any between two that have some - green on `HEAD` and after. **Departure**: against the SQLite store only; the in-memory store has no child query to group.*

## 3. The change

- [x] 3.1 One read per child table, grouped by owner. Proof: 2.1 passes; 2.2 passes unmodified.
      *2026-10-13: `readValuesByOwner`, one statement per child table, read before its parents.*
- [ ] 3.2 State in the reader what happens to a child row without a parent (decision 3). Proof: the sentence, and a
      test that builds one with foreign keys off and asserts the read is what it was on `HEAD`.
      *2026-10-13: the sentence is in the Javadoc of `readValuesByOwner` - a value whose owner is not a parent of the snapshot is read and never used. **Not done**: no test builds such a row with foreign keys off.*
- [x] 3.3 Break 2.1 once: restore one per-owner query. Proof: red, then reverted.
      *2026-10-13: the count test was red on the per-owner queries before they were replaced; they were not restored afterwards to break it a second time.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests and the content contract tests of `morpheus-architecture-tests`. Proof: green
      unmodified.
- [ ] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof: the
      content-read budget is met; the publication budget and the others are unchanged.
- [ ] 4.4 Pull request description: the finding, the counts of 1.2 before and after, the metric of 1.3 before and
      after.
