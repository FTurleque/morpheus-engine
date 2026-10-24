# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Confirm on the lot's base that nothing sets the guard: no `archunit.properties` in the repository, no
      `archunit.` system property in a POM, a validator or a workflow, no `allowEmptyShould` in a test source. Proof:
      the three searches and their empty results; that was the state on 2026-10-13 at `8b26d3d7`.
- [ ] 1.2 Read, in the ArchUnit version the root POM pins (`archunit.version`; do not copy it), the three entrances
      of the setting and the exact message of a rule that checked no class. Proof: the names and the message, quoted
      in the test's Javadoc with the version they were read in.

## 2. Reproduce before fixing

- [ ] 2.1 The behavioural test. Proof that it bites, in a scratch state reverted afterwards: with an
      `archunit.properties` turning the guard off on the test classpath, it fails; with the system property passed to
      Surefire, it fails; with neither, it passes.
- [ ] 2.2 Make the vacuous rule fail for another reason once (an import of a package that throws) and confirm the
      test does not accept it. Revert.

## 3. The change

- [ ] 3.1 The scan of the suite's own sources for the per-rule opt-out, comments and strings neutralised. Break it
      once with a real call on a real rule; confirm a mention in a Javadoc does not trip it. Revert.
- [ ] 3.2 The scan of the repository for a committed setting that turns the guard off: every `archunit.properties`,
      the POMs, `scripts/`, `.github/workflows/`. Fixture tests in both directions (off refused, another property
      accepted, the property set to true accepted). Break it once on the real tree with a property file in
      `morpheus-architecture-tests/src/test/resources`. Revert.
- [ ] 3.3 Three tests, one per intent; none grouped (`enforcement-choice`, "split by intent").

## 4. Decision record and rules

- [ ] 4.1 No ADR: the guard is obligation 1 of `.claude/rules/architecture.md` (4.2), proved by a named test; the manual verification
      of 10/09/2026 stays as the dated record it is.
- [ ] 4.2 `.claude/rules/architecture.md` (obligation 1 of the three) and
      `.claude/skills/enforcement-choice/SKILL.md`: "do not disable it" names the test.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify` on Linux and Windows: the repository scan reads paths and must not depend on the
      separator or on the default charset.
- [ ] 5.2 The executed-test count of `morpheus-architecture-tests` before and after, read in a validation summary;
      `architectureTestsMinimum` is raised by the `coverage-ratchet` procedure if the maintainer wants the new tests
      locked in.
- [ ] 5.3 Pull request description: the sentence of AUD-TRV-03 this closes, the outcomes of 2.1, 2.2, 3.1 and 3.2.
