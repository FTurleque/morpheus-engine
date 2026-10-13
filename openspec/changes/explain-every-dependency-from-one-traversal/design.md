# Design

## Context

- `traverse` is bounded by depth (the caller's), nodes and links (`MAX_NODES`, `MAX_LINKS`), and says why it stopped
  in `truncationReason` (ADR-0108). `findPath` is bounded by depth and nodes, and throws
  `TraceabilityTraversalBudgetException` at the node budget instead of answering that no path exists.
- `TraceabilitySubgraph` (start, nodes, links, truncation reason) is a public record of the application layer; three
  services return it or embed it (`TraceRequirementService`, `ChangeContextQueryService`, `ChangeAnalysisService`), and
  the first two reach HTTP, MCP and the CLI.
- A `ChangeDependencyImpact` carries the `TraceabilityPath` from the changed requirement to the impacted entity; its
  length is the depth the analysis reports, and a step whose link is not `RESOLVED` raises the warning
  `TRACEABILITY_PATH_PARTIALLY_RESOLVED`.
- The analysis follows `DEPENDS_ON` only, outgoing for dependencies and incoming for dependents.
- Each read of the SQLite traceability store also reads the evidence of every link it returns, one query per link
  (`SqliteTraceabilityStore.java:252`, `:256-268`).

## Goals / Non-Goals

**Goals**: one traversal per requirement and direction; an unchanged analysis; a proof that runs on every build.

**Non-Goals**: the cost of one store read - the relation filter is AUD-PRF-12 (sprint 7), and the evidence query per
link is owned by no finding (see the open questions); the node and link budgets; exposing the analysis over HTTP or
MCP; the manifest line of `analyze-change`.

## Decisions

1. **The traversal records how it reached each node.** When a node is first discovered, the step that discovered it
   (the link, and the node it was reached from) is kept, and the path to any discovered node is rebuilt from those
   steps in memory. It is the search `findPath` runs, done once.
   *Rejected*: caching the store reads inside the service - it hides the square behind a map, keeps a thousand
   searches, and makes the service stateful.
   *Rejected*: memoising `findPath` per root - the same thing with a different key.

2. **The public shape of a traversal does not change.** The steps are not added to what `trace-requirement` and
   `change-context` serialise. Whether they travel in a second value returned beside the subgraph or in an operation of
   their own is the lot's choice; the test is that the three answers that embed a subgraph are byte-identical.
   *Rejected*: a new component on `TraceabilitySubgraph` - it would appear in two public answers that never asked for it.

3. **Equivalence is asserted, not assumed.** The argument for it (same order, same queue discipline) is reasoning. The
   lot keeps the per-node search long enough to compare: on fixtures with two shortest paths to one node, a cycle, a
   link that is not resolved, a traversal truncated by the node budget and one truncated by the link budget, the path
   rebuilt from the traversal equals the path `findPath` returns. Then `findPath` goes.
   A traversal truncated by the link budget is the case to watch: it stops recording links while `findPath` has no link
   budget. Every node it reports was discovered before it stopped, so its step was recorded; the comparison proves it.

4. **`findPath` is removed or excused, by name.** It will have no caller in production code. Its one behaviour that
   nothing else has - refusing to answer "no path" when the budget ran out - is covered by
   `TraceabilityTruncationContractTest`; if the method goes, so does that case, and the lot says so in the pull request
   rather than keeping a method alive for its test.

5. **The proof is a count, in the ordinary suite.** A test wraps the memory traceability store in a counting store,
   analyses a change over a dependency chain of `n` requirements and one of `2n`, and asserts the reads at most double.
   On `HEAD` they quadruple. It needs no timing and no SQLite.
   Enforcement mechanism: a behavioural test - the intent is "the number of store reads grows with the nodes found",
   a property of an execution. A textual ban on `findPath(` inside `ChangeAnalysisService` would hold the letter and
   miss the next loop written differently; it is not added.

## Risks / Trade-offs

- A traversal that also keeps one step per node holds at most `MAX_NODES` more references. Negligible.
- Removing the per-node search removes a hidden consistency check: today a node "without a path" throws
  `IllegalStateException`. Rebuilding from recorded steps cannot produce such a node except through a defect of the
  traversal itself; the same exception stays where the path is rebuilt.

## Open Questions

- **The evidence query per link read.** Every `outgoing` and `incoming` of the SQLite store issues one more query per
  link to read its evidence. It is an N+1 on the read side that no finding names; it multiplies the cost of every
  traversal, this one included. Recommendation: give it to sprint 7 with AUD-PRF-12, which rewrites the same query.
- **The number of requirement deltas is not bounded.** The analysis runs two traversals per delta. Each is bounded; their
  number is the size of the change being analysed. Recommendation: leave it - it is the caller's own data, on a
  command line - and let the budget say what a large proposal costs.
