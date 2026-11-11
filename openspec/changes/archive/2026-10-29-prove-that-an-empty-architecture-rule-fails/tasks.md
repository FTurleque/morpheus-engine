# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 Confirm on the lot's base that nothing sets the guard: no `archunit.properties` in the repository, no
      `archunit.` system property in a POM, a validator or a workflow, no `allowEmptyShould` in a test source. Proof:
      the three searches and their empty results; that was the state on 2026-10-13 at `8b26d3d7`.
- [x] 1.2 Read, in the ArchUnit version the root POM pins (`archunit.version`; do not copy it), the three entrances
      of the setting and the exact message of a rule that checked no class. Proof: the names and the message, quoted
      in the test's Javadoc with the version they were read in.

## 2. Reproduce before fixing

- [x] 2.1 The behavioural test. Proof that it bites, in a scratch state reverted afterwards: with an
      `archunit.properties` turning the guard off on the test classpath, it fails; with the system property passed to
      Surefire, it fails; with neither, it passes.
- [x] 2.2 Make the vacuous rule fail for another reason once (an import of a package that throws) and confirm the
      test does not accept it. Revert.

## 3. The change

- [x] 3.1 The scan of the suite's own sources for the per-rule opt-out, comments and strings neutralised. Break it
      once with a real call on a real rule; confirm a mention in a Javadoc does not trip it. Revert.
- [x] 3.2 The scan of the repository for a committed setting that turns the guard off: every `archunit.properties`,
      the POMs, `scripts/`, `.github/workflows/`. Fixture tests in both directions (off refused, another property
      accepted, the property set to true accepted). Break it once on the real tree with a property file in
      `morpheus-architecture-tests/src/test/resources`. Revert.
- [x] 3.3 Three tests, one per intent; none grouped (`enforcement-choice`, "split by intent").

## 4. Decision record and rules

- [x] 4.1 No ADR: the guard is obligation 1 of `.claude/rules/architecture.md` (4.2), proved by a named test; the manual verification
      of 10/09/2026 stays as the dated record it is.
- [x] 4.2 `.claude/rules/architecture.md` (obligation 1 of the three) and
      `.claude/skills/enforcement-choice/SKILL.md`: "do not disable it" names the test.

## 5. Verification

- [x] 5.1 `./mvnw clean verify` on Linux and Windows: the repository scan reads paths and must not depend on the
      separator or on the default charset.
- [x] 5.2 The executed-test count of `morpheus-architecture-tests` before and after, read in a validation summary;
      `architectureTestsMinimum` is raised by the `coverage-ratchet` procedure if the maintainer wants the new tests
      locked in.
- [x] 5.3 Pull request description: the sentence of AUD-TRV-03 this closes, the outcomes of 2.1, 2.2, 3.1 and 3.2.

## Proofs of the checked tasks (2026-10-29, from `6b3f6304`)

- 1.1: no `archunit.properties` outside `target/`; no `archunit.` system property in a POM, `scripts/`,
  `.github/workflows/` or `.mvn/`; `failOnEmptyShould`, `allowEmptyShould` and `ArchConfiguration` appear only in prose
  (`.claude/rules/architecture.md`, the enforcement-choice skill, the archived ADR-0103, the 2026-10-09 audit).
- 1.2: read in ArchUnit 1.5.1 (`ArchRule.SimpleArchRule`, `AllowEmptyShould`, `ArchConfiguration`): the entrances are
  `archunit.properties` at the root of the classpath, the system property `archunit.archRule.failOnEmptyShould`, and
  `ArchRule.allowEmptyShould(true)`; only `false`, in any case, turns the guard off; the message is "Rule '...' failed to
  check any classes", quoted in the Javadoc of `AnEmptyArchitectureRuleFailsTest`.
- 2.1: with `morpheus-architecture-tests/src/test/resources/archunit.properties` turning the guard off,
  `aRuleThatSelectsNoClassFailsForHavingCheckedNone` failed ("a rule that selects nothing now passes") and
  `noCommittedSettingTurnsTheGuardOffForAnyInvocationOfTheGates` named the file; with
  `-Darchunit.archRule.failOnEmptyShould=false` on the Maven command line, the first test failed the same way; with
  neither, the five tests pass. Reverted.
- 2.2: the import narrowed to a package that does not exist: the test failed on its precondition ("the import retained
  no class"). The rule made non-vacuous and violated (`com.morpheus.domain..` should be private): it failed with "the
  vacuous rule failed for another reason than emptiness". Reverted.
- 3.1: `.allowEmptyShould(true)` added to the first rule of `LayerDependencyTest`: named as
  `LayerDependencyTest.java:29`. The mentions in the Javadoc and in the fixtures of the new class do not trip it
  (`anOptOutNamedInACommentOrALiteralIsNotACall`). Reverted.
- 3.2: `noCommittedSettingTurnsTheGuardOffForAnyInvocationOfTheGates` and
  `theSettingScanRefusesOffAndAcceptsTrueAndOtherProperties` (off refused; `true` accepted; `cycles.maxNumberToDetect`
  accepted; in a property file, on a command line and in a POM). The scan skips `.git`, `target`, `node_modules` and
  `.claude`, whose agent worktrees hold copies of the tree. Broken on the real tree in 2.1.
- 3.3: five tests in one class: the behaviour; the per-rule opt-out and its fixture; the global setting and its fixture.
- 4.1: no ADR.
- 4.2: `.claude/rules/architecture.md` (obligation 1) and `.claude/skills/enforcement-choice/SKILL.md` name the test.
- 5.1: `./mvnw clean verify` on Windows; Linux by the pull request's CI. The scan compares relative paths with forward
  slashes and reads files as UTF-8.
- 5.2: `architectureTests=846` at `6b3f6304` (CI run `40961140381`); the lot adds 14 executions to the module.
  `architectureTestsMinimum` is left to the maintainer, as this task allows.
- 5.3: the pull request description.
