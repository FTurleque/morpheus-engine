# Design

## Context

- `TraceabilityStore.outgoing` and `incoming` take a set of relation types; an empty set means every type. The memory
  store filters a stream (`MemoryTraceabilityStore.java:65`, `:80`); the SQLite store reads every link of the endpoint
  in the snapshot and filters the mapped objects. Both return links ordered by link id.
- `TraceabilityRelationType` is an enum; its names are what `relation_type` stores. The filter can never hold more
  values than the enum has constants.
- `traceability_links` holds link definitions shared between snapshots; `snapshot_traceability_links` says which links
  a snapshot contains. The endpoint read joins the two.
- `sync_source_archives.archived_at` and `policy_audit.at` are `Instant.toString()` texts. `:00.500100Z` sorts before
  `:00.500Z`, and `:00Z` after both: the text order is not the time order.
- `policy-audit-order` is the precedent: order by instant then identity, same on both stores, stored format unchanged.

## Goals / Non-Goals

**Goals**: the database discards the links a relation filter excludes; a listing is ordered by one mechanism, the one
that is right; the order of the archives is stated and tested.

**Non-Goals**:

- Bounding or paginating `listArchives`, `listAudit` or the lifecycle audit: sprint 6 (`AUD-PRF-04`).
- Reducing the number of endpoint reads a traversal makes: sprint 6 (`AUD-PRF-02`).
- Changing how an instant is stored. A fixed-width text or a numeric column would make the SQL order true and is the
  natural companion of a `LIMIT`; it is a migration and a decision for the change that bounds these tables.
- `listSnapshots`, which orders by `created_at` in SQL with no sort afterwards: the same defect with a visible effect,
  outside the seven findings of this sprint. It is reported, not fixed here.

## Decisions

1. **The filter becomes `AND l.relation_type IN (?, ...)`, one placeholder per value, values bound.** The statement
   text depends on the number of values, never on a value. The names are bound in a fixed order (the enum's) so the
   same filter always produces the same statement. An empty filter produces today's statement unchanged.
   *Rejected*: concatenating the enum names into the text - they are safe today, and it is the shape
   `rules/security.md` teaches to distrust. *Rejected*: a temporary table or `json_each` - machinery for a list bounded
   by an enum.

2. **The Java filter goes.** Keeping it "to be safe" would leave two filters that must agree and one that hides a
   defect in the other. The parity tests against the memory store are the proof that the SQL filter is right.

3. **`ORDER BY l.link_id` stays.** With several relation types the index returns links grouped by type, so the order by
   link id still needs a sort over the links kept; it now sorts fewer. Nothing here promises a plan without a sort.

4. **`listArchives` loses its `ORDER BY`, not its Java sort.** The Java comparison is the definition
   (`SourceArchiveRecord.compareTo`) and the one the memory store uses. *Rejected*: removing the Java sort and trusting
   SQL, the audit's other branch - it would return `:00.500100Z` before `:00.500Z`. The reason is written above the
   statement in the words the policy store uses, so the next reader does not put the clause back.

5. **The order is specified, not only the absence of a clause.** A requirement that only said "no `ORDER BY` here"
   would be met by a store that returns archives unordered. The requirement states the order and its scenario writes
   the three timestamps of the policy precedent; the absence of the clause is a second, textual, assertion.

6. **Enforcement.**
   - Relation filter: behaviour by parity tests on both stores; the claim "the database does the filtering" by the
     query plan of the filtered statement, read through the product's driver, whose search on the endpoint index names
     `relation_type`. It is a property of a planner, so neither ArchUnit nor a source scan can state it.
   - Archive order: behaviour by a test on both stores. The absence of `ORDER BY` in `listArchives` is textual by
     nature (ADR-0103: "this clause must not appear here"), scoped to that statement, not to the file - other
     statements of `SqliteSyncStateStore` order legitimately.

## Risks / Trade-offs

- **The planner may prefer the snapshot membership as the outer loop for some filter.** Today it drives from the
  endpoint index. Task 1.2 reads the plan for one, several and all relation types, in both directions.
- **A filter that names every type** is equivalent to the empty filter but takes the `IN` path. It is correct; it is
  not special-cased.
- **Removing the `ORDER BY` changes the index chosen.** Observed: still `idx_sync_source_archives_project_time`,
  searched on `project_id`. Whether that index keeps a purpose is a question for the change that bounds the table.
- **Sprint 6 may delete or reshape `listArchives`.** It has no production caller. If the bounding change removes it or
  gives it a sortable key, the archive half of this change reduces to the order requirement, which holds either way.

## Open Questions

1. **Should the archive order live in its own capability, beside `policy-audit-order`?** Recommendation: keep it here
   until sprint 6 has decided how the three append-only tables are bounded; then one capability can state the order of
   all three and this requirement moves into it at archive time.
2. **`listSnapshots` orders by `created_at` text in SQL and is not re-sorted, while the memory store compares
   instants.** Recommendation: a change of its own, with the reproduction first (two snapshots of one project within
   the same second, one with a shorter fractional part); it is a determinism defect, not a performance one.
