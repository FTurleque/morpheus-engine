# Proposal

## Why

Three findings of sprint 7 of the audit of 2026-10-09 are about the indexes of the SQLite schema, and all three need the
same thing: a migration. Re-read on `develop` at `8b26d3d7` on 2026-10-13.

**AUD-PRF-07 (Medium) holds, and the count is wrong.** The finding is titled "six redundant indexes"; its own evidence
lists ten, and the schema holds eleven. Measured by loading the twenty migration files and comparing, table by table,
the column list of every index a migration creates with the column list of every other non-partial index
(`PRAGMA index_list` / `index_xinfo`):

| Index | Created at | Same leading columns as |
|---|---|---|
| `idx_saved_view_versions_identity` | `V014:27` | primary key, exact |
| `idx_snapshot_traceability_links_snapshot` | `V005:35` | primary key, exact |
| `idx_policy_pack_activations_scope` | `V015:36` | primary key, exact |
| `idx_policy_overrides_scope` | `V015:53` | primary key, exact |
| `idx_policy_pack_versions_pack` | `V015:20` | `UNIQUE (pack_id, version_number)`, exact - **not in the audit** |
| `idx_sync_inventory_entries_project` | `V008:36` | primary key, left prefix |
| `idx_snapshot_constraint_blocking_targets` | `V010:30` | primary key, left prefix |
| `idx_snapshot_constraint_supporting_evidence` | `V010:32` | primary key, left prefix |
| `idx_snapshot_specifications_snapshot` | `V007:174` | primary key, left prefix |
| `idx_snapshot_scenarios_snapshot` | `V007:175` | primary key, left prefix |
| `idx_snapshot_changes_snapshot` | `V007:176` | primary key, left prefix |

Each is maintained on every insert, update and delete of its table and gives no access path the constraint's own index
does not give. Three of them sit on the tables the publication path fills in bulk.

**AUD-PRF-10 (Medium) holds.** `SqliteVersionedRequirementStore.java:309-314` reads
`WHERE snapshot_id = ? AND temporal_state = 'CURRENT' ORDER BY entity_version_id LIMIT ?`. No index carries
`(snapshot_id, entity_version_id)`: `V004:45-47` is partial on `CURRENT` but ordered by `entity_identity_id`, `V004:49`
carries `snapshot_id` alone. The limit therefore bounds the rows returned, not the rows read and sorted. Not in the
audit: the unbounded twin at `:281-285` has the same shape and the same sort, and nine callers use it (eight
application services and the MCP tool service).

**AUD-PRF-11 (Low) holds.** `SqliteSavedViewStore.java:110-116` orders by `name COLLATE NOCASE, id`;
`V014:13-14` indexes `(scope_kind, scope_id, name, id)` in the default binary collation, which serves the filter and
not the order. The rows sorted are bounded by `QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE` (`:60-68`).

The plans were observed, not deduced - but not through the product: with the SQLite library bundled with the local
Python (3.50.4), on an in-memory database built from the migration files. That is a dated observation with another
library version than `sqlite-jdbc` carries; the first task repeats it through the product's driver. What it showed:

- the bounded read uses `idx_requirement_versions_snapshot` then `USE TEMP B-TREE FOR ORDER BY`; with a partial index on
  `(snapshot_id, entity_version_id)` the temporary sort disappears;
- the saved-view listing uses `idx_saved_views_scope` then `USE TEMP B-TREE FOR ORDER BY`; with `name COLLATE NOCASE` in
  the index the sort disappears and the index still covers the `COUNT(*)` of the budget check;
- four statements choose a redundant index today (`idx_saved_view_versions_identity`,
  `idx_policy_pack_activations_scope`, `idx_policy_overrides_scope`, `idx_sync_inventory_entries_project`); once it is
  dropped each uses the constraint's index with the same search terms and no added sort or scan.

The module never runs `ANALYZE` or `PRAGMA optimize` (no occurrence under `morpheus-store-sqlite/src/main`), so no
statistics table exists and the plan of a statement does not depend on the data: a plan measured on a migrated, empty
database is the plan in production.

Nothing guards any of this. `SqliteSchemaMigrationTest.java:98-122` asserts that each of the eleven redundant indexes
**exists**; no test reads a query plan; the sprint's exit criterion ("every filtered or sorted column is served by an
index, verified by EXPLAIN QUERY PLAN") has no executable form.

## What Changes

- One new migration, the next free version at implementation time, that drops every index whose column list is the
  leading part of another index of the same table, adds the index that serves the two CURRENT requirement reads in their
  contract order, and replaces the saved-view scope index by one that carries the collation its only ordered reader
  uses.
- A schema test that refuses, on a migrated database, any index a migration created that repeats the leading columns of
  another: a twelfth cannot be added by a later migration.
- A query-plan test that runs `EXPLAIN QUERY PLAN` through the product's driver on the exact statements under guard and
  refuses a full table scan or a temporary sort that is not listed with a reason.
- No query result changes: same rows, same order, on both stores.

## Capabilities

### New Capabilities

- `sqlite-index-coverage`: which indexes the migrated schema may hold, which reads must be served by an index in their
  order, and how the build proves it.

### Modified Capabilities

(none - `sqlite-schema-history` specifies how a migration is identified and accepted; a new migration falls under it
unchanged, and under the two requirements `back-up-before-a-schema-migration` adds to it)

## Impact

- `morpheus-store-sqlite`: one migration file, `SqliteSchemaManager` (the list and `SUPPORTED_SCHEMA_VERSION`),
  `SqliteMigrationChecksumGoldenTest` (one pin), `SqliteSchemaMigrationTest` (the assertions that the dropped indexes exist
  become assertions of absence), two new tests. The guarded statements may move to named constants (design, decision 5).
- `docs/user/UPGRADE_1_2_1.md` and `docs/user/release-notes/RELEASE_NOTES_1.2.1.md`: `UpgradeGuideCoherenceTest` fails
  until the guide states the schema version reached. `docs/developer/REMOTE_SERVER_PLATFORM.md` describes "the latest
  migration" by name and is stale already; it is corrected in the same change.
- No CLI command, MCP tool or HTTP route changes; `contracts/public-surfaces.tsv` and the OpenAPI files are untouched.
  No coverage ratchet moves. No ADR is proposed (design, open question 1).
- Order: after sprint 6, which rewrites reads of the same tables -
  `read-a-snapshot-content-in-a-fixed-number-of-statements` (`AUD-PRF-01`, no schema change; it neither uses nor
  removes the two constraint child indexes dropped here), `read-an-append-only-journal-one-page-at-a-time`
  (`AUD-PRF-04`, which may add a migration of its own) and `write-traceability-links-as-one-batch` (`AUD-PRF-06`).
- Sprint 13, `page-requirement-reads-in-the-store` (`AUD-PRF-05`), adds no index and says so: its paged reads order by
  requirement identity and by entity identity, not by entity version, so the index added here does not serve them. If
  its measurement shows that sort is the cost, the index on that key is asked of this change's migration (task 1.5),
  so that the schema advances once.
