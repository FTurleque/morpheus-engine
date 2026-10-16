# Design

## Context

- A `SqliteConnectionScope` binds one physical connection to the current thread; stores built inside it share that
  connection and skip the schema check after the first.
- A store built outside a scope opens a connection of its own, hardens it and checks the schema.
- `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` is the measured set of classes allowed to reach a concrete
  store; it only shrinks.
- The pre-migration copy (ADR-0110) is taken on the scope's connection when one is active.
- The CLI's maintenance commands (`MorpheusServerCli`) work on the database file itself - backup, restore - and are not
  ordinary operations.

## Goals / Non-Goals

**Goals**: one connection and one schema check per operation on every transport; a rule that fails when a site opens
stores outside a scope; the same transactional reading for an operation whichever transport carries it.

**Non-Goals**: a connection pool; a long-lived connection per process; changing the journal mode; nesting scopes (the
refusal stays - it is made explicit and tested).

## Decisions

1. **The MCP runtime first.** It opens a scope, then its seven stores inside it. It is the most expensive site and the
   simplest to align; its own lot, with a measure before and after of connections opened per tool call.

2. **One runtime per adapter, by composition, not by inheritance.** The runtime takes the set of stores an operation
   needs and opens exactly those, inside the scope. `MorpheusQueryApiService`'s inner runtime, which duplicates
   `ApiRuntime` with another set of stores, is absorbed. *Rejected*: one runtime that opens every store for every
   operation - it would trade seven connections for seventeen store constructions.

3. **The maintenance sites are named exceptions.** Backup, restore and the server lease act on the file, outside any
   operation; they are listed with their reason, as the composition roots are.

4. **The rule.** Structural: a class of `api`, `mcp` or `cli` that depends on a concrete SQLite store also depends on
   the scope, or is one of the named exceptions. Textual where structure cannot say it: a `new Sqlite...Store(` in an
   adapter appears lexically inside a try-with-resources that opens the scope. Both, because they do not see the same
   thing (ADR-0103); each is broken once.

5. **Nesting is refused by a tested property**, and its converse is proved: two runtimes of one adapter built in
   sequence on one thread both work. A route that needs two runtimes at once is a defect the test names.

6. **ADR-0109 is amended**, not replaced: the composition roots that become users of a runtime leave its list.

## Risks / Trade-offs

- Sharing one connection changes what an MCP tool observes between two stores: reads that were independent become one
  transaction's view. That is the point of convergence with HTTP, and the MCP tool tests are the proof.
- A scope is thread-confined; if the scheduler change moves database work to a pool of platform threads, the scope must
  be opened on the thread that does the work. The two changes are designed together.
- The pre-migration copy is taken once per scope instead of once per store: the store tests of ADR-0110 must pass
  unmodified.
