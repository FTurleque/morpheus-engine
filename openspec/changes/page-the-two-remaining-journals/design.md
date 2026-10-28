# Design

## Context

- `read-an-append-only-journal-one-page-at-a-time` (archived 2026-10-17): decision 1 puts the page in the store port
  and removes the unbounded read; decision 2 (b) gives the policy audit a stored sortable key `at_key` (migration 21,
  `SqlitePolicyPackStore.orderKey`); decision 4 fences the two journals without a reader instead of paging them.
- `change_lifecycle_mutation_audit` is ordered by `to_revision` (an integer) then `mutation_id`, and
  `idx_change_lifecycle_audit_change(project_id, change_id, to_revision, mutation_id)` follows that order.
- `sync_source_archives.archived_at` holds `Instant.toString()`, whose text does not sort by time when the fraction
  varies in length. Its primary key is `(project_id, source_path, fingerprint_sha256, archived_at, reason)`;
  `idx_sync_source_archives_project_time(project_id, archived_at)` is used by no statement that orders.
- `SourceArchiveRecord.compareTo` orders by instant, path, reason, and returns 0 for two records that differ only by
  fingerprint, which the primary key admits.
- `PageRequest` (`com.morpheus.application.query`) bounds an offset and a limit; `PolicyAuditPage` is the page of the
  policy audit. No generic page without a snapshot exists in the application layer (`SnapshotPage` carries one).

## Goals / Non-Goals

**Goals**: the two journals read by pages bounded at the store, in a total order the database follows from an index;
no unbounded journal read on any port; memory and SQLite return the same pages.

**Non-Goals**: a reader of either journal on a public surface; a cursor; a purge; the cost of `COUNT(*)` and `OFFSET`
on a deep page, which the policy audit already carries and ADR-0131 records.

## Decisions

1. **The port takes the page; the unbounded method goes.** `listAudit(projectId, changeId, PageRequest)` returns a
   `ChangeLifecycleMutationAuditPage`; `listArchives(projectId, PageRequest)` returns a `SourceArchivePage`. Each record
   holds the items, the request, the total and `hasMore`, validated in its compact constructor like `PolicyAuditPage`,
   with a factory that slices a list already in order for the memory store.
   *Rejected*: a generic journal page - it would be a fourth page type beside `PolicyAuditPage`, `SnapshotPage` and
   `RequirementSearchPage`, adopted by one of the three journals only; one record per journal is what the policy audit
   already does. *Rejected*: keeping the unbounded method beside the paged one (decision 1 of 2026-10-17).

2. **The lifecycle audit pages on the order it has.** `ORDER BY to_revision, mutation_id LIMIT ? OFFSET ?`, served by the
   existing index; no migration. A revision is an integer, so the order needs no key.

3. **The archives get a stored sortable key, the shape of `at_key`.** A migration adds `archived_key`, fills it from
   `archived_at` with the expression of migration 21, and creates
   `idx_sync_source_archives_project_order(project_id, archived_key, source_path, reason, fingerprint_sha256)`. The store
   writes the key with each row (`SqliteSyncStateStore.archivedKey`, the format of `SqlitePolicyPackStore.orderKey`),
   orders by the index columns and no longer sorts in Java. The index that no statement uses,
   `idx_sync_source_archives_project_time`, is dropped by the same migration: the new index carries the same leading
   column and no read searches by `archived_at`. The stored text of `archived_at` is untouched.
   The column order is exactly `SourceArchiveRecord.compareTo`: the key orders by time; `source_path` compares as
   `SourcePath.compareTo`; `reason` holds `DELETED` and `MOVED`, whose text order is their declaration order.

4. **The order of an archive is total.** `SourceArchiveRecord.compareTo` breaks its last tie by the fingerprint's
   lowercase hexadecimal text, the fifth column of the primary key. Without it two rows of one instant, path and reason
   compare equal: the memory store keeps them in write order, SQLite in any order, and an offset between them names a
   different record on each.

5. **The fence becomes a rule on the ports.** No method declared by `PolicyPackStore`, `ChangeLifecycleMutationStore`,
   `SyncStateStore` or a class implementing one returns a collection (or an array, or a stream) of journal records, and a
   method that returns a page of journal records takes a `PageRequest`. Its guard counts exactly the methods the rule
   judges - the methods that return journal records - and requires each of the three paged reads to be among them on
   the port and on both stores. A fixture interface in the test sources breaks the rule on every run. The call fence is
   removed: a page is bounded, so a production caller is no longer a risk to refuse.
   Enforcement mechanism: an ArchUnit rule - the intent is "no port offers a whole journal", a property of compiled
   signatures that text matching would read from source formatting.

## Risks / Trade-offs

- `SourcePath.compareTo` compares UTF-16 code units; SQLite's `BINARY` collation compares UTF-8 bytes. They agree on
  every path whose characters are below U+E000 or all above U+FFFF, and may disagree between a character in
  U+E000..U+FFFF and a supplementary one at the same position. The inventory read (`ORDER BY source_path`) already
  carries this; it is recorded here, not fixed.
- A migration in a corrective line: one more schema version for the upgrade guide, and the way back is the backup.
- The memory store and SQLite admit different duplicates (the memory store compares whole records, SQLite the primary
  key): unchanged by this change.

## Open Questions

(none)
