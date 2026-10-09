# Tasks

## 1. Measure

- [x] 1.1 Generate the exact list of api/mcp classes depending on `com.morpheus.store..` or `com.morpheus.provider..` with an ArchUnit evaluation (not a grep); verify it matches the 17 classes recorded in design.md or record the difference
  - 2026-10-08: **32** classes (20 `api`, 12 `mcp`), not the 17 of design.md, which came from an import grep; only `MorpheusProjectSyncApiService` reaches a provider (OpenSpec)
- [x] 1.2 Evaluate the transport-independence rule (`com.morpheus.integration.mcp..` depends on no other `com.morpheus..` package) and the package-cycle rule on `domain`, `application`, `api`; record each violation count in the ADR draft
  - 2026-10-08: transport independence 0 violations; domain cycles **2** (`provider ↔ source`, `requirement ↔ scenario`, reported one at a time by ArchUnit 1.5.1); application 167 cycles over 16 slices; api without subpackages, so not applicable

## 2. Decide

- [x] 2.1 Write the ADR (next free number, checked with a glob of `docs/adr/0*.md`) choosing option (a) or (b) and deciding each measured rule; verify `AdrIndexCoherenceTest` passes
  - ADR-0109: option (a), transport rule adopted, domain cycles with two named exceptions, application deferred (maintainer decisions of 2026-10-08); `AdrIndexCoherenceTest` green
- [x] 2.2 Amend ADR-0003 and align `.claude/CLAUDE.md` and `.claude/rules/architecture.md` with the decision; verify `RepositoryDocumentationCoherenceTest` passes
  - ADR-0003 amended; `.claude/CLAUDE.md` and `.claude/rules/architecture.md` aligned; `RepositoryDocumentationCoherenceTest` green

## 3. Enforce

- [x] 3.1 Add the rule chosen in 2.1 (allowlist with a classification test for option (a)); verify it passes, then plant a new dependency outside the allowlist and verify it fails, then remove the plant
  - `AdapterCompositionRootArchitectureTest`: a planted `com.morpheus.api.PlantedStoreUser` fails both decision-1 tests, a planted root name fails the equality test; both removed
- [x] 3.2 Add each measured rule the ADR accepted; verify each passes and refuses a planted violation
  - transport rule fails on a planted `morpheus-domain` dependency; domain cycle rule fails on a planted `PlantedLocatorHolder(SourceLocator)`; both removed
