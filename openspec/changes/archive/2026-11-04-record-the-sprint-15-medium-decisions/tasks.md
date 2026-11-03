# Tasks

## 1. Write the requirements the sprint 15 rules already hold

- [x] 1.1 One head for every scale of a coverage qualification. Proof: `CoverageQualificationEvidenceTest`
      (`twoScalesQualifiedAtDifferentHeadsAreRefusedAndNameBothHeads`).
- [x] 1.2 The product version declared only at the level it ships, nothing above it declared released. Proof:
      `ReleasedSchemaLevelsDeclarationTest`.
- [x] 1.3 The reactor version follows the schema past the last release. Proof: `UpgradeGuideCoherenceTest`
      (`aReactorThatKeepsTheLastReleasedVersionAfterAMigrationIsRefused`).
- [x] 1.4 One owner for a walk of the whole repository. Proof: `RepositoryTreeOwnershipTest`.
- [x] 1.5 The embedded SQLite engine identified by the scan, its one suppression scoped. Proof:
      `DependencyCheckWorkflowContractTest`, `scripts/tests/test_check_dependency_check_sqlite_identification.py`.
- [x] 1.6 A test that changes state of the whole JVM runs isolated. Proof: `JvmMutatingTestsAreIsolatedArchitectureTest`.
- [x] 1.7 The scenarios of an analysis follow their source key. Proof: `RequirementChangeImpactOrderTest`.
- [x] 1.8 Admission, deadline and refusals of the database workers. Proof: `DatabaseCallWorkersTest`,
      `DatabaseWorkArchitectureTest`, `RemoteSchedulerAvailabilityUnderSqliteWaitTest`.

## 2. Check

- [x] 2.1 `openspec validate record-the-sprint-15-medium-decisions --strict` and `openspec validate --specs --strict`
      pass.
