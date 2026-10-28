# Proposal

## Why

AUD-PRF-04 (sprint 6, High) asks that the three journals that only grow - the policy audit, the audit of a lifecycle
mutation and the archives of a synchronisation - be read by pages bounded at the store. The change
`read-an-append-only-journal-one-page-at-a-time` (archived 2026-10-17) paged the policy audit and **fenced** the two
others: their store methods return every row of a change or of a project, no production class calls them, and an
ArchUnit rule refuses the first production call (its decision 4).

The counter-verification of 2026-10-28 keeps the finding partial, for two reasons:

- **The bound is not at the store.** `ChangeLifecycleMutationStore.listAudit` and `SyncStateStore.listArchives` still
  read every row; the archive list still sorts in Java after the read. The finding asks for the bound in the query, and
  a fence only holds as long as nobody needs the read.
- **A port method waits for its next caller.** An unbounded method on a port is the short signature the next surface
  will pick; decision 1 of the archived change rejected keeping one beside a paged read for that reason, and the fence
  keeps one alone. The guard of the fence was also too loose: it counted every call to either port, so it stayed green
  if the rule stopped seeing the two calls it refuses.

The reason decision 4 gave for not paging - an order to design for no caller, and for the archives the timestamp
problem solved a second time - is a cost already paid once: the policy audit has a stored sortable key, its migration,
its index and its proof. The archives reuse that shape.

## What Changes

- `ChangeLifecycleMutationStore.listAudit` takes a `PageRequest` and returns a `ChangeLifecycleMutationAuditPage`: the
  records of the page, in revision then mutation order, and the total of the change. The unbounded method is removed.
- `SyncStateStore.listArchives` takes a `PageRequest` and returns a `SourceArchivePage`: the records of the page, in the
  order of `SourceArchiveRecord`, and the total of the project. The unbounded method is removed.
- `SourceArchiveRecord`'s order breaks its last tie by the fingerprint, which the table's primary key holds, so that the
  order is total and an offset names one record on both stores.
- SQLite: the lifecycle audit page is `LIMIT ? OFFSET ?` on its existing order and index. A migration adds
  `sync_source_archives.archived_key`, the instant of `archived_at` as a fixed-width text, fills it for existing rows,
  and an index in the exact order of the records; the store writes the key, orders in SQL and no longer sorts in Java.
- The architecture fence becomes a rule on the ports: no method of a journal store port, or of a class implementing
  one, returns a collection of journal records, and every method that returns a page of one takes a `PageRequest`.
- Tests that read a whole journal read it page by page.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `append-only-journal-reads`: the requirement that fenced the two journals without a reader is replaced by one that
  pages them at the store like the policy audit.

## Impact

- `morpheus-application` (`ChangeLifecycleMutationStore`, `SyncStateStore`, `SourceArchiveRecord`, two page records),
  `morpheus-store-sqlite` (both stores, a migration, the supported schema version), `morpheus-store-memory` (both
  stores), tests of `morpheus-application`, `morpheus-api`, `morpheus-cli` and `morpheus-architecture-tests`.
- **No public surface changes**: neither method has a caller in production code, before or after.
- A schema version: `docs/user/UPGRADE_1_2_2.md` names it; `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION` is read, never
  copied.
- `docs/architecture/adr/BE-0151-pagination-des-collections-et-journaux.md` says the three journals are paged.
