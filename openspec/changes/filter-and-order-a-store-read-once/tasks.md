# Tasks

Implementation is a separate lot, started after this specification is reviewed and after the sprint 6 changes for
`AUD-PRF-02` and `AUD-PRF-04` are merged. Nothing below is done by the pull request that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-read `SqliteTraceabilityStore.endpointQuery` and `SqliteSyncStateStore.listArchives` as sprint 6 left
      them. Proof: the statement texts and whether each finding still holds; a half that sprint 6 closed is dropped
      from this lot and said so in the pull request.
- [ ] 1.2 Run `EXPLAIN QUERY PLAN` **through `sqlite-jdbc`** on the endpoint statement, outgoing and incoming, with no
      relation clause and with a clause of one, several and every relation type. Proof: the eight plans. The plans of
      the proposal were observed with another SQLite build and are not this proof.
- [ ] 1.3 List the production callers of `listArchives` and how `archived_at` is written. Proof: the list (none on
      2026-10-13) and the write site; if sprint 6 made the column sort chronologically, the requirement on the order is
      met by SQL, decision 4 is reversed for that store and the textual assertion is not written.
- [ ] 1.4 Count the constants of `TraceabilityRelationType` from the source. Proof: the bound on the placeholder list,
      read, not copied from this change.

## 2. Reproduce before fixing

- [ ] 2.1 The parity test of the endpoint reads: both stores, both directions, empty set, one type, several, every
      type, an absent type, a link of another snapshot. Proof: green on `HEAD` - it pins the behaviour.
- [ ] 2.2 The plan test of the filtered statement. Proof: red on `HEAD`, the search names no `relation_type`.
- [ ] 2.3 The archive order test with the three timestamps and with shared instants, on both stores. Proof: green on
      `HEAD` thanks to the Java sort; it turns red if that sort is removed instead of the clause (shown once in 3.4).
- [ ] 2.4 The textual assertion on the archive listing statement, scoped to that statement. Proof: red on `HEAD`.

## 3. The change

- [ ] 3.1 The relation clause with bound parameters in a fixed order; the Java filter removed. Proof: 2.1 unchanged and
      green, 2.2 green.
- [ ] 3.2 The `ORDER BY` removed from `listArchives`, the reason written above the method. Proof: 2.3 and 2.4 green.
- [ ] 3.3 If `serve-ordered-store-reads-from-an-index` is merged, register the filtered statement in its plan guard and
      delete the test of 2.2. Proof: one guard, not two.
- [ ] 3.4 Break each rule once, then revert: drop the relation clause (2.2 fails); remove the Java sort and restore the
      SQL order (2.3 fails on the three timestamps); put the `ORDER BY` back (2.4 fails).

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; the changed-line coverage gate. Proof: green; no ratchet is touched.
- [ ] 4.2 `TraceabilityPersistenceContractTest`, `TraceabilityTraversalContractTest`,
      `TraceabilityTruncationContractTest` and `IncrementalSyncPersistenceContractTest`. Proof: green unmodified.
- [ ] 4.3 M19: `scripts\validate.cmd m19` and its `.sh` twin. Proof: the traceability budget holds; figures before and
      after in the pull request.
- [ ] 4.4 Pull request description: the two findings, the half of `AUD-PRF-13` already held by `policy-audit-order`,
      the plans of 1.2, and the `listSnapshots` observation of the design as a separate item.
