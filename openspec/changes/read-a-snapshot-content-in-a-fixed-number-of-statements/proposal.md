# Proposal

## Why

AUD-PRF-01 (sprint 6, High) reports an N+1 in the read of a snapshot's business content. Re-read on `develop` at
`8b26d3d7` on 2026-10-13, the finding **holds**, line for line:

- `SqliteSnapshotBusinessContentReader` runs one child query per parent row at seven places: the preconditions of each
  scenario (`:162`), the scope, out-of-scope and risks of each change (`:190`, `:192`, `:194`), the blocking targets
  and supporting evidence of each constraint (`:211`, `:218`), the verification evidence of each acceptance criterion
  (`:304`). Each prepares its own statement (`:315`, `:341`) while the parent's result set is still open.
- A read therefore executes `scenarios + 3 x changes + 2 x constraints + criteria` statements on top of the nine that
  read the header and the eight parent tables.
- The writer beside it does the opposite: `SqliteSnapshotBusinessContentWriter` prepares each statement once and
  batches.

Two things the audit did not say change how the correction is specified:

- **The read is everywhere.** The audit names three callers. `findSnapshotContent` has 15 call sites in production
  code - 13 in `morpheus-application`, one in `morpheus-api` (`MorpheusSpecificationQueryApiService.java:39`), one in
  `morpheus-mcp` (`MorpheusMcpToolService.java:79`) - behind most read capabilities of the three transports.
- **No gate has ever measured it.** The M19 fixture publishes 15,000 scenarios and no change, constraint or criterion,
  and no gate reads the content back (see `declare-a-budget-before-bounding-a-cost`). The audit's figure of about 2,100
  statements is arithmetic on an imagined snapshot; the cost is unmeasured, and the audit says so.

The primary key of each of the seven child tables is `(snapshot_id, <owner>, ordinal)`: one query per table, for the
whole snapshot, in owner then ordinal order, is served by an index that already exists. No schema change is needed.

## What Changes

- Reading a snapshot's content executes a number of statements that does not depend on how many rows the snapshot
  holds.
- The content read is the same value as before, element for element and order for order, on both stores.
- A test in the ordinary suite counts the statements, so the shape cannot come back between two nightly runs.

## Capabilities

### New Capabilities

- `snapshot-content-read-cost`: the read of a snapshot's business content costs a fixed number of statements, and
  returns what it returned.

### Modified Capabilities

(none)

## Impact

- `morpheus-store-sqlite`: `SqliteSnapshotBusinessContentReader` only. No port, no contract, no migration, no ADR.
- Measured against the content-read budget declared by `declare-a-budget-before-bounding-a-cost`, which lands first.
- Sprint 7 comes after on the same module: AUD-PRF-07 (redundant indexes) lists two indexes on the constraint child
  tables that duplicate their primary key; this change neither uses nor removes them.
- After `own-the-sqlite-connection-policy-in-one-place` and `keep-the-scheduler-free-while-sqlite-waits` (sprint 5),
  which touch the store classes around the reader; the reader itself is not theirs.
