# Proposal

## Why

AUD-PRF-06 (sprint 6, Medium) reports that `putLinks` batches nothing. Re-read on `develop` at `8b26d3d7` on
2026-10-13, the finding **holds**:

- `SqliteTraceabilityStore.putLinks` (`:59-76`) opens one transaction and calls `putLinkInternal` for each link
  (`:69-71`). That method (`:206-222`) prepares and runs, per link: a lookup of the definition (`:225`), an insert of
  the definition (`:170`), a batch of its evidence (`:195`), and an insert of the membership (`:216`). When the
  definition already exists the two inserts are skipped, and the lookup itself reads the link's evidence with one more
  statement (`:252`, `:257`): three statements instead of four.
- Its one production caller is the publication of a snapshot (`ProjectSnapshotImportService.java:129`), which generates
  a new identity for every link it derives (`:127`). On that path the lookup therefore never finds anything: it is one
  statement in four that cannot succeed.
- The whole batch runs in one transaction; on a rollback journal that is one writer holding the database while every
  other connection waits on its busy timeout.

Two facts the audit did not record:

- **What the batch refuses is not tested.** Three tests call `putLinks`: a batch of two links
  (`SqliteTraceabilityStorePersistenceTest.java:108`), an empty batch (`:207`) and a CLI fixture
  (`MorpheusCliTest.java:268`). The collision and the idempotent rewrite are tested on `putLink` only (`:163`,
  `:185`). What a batch does on a duplicate, on a collision or on a failure in the middle is exercised by nothing.
- **The two stores disagree on a refused batch.** The memory store does not override `putLinks`: it inherits the port's
  default (`TraceabilityStore.java:18-20`), which stores the links one by one. A collision on the tenth link leaves nine
  stored in memory, and none in SQLite, where the transaction rolls back. The repository's rule is that the two stores
  behave alike.

The audit lowered the severity because the cost is budgeted: the publication of 25,000 links has a frozen bound
(`M19FullPublishPerformanceGate.java:40`). The correction is therefore measured against a budget that exists, and
its aim is the time the database is held; whether that budget is in any danger is the first thing to read from a
nightly run.

## What Changes

- A batch of links is written with a number of prepared statements that does not depend on how many links it holds.
- What the batch means is written down and tested: all or nothing, a link already defined identically joins the
  snapshot without being rewritten, a different definition under the same identity refuses the batch.
- The memory store refuses a batch the same way.

## Capabilities

### New Capabilities

- `traceability-link-batch-write`: what storing a batch of traceability links guarantees, and what it costs.

### Modified Capabilities

(none)

## Impact

- `morpheus-store-sqlite` (`SqliteTraceabilityStore`), `morpheus-store-memory` (`MemoryTraceabilityStore`),
  `morpheus-application` (the port's default `putLinks`, if it stops being a default). No contract, no migration, no
  ADR.
- Measured against the existing publication budget; no new budget.
- After `keep-the-scheduler-free-while-sqlite-waits` (sprint 5): its harness measures how long a writer makes readers
  wait, and this change shortens the longest writer the product has. Landing it first would move the baseline that
  decision is taken from.
- Sprint 7 comes after on the same file: AUD-PRF-12 rewrites the read (`endpointQuery`); this change rewrites the write
  and leaves the read alone.
