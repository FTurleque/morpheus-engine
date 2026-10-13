# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-run the eight commands of the proposal's table on the lot's base. Proof: the table with its date and
      commit. Do not trust either column of the proposal; the audit, ADR-0103 and the proposal already disagree.
- [ ] 1.2 List the members of the mechanical family by file, and for each file whether its subject is a class of a
      module on the ArchUnit classpath (re-read the `pom.xml` of `morpheus-architecture-tests`, not a rule page).
      Proof: the list; on 2026-10-13, 196 members in 31 files, 9 more of the same form with a message.
- [ ] 1.3 List the textual bans of a type written another way - `assertTrue(!x.contains(...))`, a helper, a loop over
      literals - so that the fixture tests of 2.1 state what the measure does not count. Proof: the list.
- [ ] 1.4 List the test classes that import no ArchUnit type, read nothing under a `src/main` directory and load no
      production class. Proof: the list; a scan of path literals on 2026-10-13 suggested about nineteen.
- [ ] 1.5 Take the executed-test count of the module from a validation summary of the base (`live-numbers`).

## 2. The measure and its ceiling

- [ ] 2.1 The fixture tests of the expression, in both directions, before the gate reads the real sources: comments,
      strings and text blocks are not code. Reuse the neutralisation that
      `CliOptionParsingRefusesUnknownOptionsTest` or `UnreferencedPublicMethodArchitectureTest` already has rather
      than writing a third; if it has to be shared, it is copied per the module's own convention, not moved to a new
      module.
- [ ] 2.2 The gate, with the ceiling set to the count of 1.2 and the commit of the measure in its Javadoc. Intent, in
      one sentence: *the number of textual bans of a type that state no intent does not grow* - a property of test
      sources, so textual by nature (`enforcement-choice`).
- [ ] 2.3 Break it three ways and revert each: a member added (fails, names the message rule); a member removed
      without lowering the ceiling (fails, asks to lower it); a member added **with** a message (passes). Proof: the
      three outcomes.

## 3. The proof file

- [ ] 3.1 The proof file under the module's `target`, first line the method. Each expression has its fixture.
- [ ] 3.2 A test that the file is written and complete. It asserts presence and form, never a value.

## 4. The coherence marker

- [ ] 4.1 The derivation of the property from a test class's source, with fixtures: a class of each kind, and a class
      that reads a production source through a helper of another class of the suite (state whether the derivation
      follows it; if not, say so in the gate's Javadoc as a known limit).
- [ ] 4.2 The marker on the classes of 1.4, and the gate that compares marker and property in both directions.
- [ ] 4.3 Break it twice and revert: a marked class given a `src/main` read; an unmarked documentation class.
- [ ] 4.4 Run the module with the marker as the only group. Proof: the classes run and their count, on both
      platforms - a group selection is a Surefire setting, and the default selection of `clean verify` must be
      unchanged (compare the executed-test count with 1.5 plus the tests this change adds).

## 5. Decision record and rules

- [ ] 5.1 The dated amendment of ADR-0103 (design, decision 6). Dated figures of the ADR are not rewritten.
- [ ] 5.2 `.claude/rules/architecture.md` and `.claude/skills/enforcement-choice/SKILL.md`: a textual ban of a type
      carries its intent as its message; the family only shrinks; the proof file is where a count is read. No figure.
- [ ] 5.3 `docs/developer/BUILD_AND_TEST.md`, if it lists the proof files of a build: the new one, with its method.
- [ ] 5.4 `docs/architecture/risks/register.md`: an entry for the family as a debt whose counter is the ceiling.

## 6. Verification

- [ ] 6.1 `./mvnw clean verify` on Linux and Windows: the measure reads source files, and line endings and the
      default charset must not change a count. Proof: the proof file of each platform, identical.
- [ ] 6.2 The executed-test count is higher than 1.5; `architectureTestsMinimum` is raised by the `coverage-ratchet`
      procedure, with every destination that `RepositoryDocumentationCoherenceTest` and
      `ProductionIntegrityContractTest` then name.
- [ ] 6.3 Pull request description: the finding, the table of 1.1, why the share is not capped, and the justification
      of each file changed under `morpheus-architecture-tests/` (`rules/governance.md`).
