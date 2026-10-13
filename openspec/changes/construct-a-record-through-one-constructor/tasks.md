# Tasks

## 0. Decide before starting

- [ ] 0.1 The maintainer answers the first open question of the design (SDK API version). Proof: the answer recorded
      in this file; it fixes whether `Constraint` and `NormalizedProjectContent` are converted or listed.

## 1. Measure before changing

- [ ] 1.1 Write the structural test with an empty exception list and run it. Proof: the records it names; on
      2026-10-13 a source scan finds seventeen constructors in fifteen records (the audit counted eight, by Javadoc).
      Neither number is trusted: the list of the test is the measure.
- [ ] 1.2 For each constructor, find its callers with the compiler: remove it, compile the reactor with tests, record
      which classes fail, restore. Proof: the table (none / tests only / production), beside the one of the proposal.
      Constructor references (`X::new`) are looked for explicitly.
- [ ] 1.3 List the Javadocs of `src/main/java` that justify an element by compatibility. Proof: the list; on
      2026-10-13, nine constructors, the two `default` methods of `VersionedRequirementStore`, the MCP stub, and the
      two `@Deprecated` methods that `remove-the-unpinned-plugin-entry-points` carries.

## 2. The change

- [ ] 2.1 Remove the constructors without any caller. Proof: the reactor compiles; no test is edited.
- [ ] 2.2 Remove the constructors only tests call; each test calls the canonical constructor or a fixture of its own
      module that reproduces the removed default exactly. Proof: the same tests pass with their assertions unmodified.
- [ ] 2.3 Replace each constructor with production callers by a named factory (or list it, per 0.1); update the
      OpenSpec and Markdown providers and the reference plugin. Proof: a test per factory asserting every defaulted
      component; the provider tests pass unmodified.
- [ ] 2.4 Re-document the two `default` methods of `VersionedRequirementStore` as the port's fallback, or make them
      abstract (design, third open question). Proof: the search of 1.3 no longer returns them.

## 3. The gate

- [ ] 3.1 The structural test green with its final list. Mechanism: a test over the imported production records that
      compares public constructors to record components - intent "a record offers one public way to be constructed";
      not a textual assertion, because the word is not the property.
- [ ] 3.2 Break it once, then revert: restore one removed constructor without its Javadoc. Proof: the failure names
      the record.
- [ ] 3.3 Break the two-way check once: add a clean record to the exception list. Proof: the named failure.

## 4. Documentation

- [ ] 4.1 `.claude/rules/code-style.md`: line 13 states the rule for records and cites the test; line 61 gains the two
      bounds of the design, decision 5.
- [ ] 4.2 `docs/developer/PROVIDER_SDK.md`: how a plugin builds a `NormalizedProjectContent` and a `Constraint` now.
      `docs/user/release-notes/` for the version that ships it: the source-incompatible change for plugin authors, in
      product terms.
- [ ] 4.3 Dated records are left as they are.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through
      the required checks; the changed-line coverage gate passes.
- [ ] 5.2 The M22 gates (they load the reference plugin's JAR from `target/`). Proof: green.
- [ ] 5.3 Presence ratchets: tests removed with a constructor they alone exercised lower the count of executions;
      read `config/m21-quality-ratchets.properties` and confirm the margin holds. A ratchet is never lowered - a test
      is rewritten rather than deleted when the margin would not hold.
- [ ] 5.4 Pull request description: the finding, the two tables, the factories and what each assumes, the answer to
      0.1, the justification for a change under `morpheus-architecture-tests/`.
