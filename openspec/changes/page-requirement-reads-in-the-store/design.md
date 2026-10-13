# Design

## Context

- `requirement_versions` (migration `V004`) holds one row per requirement version with `requirement_key`, `title` and
  `statement` as plain columns. Its indexes are on `snapshot_id`, on `entity_identity_id`, and a unique partial index
  on `(snapshot_id, entity_identity_id)` for CURRENT rows. Nothing indexes `requirement_id`.
- The three paged reads do not share an order: the search and the specification context order by requirement
  identity, the historical read by entity identity. The stores' own lists order by entity version identity.
- Identities are RFC 9562 version 7 UUIDs (`DomainIdentity`), compared in Java by `UUID.compareTo` and stored as
  their canonical text. The two orders agree for these identities; nothing asserts it.
- `PageRequest` bounds the limit (`MAX_LIMIT`), not the offset. The OpenAPI `Limit` parameter declares the same
  bound; `Offset` declares a minimum only.
- A published snapshot is ACTIVE or RETIRED; the search refuses any other state by name.
- The port already uses default methods so that a third-party or test implementation keeps compiling; six
  hand-written implementations of `VersionedRequirementStore` exist under `morpheus-application/src/test` alone.
- `CurrentRequirementReaderArchitectureTest` names the classes allowed to call `listRequirementVersions`, refuses a
  new caller and refuses a listed class that stopped calling it.
- `application.store` already imports `application.query` types; the port and the page types are in packages that
  depend on each other today.
- No `*PersistenceParityTest` covers requirements. `RequirementQueryContractTest` compares the two stores on one
  ASCII fixture.

## Goals / Non-Goals

**Goals**: the page is cut by the store; the three reads return exactly what they return today; the two stores cannot
diverge; the cost of a deep page is measured by a gate that runs.

**Non-Goals**: the other paginations cut in memory - business content, constraint evaluations, the query DSL,
portfolio, composition conflicts, the version lineage, the specification list. They cut a page out of the snapshot's
business-content projection or out of a computed result, not out of a table read row by row, and the read they sit on
belongs to sprint 6 (AUD-PRF-01). Also out: the six remaining whole-population readers (quality, completeness,
comparison), which compute over every requirement; a full-text index; a cursor in the public contract; removing or
weakening `totalMatches`.

## Decisions

1. **One port operation: criteria, order, page in, items and total out.** The criteria are the two the reads use
   today - the terms of a lexical query, and a specification. The order is named by the caller among the two the
   reads publish today, and is total: ties are broken by entity version identity, so two rows can never exchange
   places between two pages. The total is the number of rows the criteria match, independent of the page.
   *Rejected*: passing a Java predicate to the store. It keeps exact semantics for free but the store must build a
   record for every row to evaluate it, which is the cost the finding is about.
   *Rejected*: returning the page without the total and counting in the service. Two reads of a store that another
   writer may touch between them; and the count is the scan.

2. **The observable result is frozen first.** Before the port changes, a characterisation suite pins, through the
   services, what the three reads return on a fixture that holds what the obvious SQL gets wrong: upper-case accented
   and non-Latin letters in the stored text and in the query, `%`, `_` and `\` in both, a requirement without a key,
   a term that would only match across two fields, an offset equal to and beyond the total, the last partial page.
   It passes on `HEAD` on both stores and is the definition of "unchanged".

3. **Parity is a suite of its own.** A `RequirementPagePersistenceParityTest` beside the three existing parity
   suites runs the same calls on the port against both stores and compares items, order and total. The memory store
   implements the operation with the application's own normalisation; it is the reference.

4. **How SQLite evaluates the match is chosen by measurement, under the constraint of decision 2.** The options:
   - *An application-defined SQL function* that applies the application's normalisation, registered on the store's
     connection. Exact, no schema change; a callback per candidate row. Whether the pinned driver supports it under
     the connection policy of `own-the-sqlite-connection-policy-in-one-place` was **not verified** by this
     specification - task 1.3.
   - *A persisted, normalised search column* written with the row. Exact, pure SQL, the base of a later index; it is a
     schema migration with a backfill, so it carries the upgrade statement and the backup of the two sprint 5 changes
     and an ADR.
   - *SQLite's own folding.* Refused: it changes what a search returns and breaks parity.
   - *No predicate in SQL*: the empty query - every list without a search term - is answered entirely in SQL, and a
     lexical query streams rows through the Java match without keeping them. The fallback if neither of the first two
     survives measurement; it still removes the full materialisation for the commonest call.
   Recommendation: the function, if 1.3 holds; the column only if the gate of decision 7 shows the callback is the
   cost. The specification binds the behaviour, not the option.

5. **The shipped stores implement the operation; the default does not hide a missing one.** The port keeps a default
   so that existing implementations compile, and that default answers correctly from the whole population. A rule
   requires `morpheus-store-sqlite` and `morpheus-store-memory` to declare the operation themselves. Mechanism:
   ArchUnit - the intent is *these classes declare this method*, a property of the compiled classes, both of which
   are in the imported set.

6. **The whole-population readers are named.** `CurrentRequirementReaderArchitectureTest` gains a second list, of the
   same form as the first: the classes allowed to call the unbounded CURRENT read, each with the reason it needs
   every requirement; a new caller is refused, a listed class that stopped calling is refused. The three paged
   services and `MorpheusMcpToolService` are not in it. Mechanism: ArchUnit on the method call, with the overload
   told apart by its parameter types - the intent is *no dependency on this method*; none of the names involved is a
   compile-time constant.
   Re-measure the callers on the day: nine classes on 2026-10-13 against the audit's eight.

7. **The budget measures what the change is for.** `M19QueryPerformanceGate` keeps its scenario and its budget and
   gains two: the last page of the same query, and a query that matches nothing. The budget is never raised; whether
   it is tightened is decided from the measurement of both platforms, in the nightly lane where the gate runs.
   Run the gate on `HEAD` before the change: without a number before, there is no number after. The two scenarios
   are declared against the existing budget before the store is changed, as ADR-0085 asks and as sprint 6 does for
   its own reads in `declare-a-budget-before-bounding-a-cost`.

8. **The count is a count.** The MCP header that reports how many CURRENT requirements a snapshot holds asks the port
   for that number. It is the same operation with a page that holds nothing, or a method of its own; the lot chooses,
   the rule of decision 6 is what holds it.

9. **The adapter stops cutting requirement pages.** `MorpheusHistoryApiService` passes its page to the application.
   `PagedEnvelope.slice` stays for the lists it still serves; it is not removed by this change.

10. **Where this change stops against its neighbours.**
    - *Sprint 6* (AUD-PRF-01, 02, 04, 06) owns the reads that load a whole table or a whole projection. This change
      owns the page of `requirement_versions` and nothing else. AUD-PRF-04
      (`read-an-append-only-journal-one-page-at-a-time`) paginates append-only tables; a published snapshot does not
      grow, so offset pagination is correct here and the public contract already speaks it. The two do not need one
      vocabulary.
    - *Sprint 7* owns indexes (`serve-ordered-store-reads-from-an-index`). This change adds none. That change's
      proposal expects the paged reads of this one to be served by its index on `(snapshot_id, entity_version_id)`:
      they are not ordered by that key. The historical read orders by entity identity, which the existing partial
      unique index can serve; the search and the specification context order by requirement identity, which no index
      serves. If the gate of decision 7 shows the sort is the cost, the index on that key is asked of sprint 7's
      migration, not added here.
    - *Sprint 11* decides where the types of the ports live (AUD-ARC-01 in
      `decide-what-technology-the-application-core-may-touch`, AUD-ARC-03 in
      `let-application-package-cycles-only-decrease`). This change adds one operation and its value types to the
      package where the port's types live on the day, and adds no dependency between two application packages that
      does not already exist. Delivered after sprint 11, as the audit orders it; if it has to land before, the types
      move with the others.

## Risks / Trade-offs

- **The scan stays.** With an exact total there is no implementation that does not evaluate every CURRENT row of the
  snapshot. The change is worth what the measurement says, and the pull request says the number.
- **Order by text against order in Java.** The two agree for version 7 identities written in canonical lower case.
  The parity suite carries identities chosen to tell the two orders apart if they ever stop agreeing.
- **A function registered per connection** depends on who opens connections; that is the junction with the
  connection-policy change and is designed with it.
- **A default that answers correctly is a default that can be slow.** Decision 5 holds the two shipped stores;
  a third-party store keeps the old cost and the old result.
- Deep offsets cost what they skip. The offset has no declared maximum today; see the open questions.

## Open questions left to the maintainer

- **Is an exact `totalMatches` worth the scan?** Recommendation: yes, keep it. It is a required field of a published
  response, three surfaces print it, and the fixture of the budget is ten thousand rows. Reopen only if the
  measurement says otherwise, and then as a contract change through the `public-capability` procedure.
- **Should `Offset` declare a maximum in the OpenAPI?** The governance rule asks for explicit bounds and this
  parameter has only a minimum; the CLI accepts any non-negative integer. Recommendation: a separate change - it is a
  contract change on every paged route, not on requirements.
- **A persisted search column.** Recommendation: not without the measurement of decision 7, and not without its ADR.
