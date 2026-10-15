# Tasks

## 0. Decide before starting

- [ ] 0.1 The maintainer answers the first open question of the design (SDK API version). Proof: the answer recorded
      in this file; it fixes whether `Constraint` and `NormalizedProjectContent` are converted or listed.
      **2026-10-13 - not answered.** The lot was run on the instruction to convert: both records are converted to
      named factories, nothing is on the exception list, and `sdk.apiVersion` is **not** changed. The source
      incompatibility for plugin authors is stated in the release notes. The question stays open: if the answer is
      "a new API version", it is a follow-up to this lot, not a reason to restore a constructor.

## 1. Measure before changing

- [x] 1.1 Write the structural test with an empty exception list and run it. Proof: the records it names; on
      2026-10-13 a source scan finds seventeen constructors in fifteen records (the audit counted eight, by Javadoc).
      Neither number is trusted: the list of the test is the measure.
      **2026-10-13.** `RecordSingleConstructorArchitectureTest`, run on `d5be889b` before any removal, named seventeen
      constructors in fifteen records - the proposal's list exactly: `Constraint` (4 arguments),
      `ProviderProbeResult` (8), `ProjectDiscoveryResult` (4), `NormalizedProjectContent` (6, 10, 11),
      `ChangeOrchestrationState.ConstraintView` (2), `ChangeTransitionEvaluation` (8), `UpdateCheckResult` (7),
      `UpdateManifest` (4), `RequirementSearchPage` (5), `SnapshotBusinessContent` (9), `SourceScanPolicy` (5),
      `MorpheusRemoteIdentityFile.Identity` (3), `.GeneratedCredential` (3), `MinosIntegrationSettings` (5),
      `NexusIntegrationSettings` (5). `morpheus-provider-testkit` is not on the architecture classpath
      (`ImportedClasspathCompletenessTest`); read by hand, its one record declares only its canonical constructor.
- [x] 1.2 For each constructor, find its callers with the compiler: remove it, compile the reactor with tests, record
      which classes fail, restore. Proof: the table (none / tests only / production), beside the one of the proposal.
      Constructor references (`X::new`) are looked for explicitly.
      **2026-10-13.** Done in one pass instead of seventeen: all seventeen removed together, then `compile` on the
      whole reactor (production callers), then `test-compile` (test callers); nothing was restored. No constructor
      reference (`X::new`) exists for any of the fifteen records. Call sites, production / tests:

      | Constructor | Production | Tests |
      |---|---|---|
      | `Constraint` (4) | 2 - `OpenSpecChangeMetadataReader`, `StructuredMarkdownSpecificationContentReader` | 23 |
      | `NormalizedProjectContent` (6) | 2 - `OpenSpecCurrentSpecificationReader`, `ReferenceSpecificationContentReader` | 5 |
      | `NormalizedProjectContent` (10) | 1 - `OpenSpecChangeMetadataReader` | 5 |
      | `NormalizedProjectContent` (11) | 2 - `OpenSpecProjectContentReader`, `OpenSpecSpecificationContentReader` | 10 |
      | `SnapshotBusinessContent` (9) | 0 | 17 |
      | `Identity` (3) | 0 | 9 |
      | `UpdateManifest` (4) | 0 | 5 |
      | `ProviderProbeResult` (8) | 0 | 4 |
      | `SourceScanPolicy` (5) | 0 | 4 |
      | `NexusIntegrationSettings` (5) | 0 | 2 |
      | `MinosIntegrationSettings` (5) | 0 | 1 |
      | `GeneratedCredential` (3) | 0 | 1 |
      | `UpdateCheckResult` (7) | 0 | 1 |
      | `ProjectDiscoveryResult` (4), `ConstraintView` (2), `ChangeTransitionEvaluation` (8), `RequirementSearchPage` (5) | 0 | 0 |

      One difference with the proposal: `UpdateCheckResult` (7) had a caller, a test that existed for that constructor
      alone (`ProductIntegrityTest#historicalResultConstructorRemainsExplicitlyDiscoveryOnly`).
- [x] 1.3 List the Javadocs of `src/main/java` that justify an element by compatibility. Proof: the list; on
      2026-10-13, nine constructors, the two `default` methods of `VersionedRequirementStore`, the MCP stub, and the
      two `@Deprecated` methods that `remove-the-unpinned-plugin-entry-points` carries.
      **2026-10-13, after the lot.** The nine constructors and the two `default` methods no longer match. What still
      does: the declared stub (`MorpheusProductMcpTools`, `product.update-discovery`), `ProviderPluginActivator:22`
      (carried by `remove-the-unpinned-plugin-entry-points`), and one match the proposal did not list - the class
      Javadoc of `MorpheusApiService` ("adapter composition and compatibility delegation only"), which describes a
      facade and justifies no element; left as it is, for the maintainer to judge.

## 2. The change

- [x] 2.1 Remove the constructors without any caller. Proof: the reactor compiles; no test is edited.
      **2026-10-13.** Four removed without a test edited; the fifth (`UpdateCheckResult`) had the one test of 1.2.
- [x] 2.2 Remove the constructors only tests call; each test calls the canonical constructor or a fixture of its own
      module that reproduces the removed default exactly. Proof: the same tests pass with their assertions unmodified.
      **2026-10-13.** Every test call site passes the removed default explicitly to the canonical constructor
      (`Optional.empty()`, `List.of()`, `SourceScanPolicy.DEFAULT_MAX_DIRECTORIES`); no fixture was added, the
      seventeen call sites of `SnapshotBusinessContent` included - one more `List.of()` in a list of them stays
      readable. One test is removed, not rewritten: the one of 1.2, whose subject was the removed constructor.
- [x] 2.3 Replace each constructor with production callers by a named factory (or list it, per 0.1); update the
      OpenSpec and Markdown providers and the reference plugin. Proof: a test per factory asserting every defaulted
      component; the provider tests pass unmodified.
      **2026-10-13.** Three factories for four constructors: `Constraint.withUnknownSemantics`,
      `NormalizedProjectContent.currentSpecificationsOnly` (the 6-argument shape) and
      `NormalizedProjectContent.withoutAcceptanceCriteria` (the 11-argument shape; the one 10-argument caller now
      passes its empty list of requirement deltas itself). Tests:
      `ConstraintSemanticsTest#aConstraintWithUnknownSemanticsIsUnknownOnEveryAxisAndCarriesWhatItWasGiven`,
      `NormalizedProjectContentTest#contentOfCurrentSpecificationsOnlyCarriesNoChangeArtefactAndNoAcceptanceCriterion`
      and `#contentWithoutAcceptanceCriteriaKeepsEveryOtherListItWasGiven`, each comparing the factory's value to the
      canonical constructor's.
- [x] 2.4 Re-document the two `default` methods of `VersionedRequirementStore` as the port's fallback, or make them
      abstract (design, third open question). Proof: the search of 1.3 no longer returns them.
      **2026-10-13.** Re-documented; they stay `default`.

## 3. The gate

- [x] 3.1 The structural test green with its final list. Mechanism: a test over the imported production records that
      compares public constructors to record components - intent "a record offers one public way to be constructed";
      not a textual assertion, because the word is not the property.
      **2026-10-13.** Green, the list empty. Three methods: the rule, the two-way check of the list, and a proof on
      five records of the test class that a second public constructor is reported and a private one is not.
- [x] 3.2 Break it once, then revert: restore one removed constructor without its Javadoc. Proof: the failure names
      the record.
      **2026-10-13.** `UpdateManifest(String, String, URI, String)` restored without Javadoc: the rule failed with
      `[com.morpheus.application.product.UpdateManifest(String, String, URI, String)]`. Reverted.
- [x] 3.3 Break the two-way check once: add a clean record to the exception list. Proof: the named failure.
      **2026-10-13.** `UpdateCheckResult` listed: `everyListedRecordStillDeclaresASecondPublicConstructor` failed with
      `[com.morpheus.application.product.UpdateCheckResult]`. Reverted.

## 4. Documentation

- [x] 4.1 `.claude/rules/code-style.md`: line 13 states the rule for records and cites the test; line 61 gains the two
      bounds of the design, decision 5.
- [x] 4.2 `docs/developer/PROVIDER_SDK.md`: how a plugin builds a `NormalizedProjectContent` and a `Constraint` now.
      `docs/user/release-notes/` for the version that ships it: the source-incompatible change for plugin authors, in
      product terms.
      **2026-10-13.** `RELEASE_NOTES_1.2.1.md`, under "Ruptures": 1.2.1 is the version in preparation.
- [x] 4.3 Dated records are left as they are.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through
      the required checks; the changed-line coverage gate passes.
      **2026-10-13 - left to the required checks.** What was run locally, on Windows, offline:
      `./mvnw -o verify -Djacoco.skip=true -pl '!morpheus-coverage-report'` - every module's tests and its
      `dependency:analyze-only` execution pass; the one failure is `CoverageQualityGateTest`, which refuses to
      conclude without the JaCoCo reports that run skipped. No `clean verify`, no Linux run, no coverage measured.
- [ ] 5.2 The M22 gates (they load the reference plugin's JAR from `target/`). Proof: green.
      **2026-10-13.** Green inside the run of 5.1 (the architecture module ran 674 tests, one failure, the coverage
      gate above); not run through `validate-m22`. Left unchecked until the required checks say so.
- [ ] 5.3 Presence ratchets: tests removed with a constructor they alone exercised lower the count of executions;
      read `config/m21-quality-ratchets.properties` and confirm the margin holds. A ratchet is never lowered - a test
      is rewritten rather than deleted when the margin would not hold.
      **2026-10-13.** One test removed (1.2), five added (two factory tests, three methods of the gate). The run
      of 5.1 counted 4 060 executions for `testsMinimum=3820` and 674 in the architecture module for
      `architectureTestsMinimum=585`; no ratchet is touched. A local count, to be confirmed by the M21 validator.
- [ ] 5.4 Pull request description: the finding, the two tables, the factories and what each assumes, the answer to
      0.1, the justification for a change under `morpheus-architecture-tests/`.
      **2026-10-13 - not done:** no pull request is opened by this lot.
