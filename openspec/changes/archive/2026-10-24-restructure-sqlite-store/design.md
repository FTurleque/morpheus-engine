# Design

## Context

Measured on `develop` at `1409bac5` on 2026-10-24.

- `morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite` holds 27 classes (8 136 lines) and no directory;
  `src/test/java/com/morpheus/store/sqlite` holds 72 test sources, in the same package for package-private access.
- The module's POM depends on `morpheus-domain`, `morpheus-application` and `sqlite-jdbc` only. Nothing but the POM
  keeps it away from `morpheus-store-memory` and the providers.
- 9 of the 13 runtime classes are package-private. Every store calls `SqliteStoreConnection.openAndMigrate` (13
  calls) and `SqliteTransactionRunner.run` / `runVoid` (13 calls); `SqliteChangeLifecycleMutationStore` calls
  `SqliteContentionMetrics.of`. In the other direction, `SqliteOperationRuntime` builds the twelve stores and reads
  `SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE`, and `SqliteServerMaintenance` builds a
  `SqliteSpecificationKnowledgeStore`. The two packages depend on each other: the split names a cycle that the flat
  package hides, it does not create one.
- `LayerDependencyTest` imports every class under `com.morpheus` on its classpath, the architecture tests
  themselves included; two of them (`SqliteConnectionScopeUseArchitectureTest`,
  `StaticOperationalRuntimeUsersArchitectureTest`) depend on `SqliteConnectionScope.class`.
- `javac` inlines a compile-time constant: a class outside the module that reads a public `static final String` of a
  runtime class carries no dependency on it (`.claude/rules/architecture.md`).

## Goals / Non-Goals

**Goals**: the adapter and the runtime of the SQLite store are two packages; a rule says who may reach the runtime and
what the adapter may contain; the boundary that package-private visibility held stays held, by a rule proved to fail.

**Non-Goals**: changing a behaviour, a query, a schema, a migration, a refusal message; renaming, merging or splitting a
class; touching `morpheus-api`, `morpheus-cli` or `morpheus-mcp` beyond their import lines; removing a dependency of
another module on the runtime (AUD-ARC-01, sprint 11); a cycle rule between the two packages (see risks).

## Decisions

1. **The criterion is mechanical.** A class implements an interface of `com.morpheus.application..`: adapter.
   Otherwise: runtime. The only exceptions are the two collaborators of `SqliteSnapshotBusinessContentStore`, which
   implement nothing and are used by that store alone. No class resisted the criterion; the runtime-to-adapter edges
   of `SqliteOperationRuntime` and `SqliteServerMaintenance` are recorded as risks, not as reclassifications.

2. **A visibility widens only when a compile error requires it.** A type or member becomes `public` only if a class of
   the other package uses it. Nothing is widened for convenience, and nothing for a test alone: a test moves to the
   package of the member it reaches. The list below is the diff of modifiers. No test forces a widening: each test sits
   in the package of the package-private members it reaches, so the package-private store constructors
   (`SqliteChangeLifecycleMutationStore(Path, OperationalRecorder)`, `SqliteSpecificationKnowledgeStore(Path, int)`,
   `SqliteTraceabilityStore(Connection)`) and the package-private runtime types stay as they are.

3. **Rule 1 replaces the compiler.** *No production class outside `com.morpheus.store.sqlite..` depends on
   `com.morpheus.store.sqlite.runtime..`, except on `SqliteOperationRuntime` and `SqliteServerMaintenance` (and their
   nested types).* The rule imports production classes only, so the tests of other modules and of the architecture
   suite keep their access, as they have it today through public types. Its textual companion — *no main source
   outside the module imports or names `com.morpheus.store.sqlite.runtime.` other than the two classes* — is the only
   form that sees an inlined constant. Both are kept (`.claude/rules/architecture.md`: in doubt, keep both). The
   exception carries a comment that points to AUD-ARC-01.

4. **Rule 2 says what the adapter is.** *Every top-level class of `com.morpheus.store.sqlite.adapter` implements an
   interface of `com.morpheus.application..`, or is one of the two named collaborators.* A new class that implements
   no port fails, by name.

5. **The store's outward bounds are already rules.** *No class of `com.morpheus.store.sqlite..` depends on another
   `com.morpheus.store..` package or on `com.morpheus.provider..`* is held by `AdapterSiblingArchitectureTest`:
   `storesDoNotDependOnEachOther` slices `com.morpheus.store.(*)..`, and
   `storesDoNotDependOnProvidersTransportsOrIntegrations` selects `com.morpheus.store..`. Both select the
   sub-packages, so no third rule is written; both are broken once over the new packages.

6. **Every rule is broken once and reverted** before it is accepted: a runtime class planted as a dependency of a
   `morpheus-api` class, a constant of a runtime class read from `morpheus-cli`, a class without a port in the adapter
   package, a class left in the parent package, a dependency of a store of the adapter package on `store.memory`, a
   dependency of a runtime class on a provider. The failures are quoted in the pull request.

## Destination of the 27 classes

| Class | Package | Implements | Visibility before | After |
|---|---|---|---|---|
| SqliteChangeLifecycleMutationStore | adapter | ChangeLifecycleMutationStore | public | unchanged |
| SqliteCompositionStateStore | adapter | CompositionStateStore | public | unchanged |
| SqliteEntityIdentityStore | adapter | EntityIdentityStore | public | unchanged |
| SqliteExternalReferenceStore | adapter | ExternalReferenceStore | public | unchanged |
| SqlitePolicyPackStore | adapter | PolicyPackStore | public | unchanged |
| SqlitePortfolioStore | adapter | PortfolioStore | public | unchanged |
| SqliteSavedViewStore | adapter | SavedViewStore | public | unchanged |
| SqliteSnapshotBusinessContentStore | adapter | SnapshotBusinessContentStore | public | unchanged |
| SqliteSpecificationKnowledgeStore | adapter | SpecificationKnowledgeStore | public | `INITIALIZATION_FAILURE` public |
| SqliteSyncStateStore | adapter | SyncStateStore | public | unchanged |
| SqliteTraceabilityStore | adapter | TraceabilityStore | public | unchanged |
| SqliteVersionedRequirementStore | adapter | VersionedRequirementStore | public | unchanged |
| SqliteSnapshotBusinessContentReader | adapter | — (collaborator) | package-private | unchanged |
| SqliteSnapshotBusinessContentWriter | adapter | — (collaborator) | package-private | unchanged |
| SqliteAtomicFileReplacer | runtime | — | package-private | unchanged |
| SqliteConnectionScope | runtime | — | public | unchanged |
| SqliteContentionMetrics | runtime | — | package-private | type, `of`, `recorder` public |
| SqliteDatabaseLease | runtime | — | package-private | unchanged |
| SqliteDatabaseSecurity | runtime | — | package-private | unchanged |
| SqliteFailureClassifier | runtime | — | public | unchanged |
| SqliteOperationRuntime | runtime | — | public | unchanged |
| SqlitePreMigrationCopy | runtime | — | package-private | unchanged |
| SqliteSchemaManager | runtime | — | package-private | unchanged |
| SqliteServerMaintenance | runtime | — | public | unchanged |
| SqliteSqlStatements | runtime | — | package-private | unchanged |
| SqliteStoreConnection | runtime | — | package-private | type, `Preparation`, the three `openAndMigrate` public |
| SqliteTransactionRunner | runtime | — | package-private | type, `run`, `runVoid`, `SqlWork`, `SqlVoidWork` public |

The column "After" is the diff of modifiers: 3 types and 11 members, each one a compile error of the adapter or of
`SqliteOperationRuntime` when left package-private. The six other package-private runtime types stay package-private.
No widening is forced by a test.

## Destination of the 72 test sources

A test sits in the package of the package-private members it reaches; one that reaches none sits with the class it
exercises. Shared support sits with its users.

- **adapter (28)**: the store tests, the closed-store census, the query-plan guards (`SqliteQueryPlanGuardTest`, and
  `SqliteEveryReadStatementPlanGuardTest`, which reads `SqliteQueryPlanGuardTest.Guarded`), and the migration tests that
  read through a store (`SqliteCompositionResolutionMigrationTest`, `SqlitePolicyAuditOrderKeyMigrationTest`,
  `SqliteMigrationCompatibilityM19Test`).
- **runtime (44)**: scope, lease, security, schema, migration copy, transaction runner, maintenance, operation runtime,
  every test that reads `SqliteSchemaManager`, and their support: `NoSqliteScopeLeftBehindExtension` (named by
  `META-INF/services/org.junit.jupiter.api.extension.Extension`), `FailingFileResources`, `FixtureOpenStoreProcess`,
  `ReleasedSchemaLevels`, `SqliteLeaseHoldingProcess`, `SqliteIndexRepetitions` (with its two users,
  `SqliteIndexRedundancyTest` and `SqliteIndexRemovalUpgradeTest`, which reads `SqliteSchemaManager`).

## Dependencies on the runtime from outside the module

| Runtime class | Cited from | Kind | Status |
|---|---|---|---|
| SqliteOperationRuntime | `morpheus-api`, `morpheus-cli`, `morpheus-mcp` (main) | compiled dependency | **tolerated**, named exception of rule 1; to be removed or confirmed by AUD-ARC-01 |
| SqliteServerMaintenance | `morpheus-api`, `morpheus-cli` (main); `morpheus-mcp` (tests) | compiled dependency | **tolerated**, named exception of rule 1; to be removed or confirmed by AUD-ARC-01 |
| SqliteConnectionScope | tests of `morpheus-api`, `morpheus-cli`, `morpheus-mcp`; two architecture tests | compiled dependency, test code only | tolerated: rule 1 covers production classes |
| SqliteSchemaManager | architecture tests | source path and string literals | tolerated: content checks (declaration of `SUPPORTED_SCHEMA_VERSION`) |
| SqliteStoreConnection | `MultiResourceOwnershipContractTest` | source path and string literals | tolerated: content check (stores initialize through `openAndMigrate(`) |
| SqliteDatabaseSecurity | `MultiResourceOwnershipContractTest` | string literal | tolerated: same content check |
| SqliteDatabaseLease | `StaticOperationalRuntimeUsersArchitectureTest` | file name in a list | tolerated: list of main classes that mention `LocalOperationalRuntime` |
| SqliteContentionMetrics | `StaticOperationalRuntimeUsersArchitectureTest` | file name in a list | tolerated: same list |

**Identified debt.** The first two rows are production code of three transport adapters depending on the runtime of a
store. ADR-0109 names the composition roots that may reach a concrete store; it does not say whether a transport may
hold the runtime itself or only the ports it returns. That is the question AUD-ARC-01 decides. Until then, rule 1
names the two classes, and the list does not grow.

Outside the runtime, `morpheus-cli`'s `CliProjectWriteCapabilityResolver` builds a `SqliteSpecificationKnowledgeStore`
in main code. It is an adapter class, already public, and outside the subject of rule 1; it is recorded here for
AUD-ARC-01.

## Assertions of `morpheus-architecture-tests`

**Removed: none.** No assertion of the suite checks that a class of the module is or is not reachable; every one that
names a class of the module checks the content of its source, or the wiring of its callers. A package rule does not
see either.

**Kept, path or package updated:**

| Test | What it checks | Why no rule covers it |
|---|---|---|
| `m21/AuditRemediationContractTest` | text of `SqliteVersionedRequirementStore` | content |
| `m26/RemoteServerArchitectureTest` | `SqliteSchemaManager` declares `SUPPORTED_SCHEMA_VERSION` | content |
| `ReleasedSchemaLevelsDeclarationTest` | same declaration; the golden checksum test reads the released levels | content |
| `RepositoryDocumentationCoherenceTest` | the declaration, its reading by `SqliteServerMaintenance`, the documented command | content and documentation |
| `UpgradeGuideCoherenceTest` | the guide agrees with the declared schema version | content and documentation |
| `MultiResourceOwnershipContractTest` | every store initializes through `SqliteStoreConnection.openAndMigrate(`; the operation runtime's resources | wiring inside a method body |
| `PartialRuntimeOwnershipContractTest` | the text of `SqliteOperationRuntime` | wiring |
| `SqliteConnectionScopeUseArchitectureTest` | stores built by a class are built inside a scope it opens | constructor arguments and call order |
| `StaticOperationalRuntimeUsersArchitectureTest` | which main classes mention the static runtime | mention, not dependency |

`SqliteConnectionScopeUseArchitectureTest` selects the store types by
`getPackageName().equals("com.morpheus.store.sqlite.adapter")`, the package of the stores; its own guard (the operation
runtime builds at least twelve stores) fails if it ever selects nothing. `MultiResourceOwnershipContractTest` lists the
`*Store.java` of the adapter directory and requires `SqliteStoreConnection.java` in the runtime directory, its
non-empty guard unchanged. `RepositoryDocumentationCoherenceTest` looks for a documented `Sqlite*Exception` source in
either package.

Inside `morpheus-store-sqlite`, three tests read sources: `SqliteClosedStoreRefusalTest` lists the stores of the adapter
directory, `SqliteSourceArchiveListingTest` reads `adapter/SqliteSyncStateStore.java`, and
`SqliteEveryReadStatementPlanGuardTest` walks both packages, so it still reads every statement of the module.

## Risks / Trade-offs

- **The compiler boundary becomes a rule.** A rule can be weakened in a diff where a modifier could not be read past.
  Mitigation: the rule and its textual companion are both broken once, and the exception list is closed.
- **The cycle between the packages is real and stays.** `SqliteOperationRuntime` and `SqliteServerMaintenance` build
  stores; the stores call the runtime. A cycle rule between the two packages would fail on the first day. Breaking the
  cycle means moving the store construction out of the runtime, which is the question of AUD-ARC-01.
- **Tests that reach package-private members of both packages** cannot sit in one package without a widening. Task
  2.3 lists them; each widening it forces is added to the table above, or the test is reported if none is acceptable.
- **Pins outside Java.** Validators, live-numbers scripts, the portable distribution script, a `.rules.ts` and active
  ADRs cite the old paths. A missed one fails late (validator) or never (documentation). Task 3 inventories them by a
  search on both spellings, `com/morpheus/store/sqlite/` and `com\morpheus\store\sqlite\`.
- **Dated records are not rewritten** (`.claude/rules/documentation.md`): audits under `docs/project/audits/`, archived
  OpenSpec changes and released notes keep the paths of their date.
