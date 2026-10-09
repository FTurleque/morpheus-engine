# Design

## Context

Measured on `develop` at `6456a9e0` (see proposal.md — Why):

| Situation | `MemoryPolicyPackStore` | `SqlitePolicyPackStore` |
|---|---|---|
| Remove a missing activation | `IllegalArgumentException("policy activation does not exist: <pack>")` | `PolicyConflictException("stale policy activation revision: expected N but current is 0")` |
| Remove a missing override | `EntityNotFoundException("policy override does not exist: <rule>")` | `PolicyConflictException("stale policy override revision: …")` |
| Check order on a removal | existence, revision, then audit | audit target, then revision, then audit version |
| Update skipping a revision step | `IllegalArgumentException("policy update must advance revision and version by exactly one")` | `KnowledgeStoreException` raised by the V018 trigger `trg_policy_packs_revision_step` |

Closed SQLite stores: `SqlitePolicyPackStore`, `SqliteSavedViewStore` and `SqliteCompositionStateStore` throw
`IllegalStateException`; the nine others throw `KnowledgeStoreException` with a "SQLite … store is closed" message.

The callers of the three diverging stores catch `KnowledgeStoreException` everywhere: the policy HTTP routes and MCP
tools catch it alongside `IllegalStateException`, and `MorpheusCompositionMcpTools` catches
`IllegalArgumentException | KnowledgeStoreException` only. `PolicyPackService` checks existence (`EntityStateException`)
and revision (`PolicyConflictException`) before calling a store, so the service's refusals do not change.

`SqlitePolicyPackStoreAtomicityTest` pins today's SQLite refusals, among them `PolicyConflictException` on a missing
activation and `IllegalStateException` on a closed store. Its assertions change on purpose.

## Goals / Non-Goals

**Goals:**

- One refusal per situation across the two policy stores, asserted by the parity test that already compares them.
- One closed-store refusal across the twelve SQLite stores.

**Non-Goals:**

- Changing what `PolicyPackService`, HTTP, MCP or the CLI answer: their refusals are decided before a store is called.
- Removing the V018 triggers: they stay as an integrity backstop for any writer that bypasses the stores.
- The memory stores' lifecycle: they have no `close()`.

## Decisions

1. **A missing row is refused with `EntityNotFoundException`** (maintainer decision, 2026-10-09). It names the actual
   situation, as `unknown policy pack` and `unknown portfolio` already do. Alternative set aside: SQLite's
   `PolicyConflictException` treats absence as a revision of 0, which is true of the CAS arithmetic but tells the
   caller something false — there is no row to be stale against.
2. **Audit target first, then stored state, then audit version** (maintainer decision). The caller's input is
   validated before the state is read, so a malformed audit is refused the same way whether the row exists or not.
   The audit version is checked last because it is compared with the stored row's version. Alternative set aside: the
   memory store's order, which lets a missing row hide a malformed audit.

   Where the audit version comes from decides where it is checked. When it is the caller's own input — the new version
   of `create` and `compareAndSetDefinition`, the version being activated, the absent version of a removed override —
   it is checked with the target, before any state is read. When it must match a stored row — the active version on
   `removeActivation` and `compareAndSetOverride` — it is checked after that row was found. A deactivation whose audit
   names no version therefore reports a missing activation before the version mismatch: there is nothing to compare
   it with.
3. **A skipped revision step is checked explicitly in SQLite** with the memory store's message, before the
   transaction. The trigger keeps raising if a write bypasses the store. Alternative set aside: mapping the trigger's
   `SQLException` back to `IllegalArgumentException`, which would depend on parsing a driver message.
4. **A closed SQLite store throws `KnowledgeStoreException("SQLite <name> store is closed")`, with no cause**
   (maintainer decision). Nine of twelve stores already do, and it is the type every transport handler of these
   stores catches. Alternative set aside: `IllegalStateException` everywhere, which would require widening
   `MorpheusCompositionMcpTools` and the other handlers that catch `KnowledgeStoreException` only.

## Risks / Trade-offs

- **A caller relying on today's SQLite `PolicyConflictException` for a missing row** would now see
  `EntityNotFoundException`. Searched: no production caller removes a row without the service's existence check.
- **`KnowledgeStoreException` also wraps real I/O failures.** A closed store stays distinguishable by its message and
  its missing cause, which the closed-store tests assert.
- **Explicit SQLite revision checks read before they write**, inside the same `synchronized` method; the CAS predicate
  of the `UPDATE` and the trigger still guard a concurrent writer from another process.
