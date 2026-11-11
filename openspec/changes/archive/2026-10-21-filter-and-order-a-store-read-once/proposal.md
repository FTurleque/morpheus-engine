# Proposal

## Why

Two Low findings of sprint 7 of the audit of 2026-10-09 are about a read that does part of its work twice, once in SQL
and once in Java. Neither needs a migration. Re-read on `develop` at `8b26d3d7` on 2026-10-13.

**AUD-PRF-12 holds.** `SqliteTraceabilityStore.endpointQuery` builds its statement at `:145-148` with no mention of
`relation_type`, maps every link of the endpoint, and keeps or drops each at `:156-157`
(`filter.isEmpty() || filter.contains(link.relationType())`). `V005:29-33` created both endpoint indexes with
`relation_type` as third column. The read is the inner step of the traversal
(`TraceabilityTraversalService.java:200` and `:205`, once per node and per direction), and the relation filter is a
parameter of public reads (`TraceRequirementQueryService`, `ChangeContextQueryService`). Observed with SQLite 3.50.4 on
a database built from the migration files - not through the product's driver: today the plan searches
`idx_traceability_links_source (source_kind=? AND source_identity_id=?)`; with `AND l.relation_type IN (?, ?)` it
searches `(source_kind=? AND source_identity_id=? AND relation_type=?)`. The third column is there and unused.

**AUD-PRF-13 holds for one of its two reads; the other is closed.**

- `SqlitePolicyPackStore.listAudit` (`:560-572`) has no SQL order and sorts after reading, with the reason written above
  it (`:555-558`). That is the behaviour `openspec/specs/policy-audit-order` specifies. Nothing to change.
- `SqliteSyncStateStore.listArchives` (`:94-98`) asks for `ORDER BY archived_at, source_path, reason`, then returns
  `records.stream().sorted().toList()` (`:115`). The audit asks whether `archived_at` has the defect the policy store
  documents. It has: the column is written by `record.archivedAt().toString()` (`:339`), an `Instant.toString()` whose
  fractional part varies in length, so the SQL order is not chronological; and `SourceArchiveRecord.compareTo`
  (`:34-44`) orders by instant, then by `SourcePath`, then by the declaration order of the reason, none of which the
  SQL clause is bound to reproduce. The Java sort is the truth - it is also what `MemorySyncStateStore.java:47` does -
  and the SQL sort is paid for nothing. Observed plan: `idx_sync_source_archives_project_time (project_id=?)` then
  `USE TEMP B-TREE FOR LAST 2 TERMS OF ORDER BY`.
- Not in the audit: `listArchives` has no caller outside tests on `HEAD`. The cost described is latent.

Nothing states the order of the archives the way `policy-audit-order` states the order of the audit, and no test writes
two archives whose timestamps sort differently as text.

## What Changes

- The two endpoint reads of the SQLite traceability store apply a non-empty relation filter in the statement, with
  bound parameters; an empty filter keeps today's statement. Same links, same order, on both stores.
- `listArchives` stops asking the database for an order it then replaces, and says why beside the statement, as the
  policy store does.
- The order of the source archives becomes a stated, tested property of both stores.

## Capabilities

### New Capabilities

- `sqlite-read-shaping`: what a SQLite store asks the database to filter and to order, and what it does itself, for the
  traceability endpoint reads and the source archive listing.

### Modified Capabilities

(none - `policy-audit-order` already holds the policy half of AUD-PRF-13 and is not touched)

## Impact

- `morpheus-store-sqlite`: `SqliteTraceabilityStore`, `SqliteSyncStateStore`, their tests. No migration, no schema
  version, no port signature.
- No CLI command, MCP tool or HTTP route changes; `contracts/public-surfaces.tsv` and the OpenAPI files are untouched.
  No ADR, no ratchet.
- Order: after sprint 6. `AUD-PRF-02` rewrites the traversal that calls the endpoint reads;
  `write-traceability-links-as-one-batch` (`AUD-PRF-06`) rewrites the write side of the same class and leaves
  `endpointQuery` to this change; `read-an-append-only-journal-one-page-at-a-time` (`AUD-PRF-04`) fences the archive
  listing, which has no production caller, and as specified leaves its order untouched: task 1.3 is expected to answer
  "Java". Only if its implementation ends up giving `sync_source_archives` a key that sorts chronologically in SQL is
  the second requirement of this change met by the database instead.
- Independent of `serve-ordered-store-reads-from-an-index`; if that change is merged first, its plan guard receives the
  filtered statement instead of a test of this change's own.
