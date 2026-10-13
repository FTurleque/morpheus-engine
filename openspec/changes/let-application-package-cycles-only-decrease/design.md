# Design

## Context

- `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` uses `slices().matching("com.morpheus.domain.(*)..")
  .should().beFreeOfCycles()` with two `ignoreDependency` clauses, each naming an exact pair of types. It works because
  the domain has two cycles.
- Import graph of the application on `8b26d3d7`, top-level slices: 29 slices, 66 dependencies, one component of 16
  slices and 47 internal dependencies. `store` is depended on by 13 of the other 15 and depends on three: `policy`,
  `query`, `sync`.
- Without those three: `{context, ingestion, query, security, traceability}` (6 dependencies) and
  `{lifecycle, orchestration, quality}` (4).
- An import graph sees what is spelled in source. The bytecode sees more (an unspelled type of a chained call) and
  less (an inlined constant). ADR-0103 states both asymmetries; the rule is written on the bytecode.

## Goals / Non-Goals

**Goals**: nothing can add to the cycles; the first decrease is real and cheap; the measure is one a contributor can
reproduce.

**Non-Goals**: an acyclic application in this change; a module split; the two residual components (their owners are
AUD-ARC-13 and sprint 12); slicing below the first level.

## Decisions

1. **The ratchet is the set of dependencies that lie on a cycle, not the number of cycles.** The test computes, from
   the imported classes, the dependency graph between slices, its strongly connected components, and the
   slice-to-slice dependencies inside a component of more than one slice. That set must equal a named constant. It is
   deterministic, it has no detection cap, and one entry is one thing to fix. *Rejected*: `beFreeOfCycles()` with
   `ignoreDependency` per type pair, as for the domain - hundreds of pairs, and a new type pair between two slices
   already in the list would be a new line a reviewer cannot judge. *Rejected*: `FreezingArchRule` - its store is a
   file the build rewrites, holding cycle descriptions that change when a line number does. *Rejected*: a count in
   `config/*ratchets*.properties` - a number that can be met by trading one cycle for another.

2. **Exact, by the pattern of `COMPOSITION_ROOTS`.** One `@Test` refuses a dependency in a cycle that is not named; one
   refuses a name that no longer lies on a cycle. The list shrinks or the build fails. The ArchUnit cycle count is
   reported in the ADR as a dated observation and is not a threshold.

3. **First-level slices, as ADR-0109 measured.** `query.dsl` and `query.saved` are one slice and their mutual
   dependency is invisible here; `QueryPlatformArchitectureTest` is where a rule on them belongs. Stated, not hidden.

4. **A port lives where its vocabulary lives.** `PolicyPackStore`, `SavedViewStore` and `SyncStateStore` move to the
   package of their capability, as `SpecificationContentReader` lives in `read` and `EntityIdentityResolver` in
   `identity`. `application.store` keeps the ports that speak only the domain and its own records. *Rejected*: the
   audit's first step, an `application.failure` package for the three exceptions - measured, it removes no dependency
   from any cycle, and it adds a package. *Rejected*: moving `PolicyPack`, `SavedViewDefinition` or `SyncPlan` into
   `store` - the vocabulary would follow the port instead of the capability.

5. **The requirement is on the package, not on the three names.** "The package of the store ports takes part in no
   cycle" is enforced by the list having no entry that starts or ends at `store`, plus a rule that refuses one. If the
   bytecode shows a fourth dependency the import graph missed, the requirement still says what must be true.

6. **An amendment to ADR-0109, not a new ADR.** The deferral is a sentence of that record; replacing it there keeps
   one place that says what holds for package cycles.

## Risks / Trade-offs

- Moving three interfaces touches both stores and many imports for no change of behaviour. It is one mechanical lot,
  reviewed by the parity tests, and it is what makes "the count starts decreasing" true.
- Textual assertions mention `com.morpheus.application.store` (the reasoning core must not name it). A port that
  leaves that package is no longer covered by such a mention; task 1.4 lists them.
- A list of 47 names is long on its first day. Under the projection it is 10 after task 3.

## Open Questions

- **Move the three ports, or split each into a store-side port that speaks only identifiers?** Recommendation: move
  them; the second is a redesign of three contracts.
- **Should the two residual components be given owners now?** Recommendation: name them in the amended ADR with their
  measured dependencies, and leave the work to the changes that touch those packages.
