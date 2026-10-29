# Proposal

## Why

AUD-ARC-03 (sprint 11) reports a strongly connected component of 17 of the 36 packages of `morpheus-application`,
with no rule to stop it growing: ADR-0109 measured 167 cycles over 16 slices on 2026-10-08 and deferred the rule
(decision 3). Re-read on `develop` at `8b26d3d7` on 2026-10-13, by an import graph of `src/main/java` (ArchUnit
cannot be run in the session that wrote this; the lot measures with it first), the finding **holds, and its cause is
not the one the audit names**:

- 36 declared packages, one component of the same 17 as the audit lists; six direct mutual pairs, the same six.
- Sliced as ADR-0109 slices (`com.morpheus.application.(*)..`): 29 slices, one component of **16** - the figure of the
  ADR - held together by 47 slice-to-slice dependencies.
- The audit's "single cause" is that `application.store` holds the layer's exception vocabulary
  (`EntityNotFoundException`, `EntityStateException`, `KnowledgeStoreException`), and its first action is to move
  those three classes. **Moving them opens no cycle.** They are targets of dependencies *into* `store`; every package
  that imports one also imports a store port, so the dependency remains.
- What closes the cycles is the opposite direction: `application.store` depends on exactly three other packages, and
  only through three port interfaces that import the types of the capability they serve - `PolicyPackStore` (4 types
  of `policy`), `SavedViewStore` (5 types of `query.saved` and `query.dsl`), `SyncStateStore` (5 types of `sync`).
- Removing those three dependencies from the import graph leaves two components, of 5 and 3 slices, and 10
  dependencies in a cycle instead of 47. A projection from imports, to be confirmed on the bytecode.

The count the deferral recorded is also a poor thing to ratchet: the number of elementary cycles is combinatorial -
one dependency between two already-cyclic slices adds dozens - and ArchUnit reports it up to
`archunit.cycles.maxNumberToDetect`, which the measurement of 2026-10-08 had to raise by hand and which no committed
configuration sets.

## What Changes

- The dependencies between application packages that lie on a cycle become a named, exact list in the architecture
  suite: a new one fails, and one that disappeared fails until its name is removed. The list is the ratchet and its
  own living source; no count is written anywhere as a threshold.
- The package of the store ports stops taking part in any cycle: a port lives with the capability whose vocabulary it
  speaks, or does not speak it. This is the first decrease.
- A consolidated ADR replaces ADR-0109: decision 3's deferral becomes the rule, with the measurement of the day.

## Capabilities

### New Capabilities

- `application-package-cycles`: which dependencies between application packages may lie on a cycle, and how that set
  changes.

### Modified Capabilities

(none)

## Impact

- **A consolidated ADR replaces ADR-0109** (decision 3 and its measurement table); it takes the next free number.
- `AdapterCompositionRootArchitectureTest` or a sibling class in `morpheus-architecture-tests`;
  `architectureTestsMinimum` rises.
- Three port interfaces of `application.store` and their implementations in `morpheus-store-memory` and
  `morpheus-store-sqlite`, plus every importer; `*PersistenceParityTest` are the safety net. No behaviour changes.
- `.claude/CLAUDE.md` and `.claude/rules/architecture.md` state the deferral and a dated count: both are rewritten to
  name the rule and its source.
- No public surface. The audit orders this after AUD-ARC-01; the only real coupling is that a class leaving the
  application changes the measured list, which the exact-list rule absorbs by construction.
- AUD-ARC-13 (`decide-constraint-blocking-in-the-domain`) and sprint 12 read their result on this list.
