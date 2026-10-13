# Proposal

## Why

AUD-PRF-02 (sprint 6, High) reports that the analysis of a change runs a whole breadth-first search again for every
node the traversal has just discovered. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds**, and
two of its statements need correcting:

- `ChangeAnalysisService.collectDependencyDirection` traverses once (`:306`), then calls `findPath` from the root to
  each discovered node (`:321-331`). `TraceabilityTraversalService.findPath` (`:119-171`) starts a new search each
  time, and every node it expands asks the store for its links (`:200`, `:205`).
- With the node budget at 1,000 (`MAX_NODES`, `:30`), a traversal that reaches it is followed by up to 999 searches of
  up to 1,000 expansions each: about a million store reads per direction, for each requirement delta of the proposal,
  in both directions (`:261-287`). The traversal itself needed about a thousand.
- No M19 gate names `ChangeAnalysisService` or `findPath`.

**First correction.** The audit says the traversal "already computes the predecessor tree and throws it away".
It does not: `traverse` keeps the depth of each node and the links (`:79-80`); the predecessor map exists only in
`findPath` (`:139`). The remedy "return the map it already builds" has nothing to return. The traversal has to record,
when it first discovers a node, the step that discovered it.

**Second correction.** The audit places the cost "on the HTTP path". `ChangeAnalysisService` is built at one place in
production code, `MorpheusCli.java:551`, for the command `analyze-change` (depth 1 to 20, default 2); `morpheus-api`
and `morpheus-mcp` do not mention it, and `findPath` has no other production caller. The cost is real and it is the
CLI's; no server request pays it today. `analyze-change` has no line in `contracts/public-surfaces.tsv` either, which
belongs to the gate of the `cli` column (AUD-TST-06, sprint 8) and is only noted here.

What makes the correction safe is a property of the two searches: both are breadth-first, first-in first-out, over the
same neighbour order (`NEIGHBOR_ORDER`, `:33-36`). The step by which the traversal first reaches a node is therefore
the step `findPath` would have recorded for it, and the path rebuilt from those steps is the path `findPath` returns.
The analysis can keep its output exactly.

None of this cost was measured, by the audit or by this re-reading.

## What Changes

- The analysis of a change explains every dependency it reports from the traversal that found it: the number of store
  reads grows with the nodes discovered, not with their square.
- The analysis returns exactly what it returned: same impacts, same explanation paths, same warnings, same order.
- The traceability and change-context answers, which use the same traversal, do not change.

## Capabilities

### New Capabilities

- `change-analysis-traversal-cost`: the cost of explaining the dependencies of a change is that of finding them.

### Modified Capabilities

(none)

## Impact

- `morpheus-application`: `TraceabilityTraversalService`, `ChangeAnalysisService`, possibly a new value next to
  `TraceabilitySubgraph`. No adapter, no store, no contract, no ADR.
- `findPath` loses its only production caller: it is removed with its tests moved, or excused by name, as
  `remove-unreachable-and-unreferenced-code` (capability `dead-code-refusal`) requires.
- Measured against the change-analysis budget declared by `declare-a-budget-before-bounding-a-cost`, which lands first.
- Sprint 7 comes after: AUD-PRF-12 moves the relation-type filter of `SqliteTraceabilityStore` into SQL. It lowers the
  cost of one store read; this change lowers their number. The two do not touch the same file.
