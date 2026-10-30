# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 Re-run the eight commands of the proposal's table on the lot's base. Proof: the table with its date and
      commit. Do not trust either column of the proposal; the audit, the archived ADR-0103 and the proposal already disagree.
- [x] 1.2 List the members of the mechanical family by file, and for each file whether its subject is a class of a
      module on the ArchUnit classpath (re-read the `pom.xml` of `morpheus-architecture-tests`, not a rule page).
      Proof: the list; on 2026-10-13, 196 members in 31 files, 9 more of the same form with a message.
- [x] 1.3 List the textual bans of a type written another way - `assertTrue(!x.contains(...))`, a helper, a loop over
      literals - so that the fixture tests of 2.1 state what the measure does not count. Proof: the list.
- [x] 1.4 List the test classes that import no ArchUnit type, read nothing under a `src/main` directory and load no
      production class. Proof: the list; a scan of path literals on 2026-10-13 suggested about nineteen.
- [x] 1.5 Take the executed-test count of the module from a validation summary of the base (`live-numbers`).

## 2. The measure and its ceiling

- [x] 2.1 The fixture tests of the expression, in both directions, before the gate reads the real sources: comments,
      strings and text blocks are not code. Reuse the neutralisation that
      `CliOptionParsingRefusesUnknownOptionsTest` or `UnreferencedPublicMethodArchitectureTest` already has rather
      than writing a third; if it has to be shared, it is copied per the module's own convention, not moved to a new
      module.
- [x] 2.2 The gate, with the ceiling set to the count of 1.2 and the commit of the measure in its Javadoc. Intent, in
      one sentence: *the number of textual bans of a type that state no intent does not grow* - a property of test
      sources, so textual by nature (`enforcement-choice`).
- [x] 2.3 Break it three ways and revert each: a member added (fails, names the message rule); a member removed
      without lowering the ceiling (fails, asks to lower it); a member added **with** a message (passes). Proof: the
      three outcomes.

## 3. The proof file

- [x] 3.1 The proof file under the module's `target`, first line the method. Each expression has its fixture.
- [x] 3.2 A test that the file is written and complete. It asserts presence and form, never a value.

## 4. The coherence marker

- [x] 4.1 The derivation of the property from a test class's source, with fixtures: a class of each kind, and a class
      that reads a production source through a helper of another class of the suite (state whether the derivation
      follows it; if not, say so in the gate's Javadoc as a known limit).
- [x] 4.2 The marker on the classes of 1.4, and the gate that compares marker and property in both directions.
- [x] 4.3 Break it twice and revert: a marked class given a `src/main` read; an unmarked documentation class.
- [x] 4.4 Run the module with the marker as the only group. Proof: the classes run and their count, on both
      platforms - a group selection is a Surefire setting, and the default selection of `clean verify` must be
      unchanged (compare the executed-test count with 1.5 plus the tests this change adds).

## 5. Decision record and rules

- [x] 5.1 The section of `.claude/rules/architecture.md` (design, decision 6). Dated figures of the archived ADR-0103 are not rewritten.
- [x] 5.2 `.claude/rules/architecture.md` and `.claude/skills/enforcement-choice/SKILL.md`: a textual ban of a type
      carries its intent as its message; the family only shrinks; the proof file is where a count is read. No figure.
- [x] 5.3 `docs/developer/BUILD_AND_TEST.md`, if it lists the proof files of a build: the new one, with its method.
- [x] 5.4 `docs/architecture/risks/register.md`: an entry for the family as a debt whose counter is the ceiling.

## 6. Verification

- [x] 6.1 `./mvnw clean verify` on Linux and Windows: the measure reads source files, and line endings and the
      default charset must not change a count. Proof: the proof file of each platform, identical.
- [x] 6.2 The executed-test count is higher than 1.5; `architectureTestsMinimum` is raised by the `coverage-ratchet`
      procedure, with every destination that `RepositoryDocumentationCoherenceTest` and
      `ProductionIntegrityContractTest` then name.
- [x] 6.3 Pull request description: the finding, the table of 1.1, why the share is not capped, and the justification
      of each file changed under `morpheus-architecture-tests/` (`rules/governance.md`).

## Proofs of the checked tasks (2026-10-29, from `6b3f6304`)

- 1.1: on `morpheus-architecture-tests/src/test/java` at `6b3f6304`: 202 files / 41 990 lines; 854 `@Test`; 38 files
  importing ArchUnit, carrying 190 `@Test`; 70 `.check(`; 1 287 `assertTrue` + 530 `assertFalse` over
  `<receiver>.contains(`; 1 706 with the archived ADR-0103's receiver; 197 members of the mechanical family by its
  `grep` in 31 files, 10 of the same form with a message; 435 `Files.readString`.
- 1.2: the 197 are 196 in code and one in a Javadoc (`HttpRoutesFamilyArchitectureTest`, line 97), which the gate does
  not count. The 31 files are the `*ApiService*`, `Local*HttpRoutes*`, `LocalHttp*` and server-bootstrap suites and
  `m26/RemoteServerArchitectureTest` (19); every subject is a class of `morpheus-api`, which is on the ArchUnit
  classpath (`morpheus-architecture-tests/pom.xml`).
- 1.3: no `assertTrue(!x.contains("Type"))` and no `assertFalse(x.contains(variable))` loop; two helpers that take the
  literal, `HttpRoutesFamilyArchitectureTest.assertNoRouterSourceMentions` and
  `HttpRoutesTransportBoundaryArchitectureTest.assertNoSourceMentions`. The fixtures of
  `EnforcementMechanismAccountingTest` say what the measure does not count.
- 1.4: eighteen classes: `AdrIndexCoherenceTest`, `BoundedWaitOwnershipTest`, `GateReachabilityArchitectureTest`,
  `ReactorModuleDocumentationTest`, `RepositoryRootOwnershipTest`, `RepositoryRootTest`,
  `SqliteStoreTestsDeriveTheSchemaVersionTest`, `TestFactoryAssertsItsSourceTest`,
  `d2/DependencyCheckWorkflowContractTest`, `d2/DependencyLineCurrencyTest`, `d2/RepositoryTextHygieneContractTest`,
  `m20/InnoSetupToolchainTrustArchitectureTest`, `m20/ReleaseQualificationContractTest`,
  `m21/BuildProvenanceSbomContractTest`, `m21/CoverageQualificationEvidenceTest`, `m21/CoverageScaleSeparationTest`,
  `m21/SonarQualityGateClassificationContractTest`, `m28/McpClientIntegrationArchitectureTest`.
  `CliOptionValueHasOneBlankDefinitionTest` reads `morpheus-cli/src/main` through
  `CliOptionParsingRefusesUnknownOptionsTest`: a derivation that read each class alone missed it, so the gate follows
  the classes of the suite a class names. `PublicSurfaceManifestCoversEveryServedCliCommandTest` lives in
  `com.morpheus.cli` and calls `CliCommandPaths` without an import: a test in a production package loads a production
  class.
- 1.5: `architectureTests=846` in the `validation-summary.txt` of the `develop` CI run `40961140381` at `6b3f6304`.
- 2.1: `TestSourceText` (comments, text blocks and character literals blanked; string literals blanked or kept). The
  neutralisation of `TestFactoryAssertsItsSourceTest` moved there rather than a third copy being written; its fixture
  test still passes. `theFamilyCountsOnlyABanOfATypeWrittenInCodeWithNoMessage`, `theCompositionCountsReadCodeOnly`.
- 2.2: `EnforcementMechanismAccountingTest#theTextualBansOfATypeThatStateNoIntentDoNotGrow`, ceiling 196.
- 2.3: a member added in `TestFactoryAssertsItsSourceTest` failed with "197 textual bans of a type state no intent, the
  ceiling is 196: a textual ban of a type states its intent as the assertion's message" and the members by file; a
  member of `LocalHttpQueryArchitectureTest` given a message failed with "fell to 195: lower
  UNEXPLAINED_TYPE_BAN_CEILING to 195"; a member added with a message passed. Each reverted.
- 3.1, 3.2: `morpheus-architecture-tests/target/enforcement-composition-summary.txt`, first line `method=`;
  `theSuitePublishesTheCompositionOfItsEnforcementByOneStatedMethod` asserts the method line, the keys and that each
  count is a number, never a value. The file is added to the `m21-integrity-<OS>` artefact of `ci.yml`.
- 4.1: `ArchitectureSuiteSources`: the class's own text, then the classes of the suite it names, to any depth
  (`ProductionSources` is recognised by its main-source methods and not followed). Fixtures of each kind in
  `CoherenceCheckMarkerTest`, including a helper of another class that reads `src/main` (followed) and one that reads
  `docs/` (not a read of code).
- 4.2: `@CoherenceCheck` (JUnit tag `coherence`) on the eighteen classes of 1.4 and on the two new coherence gates;
  `CoherenceCheckMarkerTest#everyTestClassThatReadsNoCodeIsMarkedAndNoOtherIs`.
- 4.3: the marker removed from `AdrIndexCoherenceTest` and a `src/main` literal added to the marked `RepositoryRootTest`,
  in one run: both named, each in its direction. Reverted.
- 4.4: `./mvnw test -pl morpheus-architecture-tests -am -Dgroups=coherence` on Windows: 20 classes, 122 executions,
  green. No workflow runs that selection, so Linux is covered by the default selection of the pull request's CI. The
  default selection of `clean verify` is unchanged: no POM sets `groups` or `excludedGroups`.
- 5.1, 5.2: `.claude/rules/architecture.md` (obligation 1, and the section "Un interdit textuel de type écrit son
  intention"), `.claude/skills/enforcement-choice/SKILL.md`. No figure copied.
- 5.3: `docs/developer/BUILD_AND_TEST.md`, beside the coverage proof files.
- 5.4: `docs/architecture/risks/register.md`, `DT-19`.
- 6.1: `./mvnw clean verify` on Windows, green; the proof file of each platform is in the `m21-integrity-<OS>` artefact
  of the pull request's CI.
- 6.2: the module runs more executions than 1.5 (the lot adds 14 tests). `architectureTestsMinimum` is not raised in
  this lot: a raise is qualified on Linux and Windows, and sprints 11 and 13 change the same key in parallel. It is
  left to the maintainer after the merges.
- 6.3: the pull request description.
