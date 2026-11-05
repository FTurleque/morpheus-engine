# Design

## Context

- `SnapshotBusinessContent` is an immutable value: eight lists, each ordered by the identity of its elements; the
  ordered values inside an element (preconditions, scope, risks, targets, evidence) keep the order of their `ordinal`.
- The reader takes a `Connection` in a package-private constructor; `SqliteSnapshotBusinessContentStore` builds it once
  and calls `find` under its monitor.
- The seven child tables have the primary key `(snapshot_id, <owner>, ordinal)` (migrations V007, V009, V010).
- `find` issues its statements one after another outside a transaction. A published snapshot's content is never
  rewritten, so the statements see the same rows.
- The memory store returns the value it was given; the two stores are held to the same behaviour by the persistence
  parity tests.
- The table and owner-column names of the generic child read go through `requireSqlIdentifier`.

## Goals / Non-Goals

**Goals**: a statement count independent of the data; the same value read; a proof that runs on every build.

**Non-Goals**: reading only the part of the content a caller needs - every one of the 15 call sites loads the whole
snapshot content to use a fraction of it, and narrowing that is a change of the port, next to AUD-PRF-05 (sprint 13);
caching a snapshot's content between requests; any index change (sprint 7); wrapping the read in a transaction.

## Decisions

1. **One statement per child table, grouped in memory.** Each child table is read once for the snapshot, ordered by
   owner then ordinal, and its values are attached to their owner when the parent rows are mapped. The read then costs
   the header, eight parent tables and seven child tables, whatever the snapshot holds.
   *Rejected*: joining each parent to its children - a change has three child lists, and a join multiplies their rows.
   *Rejected*: keeping the per-owner queries with a statement prepared once - it removes the preparation, not the
   round trips, and the count still grows with the data.

2. **The value is the contract, not the SQL.** What must not change is the `SnapshotBusinessContent` returned: same
   elements, same order of elements, same order of values inside an element, an empty list (not an absent one) for an
   owner without child rows. The specification says that and leaves the queries to the lot.

3. **A child row without a parent is not silently dropped.** The foreign keys forbid it, but the reader does not
   enforce foreign keys itself. Grouping first and attaching second would discard such a row without a word where the
   per-owner query never saw it. The lot states which it is: the read ignores it as before, and says so in the reader.
   It is not turned into a failure here - a read that starts failing on a database that used to answer is a behaviour
   change this finding does not ask for.

4. **The proof is a count, in the ordinary suite.** A test in the reader's package gives the reader a connection that
   counts the statements it prepares, reads a small snapshot and a larger one, and asserts the two counts are equal.
   It needs no timing and no large fixture, so it runs in `clean verify` on both platforms. The M19 budget measures
   the time; this measures the shape.
   Enforcement mechanism: a behavioural test. Neither an ArchUnit rule nor a textual assertion fits - the intent is
   "the cost of this read does not grow with the snapshot", which is a property of an execution, not of a dependency
   or of a text.

## Risks / Trade-offs

- All child rows of a snapshot are held in memory before the elements are built. They were all going to be held in the
  result anyway; the peak is higher by the grouping maps only. The content-read budget runs under the validator's heap.
- Equality of the value on a fixture proves nothing about a field the fixture leaves empty. The parity fixture must
  fill every child list, with more than one value and an owner without any.

## Open Questions

- **Whether callers should stop loading the whole content.** Out of scope here; recommendation: decide it with the
  pagination carried by the port (AUD-PRF-05, sprint 13), once the content-read budget says what a whole read costs.
