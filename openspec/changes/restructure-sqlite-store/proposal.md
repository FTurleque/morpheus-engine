# Proposal

## Why

`morpheus-store-sqlite` is one flat package, `com.morpheus.store.sqlite`: 27 main classes, 8 136 lines, no
sub-package (measured on `develop` at `1409bac5`, 2026-10-24). Two populations share it:

- the **adapter**: the twelve classes that implement a port of `morpheus-application` (`ChangeLifecycleMutationStore`,
  `CompositionStateStore`, `EntityIdentityStore`, `ExternalReferenceStore`, `PolicyPackStore`, `PortfolioStore`,
  `SavedViewStore`, `SnapshotBusinessContentStore`, `SpecificationKnowledgeStore`, `SyncStateStore`,
  `TraceabilityStore`, `VersionedRequirementStore`) and the two collaborators of the snapshot content store;
- the **runtime**: connection, scope, lease, file security, schema, migration copy, transactions, contention metrics,
  failure classification, maintenance and the operation runtime.

No rule can name either population, because nothing but a list of class names tells them apart. A gate that wants to
say "only the adapter may reach the runtime" or "the adapter holds port implementations and nothing else" has no
subject to select.

What the flat package holds today is held by the **compiler**: 9 of the 13 runtime classes are package-private, so no
other module can reach them. Splitting the package removes that boundary for those classes, because the twelve stores
call them across the new package line. Compiling the split on 2026-10-24 measured the cost on the main sources: 3 types
and 11 members become `public` (listed in `design.md`); the tests force none. The boundary the compiler held becomes an ArchUnit rule,
and that rule is proved by breaking it once.

The split does **not** make any assertion of `morpheus-architecture-tests` redundant. The sources of the module's
classes are cited there to check their **content** (where `SUPPORTED_SCHEMA_VERSION` is declared, that every store
initializes through `SqliteStoreConnection.openAndMigrate(`, which main classes mention `LocalOperationalRuntime`),
never their reachability. Those checks stay, and their paths follow the files. `design.md` lists each one with the
reason it stays.

## What Changes

- The 27 main classes move to `com.morpheus.store.sqlite.adapter` (14) and `com.morpheus.store.sqlite.runtime` (13).
  No class is renamed, merged or split; no behaviour, schema, migration resource or signature beyond the visibility
  list of `design.md` changes. `src/main/resources/db/migration` does not move.
- The module's tests move with the classes they exercise.
- Two ArchUnit rules: the runtime package is reached only from the module itself, except two named classes; the
  adapter package holds only port implementations and the two named collaborators, and no class stays in the parent
  package. The first rule has a textual companion for compile-time constants. That the module depends on no other
  persistence module and no provider module is held by the two rules of `AdapterSiblingArchitectureTest` that select
  `com.morpheus.store..` and its sub-packages; they are broken once over the new packages.
- Every pin of the old package or of a file path follows the move: architecture tests, validators (`.ps1` and `.sh`
  together), live-numbers scripts, `distribution/build-portable.ps1`, active ADRs and documentation, `.claude/`.
- Two production dependencies on the runtime from outside the module (`SqliteOperationRuntime` from `morpheus-api`,
  `morpheus-cli` and `morpheus-mcp`; `SqliteServerMaintenance` from `morpheus-api` and `morpheus-cli`) are made
  visible as named exceptions of the first rule. They are not removed: that is the decision of AUD-ARC-01.

## Capabilities

### New Capabilities

- `sqlite-store-packages`: the two packages of the SQLite store, what may reach each one, and what each may contain.

### Modified Capabilities

(none)

## Impact

- `morpheus-store-sqlite`: every main and test source changes package; `META-INF/services` of the test resources
  follows the JUnit extension it names.
- `morpheus-api`, `morpheus-cli`, `morpheus-mcp`: import lines only, main and test. No other edit.
- `morpheus-architecture-tests`: the rules added in `SqliteStorePackagesArchitectureTest`; path and package pins
  updated in the classes listed in `design.md`; no assertion removed. `architectureTestsMinimum` does not go down.
- `scripts/validate-m23` to `m26` and `validate-r2`, `.ps1` and `.sh`; `scripts/tests/`; `.claude/skills/live-numbers/`.
- No public surface: `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml` are unchanged.
- No ADR: the decision on which classes outside the module may reach the runtime belongs to the ADR of sprint 11
  (AUD-ARC-01). This change records the measure that ADR needs.
- Out of scope: `morpheus-api`, `morpheus-cli`, `morpheus-mcp` beyond their imports; merging any module; removing the
  two named exceptions.
