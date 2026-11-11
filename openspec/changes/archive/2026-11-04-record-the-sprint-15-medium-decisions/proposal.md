# Proposal

## Why

The eleven `Moyenne` findings of sprint 15 of the 2026-10-09 audit were closed by PR #604, which also moved
`sqlite-jdbc` to 3.54.1.0 and fixed the order of an analysis's scenarios. Each closure is held by an executable rule,
but seven of those rules state behaviour that no specification of `openspec/specs/` carries: a reader of the
specifications cannot learn them, and nothing confronts the code with a written requirement. This change writes them
down, as they hold on `develop` at `019f4913`.

## What Changes

- The scales of a coverage qualification are measured at one head (AUD-TST-22).
- The reactor version is tied to the schema it ships, to the last released level and to the latest upgrade guide
  (AUD-DEP-25, AUD-DEP-28).
- A walk of the whole repository goes through one owner that skips workspace copies and nested checkouts (AUD-TRV-08).
- The vulnerability scan identifies the SQLite engine that `sqlite-jdbc` embeds, and its one suppression is scoped to
  the shell CVE it names (AUD-DEP-27).
- A test that changes state of the whole JVM runs isolated, which is what lets the transport modules run their tests
  in parallel (AUD-QUA-17).
- The scenarios of a change analysis follow their source key, never their generated identity.
- The database workers of the HTTP adapter admit, bound and refuse by name, for the local server and for the remote
  backup (AUD-PRF-19).

No behaviour changes: every requirement describes what the code and its tests already enforce.

## Capabilities

### New Capabilities

- `test-jvm-state-isolation`: a test that changes state of the whole JVM, and the modules that run tests in parallel.
- `change-analysis-scenario-order`: the order of the scenarios an analysis returns.
- `database-worker-admission`: admission, deadline and refusals of the database workers of the HTTP adapter.

### Modified Capabilities

- `coverage-qualification-evidence`: one head for every scale of a qualification.
- `released-schema-upgrade`: the product version is declared only at the level it ships, and nothing above it is
  declared released.
- `schema-upgrade-guidance`: the reactor version follows the schema past the last release.
- `repository-root-location`: one owner for a walk of the whole repository.
- `vulnerability-feed-freshness`: the identification of the embedded SQLite engine.

## Impact

Specifications only. The rules already exist: `CoverageQualificationEvidenceTest`,
`ReleasedSchemaLevelsDeclarationTest`, `UpgradeGuideCoherenceTest`, `RepositoryTreeOwnershipTest`,
`DependencyCheckWorkflowContractTest` with `scripts/check-dependency-check-sqlite-identification.py`,
`JvmMutatingTestsAreIsolatedArchitectureTest`, `RequirementChangeImpactOrderTest`, `DatabaseCallWorkersTest` and
`DatabaseWorkArchitectureTest`. The decisions are ADR-0157, ADR-0158 and ADR-0159.
