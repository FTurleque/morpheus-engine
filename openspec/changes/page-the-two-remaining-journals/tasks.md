# Tasks

## 1. Measure before changing

- [ ] 1.1 List the production callers of `ChangeLifecycleMutationStore.listAudit` and `SyncStateStore.listArchives`.
      Proof: none, so no public surface changes.

## 2. Reproduce before fixing

- [ ] 2.1 A rule that refuses a journal store method returning a collection of journal records, red on `HEAD` on the
      two unbounded reads. Proof: the failure names both methods on the port and on both stores.

## 3. Ports and page records

- [ ] 3.1 `ChangeLifecycleMutationAuditPage` and `SourceArchivePage`, validated in their compact constructors, with a
      factory that slices an ordered list. Proof: a test of each refusal.
- [ ] 3.2 `listAudit(projectId, changeId, PageRequest)` and `listArchives(projectId, PageRequest)` replace the unbounded
      methods; `SourceArchiveRecord.compareTo` breaks its last tie by fingerprint. Proof: compiles; no unbounded method
      left.
- [ ] 3.3 Memory stores slice their ordered list.

## 4. SQLite

- [ ] 4.1 Lifecycle audit: `LIMIT ? OFFSET ?` on the existing order, plan guarded. Proof: a page test and
      `SqliteQueryPlanGuardTest`.
- [ ] 4.2 Archives: migration adding `archived_key`, filling it, the order index, dropping the unused time index; the
      store writes the key, orders in SQL, sorts nothing in Java; supported schema version bumped. Proof: page and
      upgrade tests; `SqliteQueryPlanGuardTest`.
- [ ] 4.3 The SQL order equals `compareTo` on instants whose text sorts differently from time, across page boundaries,
      on both stores. Proof: `SourceArchiveOrderParityTest`.

## 5. Tests and architecture

- [ ] 5.1 Tests that read a whole journal read it page by page.
- [ ] 5.2 `AppendOnlyJournalReadsArchitectureTest`: the port rule, a guard that counts exactly the methods it judges, a
      fixture that breaks it; the rule broken once by hand. Proof: the failure text.

## 6. Documentation

- [ ] 6.1 ADR-0131, the upgrade guide and the developer pages say the three journals are paged.

## 7. Verify

- [ ] 7.1 Module tests and the named architecture tests green; `dependency:analyze` clean on the touched modules;
      `archgate check` run.
