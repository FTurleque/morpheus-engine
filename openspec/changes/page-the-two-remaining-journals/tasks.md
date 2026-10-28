# Tasks

## 1. Measure before changing

- [x] 1.1 List the production callers of `ChangeLifecycleMutationStore.listAudit` and `SyncStateStore.listArchives`.
      Proof: none, so no public surface changes.
      *2026-10-28: `git grep` under `src/main`: no call to either method outside the ports and their memory and SQLite implementations; no public surface changes.*

## 2. Reproduce before fixing

- [x] 2.1 A rule that refuses a journal store method returning a collection of journal records, red on `HEAD` on the
      two unbounded reads. Proof: the failure names both methods on the port and on both stores.
      *2026-10-28: `noJournalStoreOffersAWholeJournal` red on `9c027eeb`: six violations, `listAudit` and `listArchives` on the two ports and on both stores.*

## 3. Ports and page records

- [x] 3.1 `ChangeLifecycleMutationAuditPage` and `SourceArchivePage`, validated in their compact constructors, with a
      factory that slices an ordered list. Proof: a test of each refusal.
      *2026-10-28: `ChangeLifecycleMutationAuditPageTest`, `SourceArchivePageTest` (refusals asserted by message).*
- [x] 3.2 `listAudit(projectId, changeId, PageRequest)` and `listArchives(projectId, PageRequest)` replace the unbounded
      methods; `SourceArchiveRecord.compareTo` breaks its last tie by fingerprint. Proof: compiles; no unbounded method
      left.
      *2026-10-28: ports changed; `SourceArchivePageTest#archivesThatDifferOnlyByContentAreOrderedByFingerprint`.*
- [x] 3.3 Memory stores slice their ordered list.
      *2026-10-28: `MemorySyncStateStoreTest#archivesAreReadOnePageAtATimeWithTheirTotal`, `MemoryChangeLifecycleMutationStoreTest`.*

## 4. SQLite

- [x] 4.1 Lifecycle audit: `LIMIT ? OFFSET ?` on the existing order, plan guarded. Proof: a page test and
      `SqliteQueryPlanGuardTest`.
      *2026-10-28: `AUDIT_PAGE_SQL`/`AUDIT_COUNT_SQL` guarded on `idx_change_lifecycle_audit_change`; pages in `ControlledLifecycleMutationContractTest#bothStoresReturnTheAuditOfAChangeOnePageAtATimeInRevisionOrder`.*
- [x] 4.2 Archives: migration adding `archived_key`, filling it, the order index, dropping the unused time index; the
      store writes the key, orders in SQL, sorts nothing in Java; supported schema version bumped. Proof: page and
      upgrade tests; `SqliteQueryPlanGuardTest`.
      *2026-10-28: `V023__source_archive_order_key.sql`; `SqliteSourceArchiveOrderKeyMigrationTest`, `ReleasedSchemaUpgradeTest` (archives written in reverse time-text order at every released level), `SqliteMigrationChecksumGoldenTest`, `SqliteSchemaMigrationTest`, `SqliteQueryPlanGuardTest` (search on `idx_sync_source_archives_project_order`, no temporary b-tree).*
- [x] 4.3 The SQL order equals `compareTo` on instants whose text sorts differently from time, across page boundaries,
      on both stores. Proof: `SourceArchiveOrderParityTest`.
      *2026-10-28: `SourceArchiveOrderParityTest#bothStoresCutTheSamePagesAcrossInstantsWhoseTextSortsAgainstTime`: `:00Z`, `:00.000000001Z`, `.500Z`, `.500100Z` and a fingerprint tie, pages of two, equal on both stores and equal to `compareTo`.*

## 5. Tests and architecture

- [x] 5.1 Tests that read a whole journal read it page by page.
      *2026-10-28: `JournalPages` in the architecture tests; counts read from `totalMatches` elsewhere.*
- [x] 5.2 `AppendOnlyJournalReadsArchitectureTest`: the port rule, a guard that counts exactly the methods it judges, a
      fixture that breaks it; the rule broken once by hand. Proof: the failure text.
      *2026-10-28: a temporary `default List<SourceArchiveRecord> listEveryArchive(...)` on `SyncStateStore` failed `noJournalStoreOffersAWholeJournal` (1 violation, named) and the guard (tenth judged method); removed. The fixture test fails the rule both ways on every run.*

## 6. Documentation

- [x] 6.1 ADR-0131, the upgrade guide and the developer pages say the three journals are paged.
      *2026-10-28: ADR-0131 sections 4 and 6, Do's and Don'ts and compliance; `docs/user/UPGRADE_1_2_2.md` and `docs/user/README.md` name V023. The edit changed ADR-0131's decision in part, so it is consolidated in ADR-0151, which replaces it;
      ADR-0131 is archived.*

## 7. Verify

- [x] 7.1 Module tests and the named architecture tests green; `dependency:analyze` clean on the touched modules;
      `archgate check` run.
      *2026-10-28, offline reactor `test`, no `clean verify`: `morpheus-application` 507 run / 0 failed, `morpheus-store-memory`
      and `morpheus-store-sqlite` green (sqlite 245 run, 1 skipped); the four API/CLI tests that read the lifecycle
      audit green; `morpheus-architecture-tests` 829 run, 2 failed, both environmental under `test` alone:
      `CoverageQualityGateTest` (no JaCoCo report without `verify`) and `ProviderPluginPlatformContractTest` (reference
      JAR not packaged). `dependency:analyze`: no problem on the 17 reactor modules analysed. `archgate check`: 12/12
      pass, one briefing warning (Decision of ADR-0131 over 2 000 characters, already over before this change).*
