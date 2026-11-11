# Design

## Context

- ADR-0085: budgets are predeclared on deterministic large fixtures, and it says where they run
  (the nightly lane, Linux and Windows, through `validate-m19`).
- `M19_PERFORMANCE_BUDGETS.md` is frozen. Its section 8 allows an optimisation to lower a time, forbids raising a
  threshold because the code misses it, and says a change of fixture or threshold needs a justification and
  invalidates the comparison with the previous baseline. It says nothing about adding a row.
- The protocol is fixed: one warm-up, five measured iterations, nearest-rank p95, `System.nanoTime()`, a heap of
  768 MiB for the whole run (`scripts/validate-m19.sh:238`), a reference environment below which no verdict is given.
- The capability `gate-reachability` refuses a gate class that no validator names, and its first scenario enumerates
  the five classes that exist today.
- `M19LargeFixtureSupport` holds the shared sizes; each gate builds its own content.
- `M19_METRIC` lines are the evidence the nightly artefacts keep.

## Goals / Non-Goals

**Goals**: a budget for each cost sprint 6 bounds, declared first; a fixture that holds the rows those costs grow
with; a recorded measurement of the uncorrected code.

**Non-Goals**: changing any existing budget, fixture size or protocol; running M19 in `clean verify`; a budget for the
costs of sprint 7 (index choice, per-row filters) or of sprint 13 (the requirement search, which already has its gate).

## Decisions

1. **Declare, then measure, then correct.** The three rows are written in the budget document with their date and
   their reason before the gate code exists. A value is chosen from what the operation is for - an interactive read,
   like the rows around it - not from a run. The first run is on `HEAD` as it is: a miss is the reproduction the
   correction starts from; a pass is recorded as such, and the correction is then justified by its count proof and not
   by a time it did not need.
   *Rejected*: one budget per correction, written with the fix. It measures the fix and calls the result a budget.

2. **The new measurements live in a database of their own.** Adding 2,000 changes and 4,000 criteria to the content the
   publication gate publishes would move the publication time, the database size and the retention growth, three
   frozen measurements. The content-read measurement publishes its own snapshot, in its own file, and reads that.
   *Rejected*: enriching `largeContent()` in place - it is the fixture change section 8 warns about.

3. **Methods in the existing gate classes, unless a subject has none.** The content read sits with the publication
   gate (same stores), the change analysis with the traceability gate (same graph builder). Neither needs a validator
   edit. The journal read has no gate of its subject; if it becomes a sixth class, both validators name it, the
   reachability gate proves it, and the enumeration of `gate-reachability` is brought up to date when this change is
   archived. *Rejected*: one new class per budget by default - three validator edits on two platforms for no gain.

4. **What each fixture must hold, so the budget measures the cost in question.**
   - Content read: the declared numbers of changes and acceptance criteria, each change with scope, out-of-scope and
     risk values, each constraint with blocking targets and supporting evidence, each criterion with verification
     evidence, each scenario with preconditions. A child table left empty measures nothing.
   - Change analysis: a proposal whose requirement deltas each reach a dependency subgraph at the traversal's node
     budget, in both directions. The existing mesh (five links out of each of 5,000 nodes) uses `RELATED_TO`; the
     analysis follows `DEPENDS_ON` only, so the gate needs links of that type.
   - Journal page: a journal long enough that reading it whole and reading one page differ measurably; the size is
     declared with the row.

5. **ADR-0085 holds it**, no new ADR: a budget may be added after the freeze when a cost is found without one; it is
   dated, it is declared before the code it will judge is changed, and it never alters an existing row.

## Risks / Trade-offs

- The three fixtures add time and heap to a run that has a heap budget of its own. The heap budget is a frozen row: if
  the new fixtures do not fit, they are made smaller and the row says so; the heap is not raised.
- A budget chosen without a run can be wrong by an order of magnitude. Section 8 already says what to do: a
  measurement protocol that was objectively wrong may be corrected, with its history; a threshold is not raised
  because the code misses it.
- A nightly proof is late by construction. That is why the corrections carry count proofs in the ordinary suite.

## Open Questions

- **The three values.** Recommendation: the content read and the journal page at the bound of the other interactive
  reads of the table (the requirement search row), the change analysis at the bound of the traversal row multiplied by
  nothing - one analysis is a handful of traversals and should cost a handful of them. The maintainer writes the
  numbers; this specification deliberately does not.
- **The journal budget may not be worth a gate.** A page read through an index is fast at any size, and its count proof
  is exact. Recommendation: keep it if the journal correction adds an index or a migration, because then the plan of
  the query is what is being trusted; drop it, with the reason written in the budget document, if not.
