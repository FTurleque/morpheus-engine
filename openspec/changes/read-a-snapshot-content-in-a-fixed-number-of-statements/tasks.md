# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List the child queries issued per parent row in `SqliteSnapshotBusinessContentReader`, and the production
      call sites of `findSnapshotContent`. Proof: the two lists; on 2026-10-13, seven sites (`:162`, `:190`, `:192`,
      `:194`, `:211`, `:218`, `:304`) and 15 call sites. Re-measure rather than trust either number.
- [ ] 1.2 Count the statements one read prepares for two snapshots of different sizes. Proof: the two counts, which
      differ on `HEAD`.
- [ ] 1.3 The content-read gate of `declare-a-budget-before-bounding-a-cost`, run on `HEAD`. Proof: its metric and
      verdict, on Windows and Linux. That change lands first; if it has not, stop here.
- [ ] 1.4 `EXPLAIN QUERY PLAN` of each planned child query. Proof: each is served by the primary key, with no
      temporary sort.

## 2. Reproduce before fixing

- [ ] 2.1 The count test of decision 4, in the reader's package. Proof: red on `HEAD`, stating both counts.
- [ ] 2.2 A value test on a fixture that fills every child list, with several values per owner and one owner without
      any, against both stores. Proof: green on `HEAD` - it is the reference the correction must not move.

## 3. The change

- [ ] 3.1 One read per child table, grouped by owner. Proof: 2.1 passes; 2.2 passes unmodified.
- [ ] 3.2 State in the reader what happens to a child row without a parent (decision 3). Proof: the sentence, and a
      test that builds one with foreign keys off and asserts the read is what it was on `HEAD`.
- [ ] 3.3 Break 2.1 once: restore one per-owner query. Proof: red, then reverted.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests and the content contract tests of `morpheus-architecture-tests`. Proof: green
      unmodified.
- [ ] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof: the
      content-read budget is met; the publication budget and the others are unchanged.
- [ ] 4.4 Pull request description: the finding, the counts of 1.2 before and after, the metric of 1.3 before and
      after.
