# Design

## Context

- A migration is a numbered SQL file listed in `SqliteSchemaManager.MIGRATIONS`; `SUPPORTED_SCHEMA_VERSION` is the
  highest. A released migration is never edited: its canonical checksum is pinned by
  `SqliteMigrationChecksumGoldenTest`, and a database that records another checksum is refused
  (`openspec/specs/sqlite-schema-history`). The redundant indexes live in V005 to V015, all released with 1.2.0, so
  they can only be removed by a later migration.
- Everything after V015 is unreleased. The version being prepared already advances the schema, the upgrade guide
  already states it, and the first open of an outdated database already leaves a verified copy behind (ADR-0110,
  `SqlitePreMigrationCopy`). One more migration before the tag adds no new kind of event for an operator.
- SQLite builds an index for every `PRIMARY KEY` of a rowid table that is not an `INTEGER PRIMARY KEY`, and for every
  `UNIQUE` constraint. No table of the schema is `WITHOUT ROWID`.
- The planner chooses between two indexes with the same columns without regard to which is the constraint's: on
  2026-10-13 four statements chose the explicit duplicate. Dropping it changes the name in the plan, not its shape.
- The port fixes the order of the two CURRENT requirement reads: "deterministic entity-version order"
  (`VersionedRequirementStore.java:46-48`, `:64-67`), and `MemorySpecificationKnowledgeStore.java:333` and `:347` sort
  by entity version id. `BoundedCurrentRequirementStoreTest` compares the two.
- `SavedViewDefinition.compareTo` orders by `name.compareToIgnoreCase`, then id; the SQLite store by
  `name COLLATE NOCASE, id`.

## Goals / Non-Goals

**Goals**: no index that repeats another; the two CURRENT requirement reads and the saved-view listing served in order
by an index; an executable form of the sprint's exit criterion; one schema advance for the three findings.

**Non-Goals**:

- Rewriting any read. Bounding the reads that load a whole table and removing the per-row child queries are sprint 6
  (`AUD-PRF-01`, `AUD-PRF-02`, `AUD-PRF-04`, `AUD-PRF-06`); paginating through the port is sprint 13 (`AUD-PRF-05`).
  This change adds no `LIMIT`, no cursor and no port method.
- The relation filter of the traceability reads and the double ordering of two listings: they need no migration and are
  `filter-and-order-a-store-read-once`.
- Running `ANALYZE`. It would make plans depend on the data and a plan test meaningless.
- Changing how a migration is identified, accepted or backed up.

## Decisions

1. **One migration for the three findings.** Each migration raises `SUPPORTED_SCHEMA_VERSION`, moves the statement the
   upgrade guide must make, adds a golden checksum and is one more step an outdated database replays. Three findings
   that are all index statements on an unreleased schema advance do not justify three. *Rejected*: one migration per
   finding - three schema versions for one decision, and three chances to merge them in the wrong order.
   *Rejected*: editing V005 to V015 - forbidden by the pinned checksums, and it would refuse every existing database.
   *Rejected*: folding the statements into V016 to V020 because they are unreleased - developer and CI databases
   already record their checksums, and the golden test pins them.

2. **The set to drop is derived, then written.** The migration names each index (SQL has no other way); the list comes
   from the measurement of task 1.1, not from the audit's ten nor from this document's eleven. The rule that keeps the
   set empty afterwards is a property of the migrated schema, checked on a real database: for every table, no index
   whose origin is `CREATE INDEX`, that is neither unique nor partial, has a column list - names, collations and
   directions in order - equal to the leading part of another non-partial index of the same table.
   Kept by that rule, as the audit asks: `idx_snapshot_constraints_change`, `idx_snapshot_acceptance_*`,
   `idx_snapshot_design_decisions_change`, `idx_snapshot_implementation_tasks_change` (other columns),
   `idx_portfolio_memberships_project` (reverse order), `idx_requirement_versions_snapshot` (the index that shares its
   leading column is partial).
   *Rejected*: a textual scan of the migration files for a `CREATE INDEX` that repeats a `PRIMARY KEY` - it would have
   to parse SQL and would miss the `UNIQUE` duplicate the audit missed.

3. **`DROP INDEX`, without `IF EXISTS`.** Every database that reaches this migration has replayed the ones that created
   these indexes. One that lacks an index has a history the ledger does not describe; the migration fails, the
   transaction rolls back and the database is left as it was - an explicit failure rather than a silent pass.

4. **The index, not the order, for the CURRENT requirement reads.** A partial index on
   `(snapshot_id, entity_version_id) WHERE temporal_state = 'CURRENT'` serves both reads in the order the port
   promises. *Rejected*: ordering by `entity_identity_id` so that the existing partial index serves the limit, the
   audit's first option. It is one line and no migration, but the order is a port contract with a parity test, and under
   a limit a different order returns different rows: the query DSL would evaluate another subset of a snapshot that
   exceeds its source budget. The paged reads of sprint 13 (`page-requirement-reads-in-the-store`) order by other keys and
   are not served by this index; an index for them, if their gate asks for one, joins this migration. Whether `idx_requirement_versions_snapshot` is still chosen by any statement afterwards is measured in task
   1.3; it is dropped only if the redundancy rule of decision 2 says so, which it does not today.

5. **The saved-view index is replaced, not doubled.** `idx_saved_views_scope` becomes
   `(scope_kind, scope_id, name COLLATE NOCASE, id)`: it serves the filter, the order and the `COUNT(*)` of the budget
   check, and no statement orders or compares `name` in the binary collation (to be confirmed in task 1.3). The column
   keeps its declared collation: changing it would need the table rebuilt and would change what `=` means on `name`.
   *Rejected*: accepting the sort with a comment, the audit's second option. It is defensible - the rows are bounded by
   the per-scope budget - and it is the fallback if task 1.3 finds a binary reader of the index; but a migration is
   being written anyway and the replacement costs no additional index.

6. **The plan guard asserts shapes, not strings.** For each statement under guard the test runs `EXPLAIN QUERY PLAN`
   through `sqlite-jdbc` on a migrated database and refuses a detail line that starts with `SCAN` on a table of the
   schema, or that contains `USE TEMP B-TREE`, unless the statement is listed with a reason; a listed statement whose
   plan no longer has the shape it is excused for fails too. It does not compare the plan with a recorded text: a
   `sqlite-jdbc` bump may rename or reorder lines without changing the shape, and when a bump does bring back a sort the
   failure is the signal wanted.
   The guard must explain the text the store executes, not a copy. The statements under guard become named constants
   of their store class, read by the test of the same package. *Rejected*: a second copy of the SQL in the test - it
   guards the copy. *Rejected*: a recording JDBC wrapper that explains whatever the persistence tests prepare - it
   guards more, but through a proxy of `java.sql.Connection` the store classes would have to accept, a seam added for a
   test. Kept as open question 2.
   Under guard at first: the statements this change touches and those whose index it drops. Extending the guard to
   every filtered or ordered read of the module is the second lot of the change, sized by task 1.2.

7. **Enforcement is neither ArchUnit nor textual.** The intent is "the migrated schema holds no repeated index" and
   "this statement is served by an index": properties of a database and of a planner, visible in no bytecode and in no
   source text. Both tests live in `morpheus-store-sqlite`, beside the store, and run in `clean verify`. The procedure
   of `.claude/skills/enforcement-choice` is followed for the one sentence each rule states and for breaking it once.

## Risks / Trade-offs

- **A dropped index may have been the one chosen for a statement not measured.** The eleven are exact or leading
  duplicates of an index that remains, so no search term is lost; task 1.3 measures every statement on the affected
  tables before and after, and the guard holds the ones that changed name.
- **The migration writes on large tables.** Creating the partial index reads `requirement_versions` once, inside the
  migration transaction, after the pre-migration copy. The drops are cheap. The net effect on the file is fewer pages.
- **Foreign keys.** SQLite looks up child rows of a cascading delete through an index on the child key; for every table
  that loses an index the child key is the leading part of its primary key, so the lookup stays indexed. No statement
  of the module exercises such a cascade today: it issues four `DELETE` statements, none on a parent of these tables
  (measured 2026-10-13, re-measured in task 1.3). The property is stated for the schema, not for a product path.
- **An older program on a migrated database.** No SQLite program depends on an index by name; the published 1.2.0,
  which the upgrade guide forbids on a migrated database, would lose nothing it needs by this migration in particular.
- **The planner is a dependency.** A plan test ties the build to the SQLite version `sqlite-jdbc` embeds, which
  `rules/build.md` pins. That is the intent: the bump that changes a plan is the change to look at.
- **Sprint 6 rewrites reads of the same tables.** If it lands first the measurements of task 1 are taken on its code; if
  it lands after, its new statements meet the guard and the redundancy rule. Either order works; the audit's order
  (6, then 7) avoids resolving the same hunks twice.

## Open Questions

1. **An ADR?** Recommendation: none. No boundary, port, contract or dependency moves; ADR-0021 already decides that the
   schema changes by explicit versioned migrations, and this capability specification is the record of the two rules.
   The filter of `.claude/skills/adr-author` is run in task 4.4 and its answer written in the pull request.
2. **How far does the plan guard reach?** Recommendation: named constants for the statements under guard now, and the
   whole module in a second lot once task 1.2 has listed them (39 statements carried an `ORDER BY` on 2026-10-13). The
   recording wrapper is the alternative if the maintainer prefers coverage by construction over an explicit list.
3. **`idx_sync_source_archives_project_time` and `idx_policy_audit_pack`.** Both carry a timestamp column stored as
   `Instant.toString()`, whose text does not sort chronologically, so neither can serve a chronological order; both are
   still chosen for their leading column. They are not redundant by the rule of decision 2 and are left alone here.
   Sprint 6 decides how these two tables are bounded, and with it what their indexes should carry.
