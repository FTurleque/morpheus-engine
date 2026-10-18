# Tasks

## 1. Measure before changing — the premise of this change is not verified yet

- [x] 1.1 On a real `./mvnw clean verify`, open the Surefire report of each of the three factory classes and record how
      a dynamic test appears (`testcase` name and class attributes), on Windows and on Linux. Proof: an excerpt per
      platform. This decides decision 2 of the design; do not write the counting before it.
- [x] 1.2 From the same reports compute the total, the generated count and the written count, per platform, twice.
      Proof: the table. Never derive any of them from a scan of annotations: on 2026-10-13 the sources hold 13
      `@TestFactory` and about 2 429 `@Test`, and neither number is a count of executions.
- [x] 1.3 List every generating method of the repository (`@TestFactory`, `@ParameterizedTest`, `@RepeatedTest`,
      `@TestTemplate`). Proof: the list; on 2026-10-13, thirteen factories in three files of `morpheus-cli` and nothing
      else.
- [x] 1.4 List what names `testsMinimum`: scripts, readers, tests, pages, rules, skills. Proof: the list, from a search
      of the whole repository.

## 2. Reproduce before fixing

- [x] 2.1 On the fabricated checkout of `test-the-validators-that-give-a-verdict` (or a minimal one if that change has
      not landed), give `validate-m21` reports whose generated executions dropped by more than the margin while the
      written ones did not. Proof: on `HEAD`, the refusal reads as a regression of the test baseline — the confusion
      this change removes.
- [x] 2.2 Empty the source of one factory. Proof: on `HEAD`, zero tests generated and no failure.

## 3. The change

- [x] 3.1 The two counts in both validators of `m21` and `d2`, printed and written in the summary. Proof: identical on
      the two platforms for the same reports.
- [x] 3.2 The generated key in `config/m21-quality-ratchets.properties` and the re-basing of `testsMinimum`, both from
      the lowest measurement of 1.2 with the margin the file's history uses, and such that their sum is not lower than
      the minimum they replace. Follow `coverage-ratchet` for the form of the evidence and `live-numbers` for every
      number written. Proof: the three numbers per platform and the sum, in the pull request.
- [x] 3.3 Read both keys through the existing readers; extend the readers' shared test cases to the new key.
- [x] 3.4 The assertion of each factory, and the rule that refuses a factory without it. Mechanism: textual assertion
      over `src/test/java`, intent "a generating method that does not assert its source is not empty must not appear"
      (ADR-0103; skill `enforcement-choice`).
- [x] 3.5 Update the textual assertions that pin the keys (`D2RepositoryHardeningArchitectureTest` and siblings) on
      purpose, never to make them pass.
- [x] 3.6 Break each rule once, then revert: drop the generated key; set it to zero; make a validator compare the sum;
      remove a factory's assertion. Proof: the named failures.

## 4. Verification and destinations

- [ ] 4.1 `./mvnw clean verify` on both platforms, then `validate-m21` on each. Proof: both summaries.
- [x] 4.2 Run `RepositoryDocumentationCoherenceTest` and `ProductionIntegrityContractTest` and let their failures name
      the pages to update; a dated measurement is not rewritten.
- [x] 4.3 Amend the ADR that carries the presence ratchets (expected ADR-0089; `adr-author` confirms) with the second
      measure and the statement that the re-basing lowers nothing.
- [x] 4.4 `.claude/rules/testing.md` and `.claude/rules/governance.md`: the two keys, and the rule that the generated
      minimum may follow the option matrix down in the change that shrinks it while the written minimum only rises.
      `.claude/skills/live-numbers/SKILL.md`: the new key among the living numbers.
- [x] 4.5 Pull request description: the finding, the measurement of 1.1, the numbers of 3.2, the justification of each
      governance file touched.
