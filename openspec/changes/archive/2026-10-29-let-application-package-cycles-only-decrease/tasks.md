# Tasks

## 1. Measure before changing

- [x] 1.1 Re-measure with ArchUnit on the current `develop`, as ADR-0109 did: cycles reported between slices of
      `com.morpheus.application.(*)..` with `archunit.cycles.maxNumberToDetect` raised for the measurement only, and
      the slices involved. Proof: the two figures with SHA and date. **Do not copy 167 / 16 from the ADR or from this
      change**: they are of 2026-10-08.
      *2026-10-29, `6b3f6304`, throwaway test: `cycles.maxNumberToDetect=100000`, `maxNumberOfDependenciesPerEdge=1`,
      395 elementary cycles reported; the slices on them are the component of 1.2, 16 slices.*
- [x] 1.2 Compute, from the imported classes, the slice graph, its strongly connected components and the dependencies
      inside them. Proof: the list; compare it with the import graph of 2026-10-13 (one component of 16 slices, 47
      dependencies) and explain every difference - a dependency the bytecode sees and the imports do not, or the
      reverse.
      *Bytecode: 31 slices, one component of 16 (`composition, context, ingestion, lifecycle, operability,
      orchestration, policy, quality, query, read, reference, security, snapshot, store, sync, traceability`), 52
      dependencies. The import graph of the same tree is identical, edge for edge. Against the import graph of
      `8b26d3d7` (47): five more, all from code merged since and spelled in the sources - `ingestion -> read`,
      `lifecycle -> query`, `operability -> security`, `security -> operability`, `sync -> query`. No dependency is
      seen by one reading and not the other.*
- [x] 1.3 List every dependency from `application.store` to another application package, with the pair of types.
      Proof: the list; by imports, three interfaces and fourteen type pairs towards `policy`, `query`, `sync`.
      *Three interfaces, 18 type pairs: `PolicyPackStore` -> `policy` (`PolicyAuditPage`, `PolicyConfiguration`,
      `PolicyIds`, `PolicyPack`, `PolicyScope`) and `query.PageRequest`; `SavedViewStore` -> `query.dsl.QueryScope`,
      `query.saved` (`SavedViewDefinition`, `SavedViewEntry`, `SavedViewId`, `SavedViewVersion`); `SyncStateStore` ->
      `query.PageRequest`, `sync` (`ProjectSyncState`, `SourceArchivePage`, `SourceArchiveRecord`, `SourceInventory`,
      `SyncStateConflictException`, `SyncPlan`). The 2026-10-13 count of fourteen left out the two `PageRequest`
      pairs and two `sync` types.*
- [x] 1.4 List the textual assertions and rules that name `com.morpheus.application.store` or one of the three
      interfaces by path. Proof: the list, with what each would stop covering after a move.
      *One: `m27/ReasoningPlatformArchitectureTest#reasoningCoreContainsNoTransportPersistenceOrProcessExecution`
      refuses `com.morpheus.application.store` in the reasoning sources; after the move it would no longer see the
      three ports. Every other mention (`AppendOnlyJournalReadsArchitectureTest`, the parity and contract tests, both
      stores) is a type reference, which the compiler repoints.*

## 2. The ratchet, before any move

- [x] 2.1 The two tests: no unnamed dependency in a cycle; no name that no longer lies on one. Bytecode, intent "no
      dependency between application packages lies on a cycle, except the named ones" (`enforcement-choice`). Proof:
      green on the measured state with the full list of 1.2 - the gate exists before the first decrease.
      *`ApplicationPackageCyclesArchitectureTest`: `noDependencyBetweenApplicationPackagesLiesOnACycleBesideTheNamedOnes`
      and `everyNamedDependencyStillLiesOnACycle` green on `6b3f6304` with the 52 names.*
- [x] 2.2 Break each once, then revert: a planted class in a package outside every cycle depending on one inside, and
      depended on by it; a name added for a dependency that does not exist; a planted dependency outside any cycle.
      Proof: two named failures, and the third passing.
      *Planted `foundation.PlantedCycleA` -> `query.PageRequest` and `query.PlantedCycleB` -> it: fails naming
      `foundation -> query` and `query -> foundation`. Added name `foundation -> payload`: fails as no longer on a
      cycle. Planted `temporal.PlantedAcyclic` -> `foundation.PayloadMap`: passes. Run before and after the move.*
- [x] 2.3 Prove the computation is not empty and not capped: it reports the component of 1.2 in full. Proof: an
      assertion on the component the measurement found, written from the measurement.
      *`theCyclicComponentsAreTheNamedOnesInFull` compares the components to `CYCLIC_COMPONENTS` and refuses a graph
      of 20 slices or fewer; `theComponentComputationFindsEveryCycleOfAKnownGraph` checks the computation on a graph
      of two known components. Green with the component of 16 before the move.*

## 3. The first decrease

- [x] 3.1 Move each port of 1.3 to the package of its capability; update both stores and the importers. Proof: the
      `*PersistenceParityTest` suites green, unchanged; no behaviour test edited.
      *`policy.PolicyPackStore`, `query.saved.SavedViewStore`, `sync.SyncStateStore`; 32 files change an import or a
      qualified name, nothing else. Parity suites green in `./mvnw clean verify`.*
- [x] 3.2 Remove from the list every name the suite now reports as stale. Proof: the list before and after, in the
      pull request.
      *31 names reported stale and removed: 52 -> 21, in two components, `{context, ingestion, operability, query,
      read, reference, security, snapshot, traceability}` and `{lifecycle, orchestration, quality}`, as the import
      projection predicted.*
- [x] 3.3 The rule that `application.store` takes part in no cycle; break it once by making a store port import a
      type of a capability that uses it. Proof: the named failure.
      *`theStorePortsPackageTakesPartInNoCycle`. Planted `store.PlantedStorePort` returning `sync.ProjectSyncState`:
      fails, naming `store -> sync` with the pair of types and the dependencies into `store`.*
- [x] 3.4 Repoint or keep each assertion of 1.4. Proof: none deleted without an equivalent or stricter one.
      *The reasoning ban keeps `com.morpheus.application.store` and adds `policypackstore`, `savedviewstore`,
      `syncstatestore` (stricter: any mention of the names).*
- [x] 3.5 `./mvnw dependency:analyze`. Proof: 0 warning.

## 4. Record and descriptions

- [x] 4.1 Replace ADR-0109 by a consolidated ADR: decision 3 and the measurement table - the rule, the measurements of 1.1 and 1.2 with their
      date, the list after 3.2, the residual components and what holds each. Keep the index line of
      `docs/architecture/adr/README.md` true. Proof: `AdrIndexCoherenceTest`.
      *ADR-0152 (`ARCH-0152-racines-de-composition-nommees-et-cycles-de-paquets-qui-ne-font-que-diminuer.md`) takes
      the whole of ADR-0109 and adds decision 5; ADR-0109 archived with its banner; citations repointed.*
- [x] 4.2 `.claude/CLAUDE.md` and `.claude/rules/architecture.md`: replace "deferred" and its dated count by the rule
      and the name of the list. Proof: the diff; no count presented as current.
      *Only `architecture.md` stated the deferral; it now names the two lists. `CLAUDE.md` cites ADR-0152.*
- [x] 4.3 `docs/architecture/arc42/11-risques-dette.md` and `docs/architecture/risks/register.md` if they carry the
      deferral. Proof: `RepositoryDocumentationCoherenceTest`.
      *Neither carries it; `arc42/09-decisions.md` names ADR-0152.*
- [x] 4.4 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`, `live-numbers`). Proof: the
      coherence tests name the destinations.
      *Not raised, by decision: the repository raises presence ratchets on Surefire counts read on Linux and Windows
      at one exact head, and three other sprint lots land beside this one. The six added executions widen the margin
      until the next presence requalification.*

## 5. Verification

- [x] 5.1 `./mvnw clean verify`. Proof: green on both platforms through the required checks.
      *Green locally on Windows; the required checks of the pull request carry Linux and Windows.*
- [x] 5.2 Pull request description: the finding, why the exceptions were not moved, the two measurements, the list
      before and after, the governance files changed.
