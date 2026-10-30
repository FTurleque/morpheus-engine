# Design

## Context

- A link has a definition (`traceability_links`), a set of evidence (`traceability_link_evidence`) and a membership
  per snapshot (`snapshot_traceability_links`). A definition is shared by the snapshots that hold the link; the
  membership insert is `INSERT OR IGNORE`.
- `putLink` and `putLinks` share `putLinkInternal`. A definition that exists and differs raises
  `KnowledgeStoreException("traceability link identity collision: <id>")`.
- `SqliteTransactionRunner.runVoid` rolls the transaction back on any failure, `Error` included.
- SQLite bounds the number of host parameters of one statement.
- The publication calls four stores in sequence, each with its own transaction; `putLinks` is one of them, and a
  failed publication is recovered by the snapshot lifecycle, not by a shared transaction.
- `SqliteSnapshotBusinessContentWriter` and `SqliteSyncStateStore.insertArchives` already prepare once and batch.

## Goals / Non-Goals

**Goals**: a constant number of prepared statements per batch; written and tested batch semantics; the same refusal
on both stores.

**Non-Goals**: splitting the batch into several transactions - a snapshot that holds half of its links is worse than
a slow one; one transaction for the whole publication; the single-link `putLink`, which stays as it is; sharing
definitions between snapshots at publication (the publication generates new identities, a decision of its own); the
read side.

## Decisions

1. **Write the semantics before changing the cost.** Four properties hold today in SQLite and are stated nowhere:
   the batch is all or nothing; a link already defined identically is not rewritten and joins the snapshot; a link
   defined differently under the same identity refuses the whole batch and names the identity; a link listed twice in
   one batch, identically, is stored once. They become tests first, on both stores, and the batching is then free to
   change everything else.

2. **Statements are prepared once per batch and executed in slices.** The definitions, the evidence and the
   memberships are each written through one prepared statement with `addBatch`, flushed in slices of a fixed size so a
   large batch does not build an unbounded buffer. All slices stay in the one transaction.
   *Rejected*: one multi-row `INSERT` per slice - it meets the parameter bound sooner and gains little over a batch on
   this driver.

3. **The existence check stops being a statement per link.** The lot chooses between reading the identities already
   present for the batch (in slices, under the parameter bound) and letting the insert report the conflict, then
   comparing only the conflicting definitions. Either way a link that is new costs no lookup of its own, and a link
   that exists is still compared field for field, evidence included, before it is accepted.
   The specification fixes the outcome - the collision is still refused, with the same message - not the technique.

4. **The memory store gets the same batch.** It overrides `putLinks` and validates the whole batch before storing any
   of it. Whether the port keeps a default implementation is the lot's choice; a default that silently gives a third
   store the non-atomic behaviour is the trap, so the recommendation is to make the method abstract.
   This is test tooling, and it is the store every unit test of the publication runs on: a parity gap there hides the
   behaviour the SQLite store has.

5. **The proof is a count, in the ordinary suite, and the budget at night.** A test gives the store a connection that
   counts prepared statements, writes a batch of a few links and a batch of many, and asserts the counts are equal.
   The publication budget then says what the change is worth on 25,000 links.
   Enforcement mechanism: behavioural tests. The intent is "the cost of a batch does not grow with it per link" and
   "a refused batch stores nothing", properties of an execution; no dependency rule or text scan expresses them.

## Risks / Trade-offs

- Batching defers constraint failures to the flush: a foreign-key or uniqueness failure is reported for a slice, not
  for a link. The collision message must still name the identity, which is why the comparison of decision 3 is explicit
  and not left to the constraint.
- The gain may be small. The store's connection already runs the batch in one transaction, which is where most of
  SQLite's write cost goes; removing three prepared statements per link is real but unmeasured. The first task
  measures it, and a gain within the noise of the budget is reported as such rather than dressed up.
- The store's constructor takes a path, not a connection: the counting connection of decision 5 needs a seam. It is
  package-private and for the test, like the reader's.

## Open Questions

- **Does the publication need the lookup at all?** It generates a new identity per link, so on its only production
  path no definition can pre-exist. Recommendation: keep the check - the port promises it, `putLink` shares it, and an
  identity collision is exactly the failure that must not become a silent overwrite - and make it cheap.
