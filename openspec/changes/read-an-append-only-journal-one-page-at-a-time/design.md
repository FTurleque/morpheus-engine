# Design

## Context

- ADR-0107: one vocabulary for a paginated response (`offset`, `limit`, `totalMatches`, `hasMore`, `items`), spelled
  once per adapter (`PagedEnvelope`), with `PagedResponseVocabularyArchitectureTest` holding the copies together. Its
  amendment MCP-2 paged three growing collections for MCP only - the policy audit, the versions of a policy pack, the
  versions of a saved view - by slicing a list already read.
- ADR-0108: an answer says what it could not observe. A list cut without saying so is what it forbids.
- `policy-audit-order`: the audit is returned by instant, then by identity; the stored format of `at` does not change.
- `PolicyPackService.audit` checks the pack exists, then sorts what the store returns - a second sort of a sorted list.
- `policy_audit(id TEXT PRIMARY KEY, ..., at TEXT)` with `idx_policy_audit_pack(pack_id, at, id)`;
  `change_lifecycle_mutation_audit` is ordered by an integer revision its index carries; `sync_source_archives` is
  ordered by `archived_at`, another `Instant.toString()`.
- The HTTP routes read `offset` and `limit` with `rejectUnknown` and a bounded `intValue`; the MCP bounds are
  `PageArguments`'; `PageBoundsAgreementTest` ties the MCP maximum to `PageRequest.MAX_LIMIT`.
- The remote proxy forwards the query string (`MorpheusRemoteProxyTargetResolver.java:72-74`) and bounds the answer.
- `remove-unreachable-and-unreferenced-code` requires a public method of a product module to be referenced or excused.

## Goals / Non-Goals

**Goals**: a policy audit page read at the store; one behaviour on three transports; no whole-journal read reachable
from a surface; the keeping of journals written down.

**Non-Goals**: purging anything; a cursor - the vocabulary is offset and limit, and a second one is what ADR-0107
refuses; the versions of a policy pack and of a saved view (open question); the double sorts as such (AUD-PRF-13,
sprint 7); the requirement search (AUD-PRF-05, sprint 13).

## Decisions

1. **The store port takes the page for the policy audit, and no longer offers the whole journal.** It returns the
   records of the page and the total for the pack. The memory store slices its sorted list - it is test tooling and
   must only agree; the SQLite store bounds the query. Tests that read the whole journal read it page by page.
   *Rejected*: keeping the unbounded method beside the paged one - the next caller picks the short signature.

2. **An order the database can follow.** Three ways, and the choice is the maintainer's:
   - *(a) Order by an expression that normalises `at`*, with an index on that expression. No stored data changes; every
     query must repeat the expression exactly, and its correctness rests on string arithmetic over a timestamp.
   - *(b) A stored sortable key*, written by the store with each record and filled for existing rows by the migration
     that adds it, with an index `(pack_id, key, id)`. The text of `at` is untouched, so the history scenario of
     `policy-audit-order` holds as written. It is a schema version, with everything that comes with one.
   - *(c) No schema change*: read the identity and the instant of every record of the pack - two short columns - sort
     them with the comparator the memory store uses, and fetch the full rows of the page only. The order is right by
     construction; the read still grows with the journal, by a narrow row instead of a whole one.
   Recommendation: (b). It is the only one under which the read is bounded at the store, which is what the sprint's
   exit criterion says; (c) is the honest fallback if a schema version is not wanted in a corrective line, and must
   then be recorded as a residual, not as the criterion met. (a) is rejected.
   Whatever is chosen, the proof is the same: pages concatenated equal the order of `policy-audit-order`, on timestamps
   whose text sorts differently from time, across a page boundary, on both stores.

3. **HTTP and the CLI answer with the page.** `offset` and `limit` as query parameters and as options, the bounds and
   the default of the other paged routes, the envelope of `PagedEnvelope`. It is a break of form for both - a bare
   array becomes an object - announced in the release notes, as MCP-2 announced the same break for MCP.
   *Rejected*: keeping the array and capping it at a default - a journal cut without `hasMore` is the silent
   truncation ADR-0108 forbids. *Rejected*: a new route beside the old one - two answers for one capability, and the
   unbounded one stays.

4. **The two journals without a reader are fenced, not paged.** Nothing calls the lifecycle audit list or the archive
   list outside tests. Paging a method nobody calls would be designing an order (and, for the archives, solving the
   timestamp problem a second time) for no caller. Instead a rule refuses any production class that calls either; the
   surface that one day needs them bounds them first.
   Enforcement mechanism: an ArchUnit rule - the intent is "no production class depends on these two methods", a
   dependency, and a method call cannot be inlined away. The lot checks the calling modules are in the imported set
   and breaks the rule once.
   These two methods are also what `dead-code-refusal` will meet: referenced by tests only. They are excused there by
   name with this reason, or removed from the port with their tests rewritten - the lot says which.

5. **Journals are kept, and the code says so.** No statement of the product deletes from the three tables today. A
   textual assertion holds that - the intent is "this SQL does not appear in the sources", and SQL lives in strings no
   bytecode rule reads. A decision record states, per journal, that it is kept for the life of the database, what an
   operator can do about its size (the offline backup and restore they already have), and what would reopen the
   question.

6. **The second sort goes with the first.** Once the store returns a page in order, `PolicyPackService` sorting it
   again is harmless and misleading. It is removed here because this change rewrites both lines; what AUD-PRF-13 names
   beyond that - the archive list sorted in SQL then in Java - is left to sprint 7.

## Risks / Trade-offs

- Offset paging walks the index to the offset: a deep page costs more than a shallow one. It materialises only the
  page, which is the heap the finding is about; the walk is recorded, not hidden.
- A page read while a record is appended can shift; ADR-0107 already says so and `totalMatches` shows it.
- The break of form reaches every HTTP and CLI client of the audit. It is one route and one command, both read-only.
- A migration in this change meets sprint 7's (AUD-PRF-07 drops indexes): two lots must not take the same migration
  number, and the supported schema version is read from its constant, never copied.

## Open Questions

- **The order key** (decision 2). Recommendation: (b).
- **The other two collections of MCP-2.** The versions of a policy pack and of a saved view are in the same state as
  the audit - paged for MCP in memory, whole over HTTP and the CLI - and AUD-PRF-04 does not name them. Their order is
  an integer the database already follows, so they are the easy half. Recommendation: decide the three together in
  the amendment of ADR-0107, so clients meet one break and not two; implement the two others in a lot of their own.
- **What the archive journal is for.** The product writes it and never reads it. Recommendation: keep writing it - it
  is the trace that a source disappeared - and say in the decision record that it has no reader; a reader is a
  capability to propose, not a side effect of this change.
- **How the port spells a page.** Recommendation: the simplest thing both this change and AUD-PRF-05 can use, decided
  by whichever lands first and written in ADR-0107's amendment.
