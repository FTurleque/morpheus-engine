# Design

## Context

- The archived ADR-0103 defines the "mechanical family" by a regular expression written in its context section, and has re-counted
  it three times with it (198, 193, now 196). Every decision of its section of 11/09/2026 was taken on that count.
  The expression matches a single-argument `assertFalse`: the form with no message.
- JUnit's `assertFalse(condition, message)` prints the message in the failure. A member of the family fails today
  with `expected: <false> but was: <true>` and the line number.
- The audit, the ADR and this proposal obtained three different totals for "assertions on a `contains`" from three
  expressions. None is wrong; none is a source.
- The repository already has the pattern of a proof file written by a gate and read by people and validators
  (`target/m21-aggregate-coverage-summary.txt`, with its first line stating its scope).
- `architectureTestsMinimum` counts the Surefire executions of the gate module. A milestone's suite lives in
  `.../architecture/m<N>/` by the quadruplet rule (`rules/testing.md`), whatever its tests read.
- Sprint 11 keeps the textual bans on `application.reasoning` and `application.policy` on purpose
  (`decide-what-technology-the-application-core-may-touch`, decision 2) and adds ArchUnit rules beside them.

## Goals / Non-Goals

**Goals**: the part of the textual enforcement that is a choice cannot grow unnoticed; a new one says what it means;
the composition of the gate module is read from one place by one method; a check on documents can be told from a
check on code without leaving the module.

**Non-Goals**: a threshold on the share of textual assertions; migrating any assertion to ArchUnit (the third group of the archived ADR-0103,
held by `.claude/rules/architecture.md`, stays closed until a fact reopens it); moving a test class to another module or another package; splitting a
mixed class; touching the assertions on non-Java artefacts.

## Decisions

1. **What only shrinks is the unexplained family, not the share.** The population is the archived ADR-0103's own: an
   `assertFalse` whose single argument is `<identifier>.contains("<TypeIdentifier>")`. The gate holds the expression,
   proves it on fixtures in both directions (a member, a member with a message, a lower-case literal, a call inside
   a comment or a string), and compares the measured count with a ceiling. *Rejected*: a ceiling on the ratio of
   textual assertions to rules - see the proposal; it rewards the wrong edits. *Rejected*: a ceiling on every
   `contains` assertion - most of them read artefacts for which text is the only handle.

2. **The ceiling is exact, and lives in the gate.** The measured count must **equal** the declared ceiling: one more
   fails, and one fewer fails until the ceiling is lowered in the same change - the semantics of
   `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider`. The ceiling is a constant of the gate class,
   with the date and the commit of the measure in its Javadoc, as the qualified coverage ceilings are. Raising it is
   an edit of a governance file that the pull request must justify (`rules/governance.md`), and
   `.claude/rules/architecture.md` says no reason is known that would justify it. *Rejected*: a key in
   `config/m21-quality-ratchets.properties` - that file is mirrored in seventeen places (`rules/meta.md`) and its
   keys are read by validators that have no use for this one. *Rejected*: "not greater than" - a silent drop followed
   by a silent return to the ceiling is growth nobody saw.

3. **A new textual ban of a type states its intent as the assertion's message.** It then leaves the family by
   construction - the expression matches the single-argument form - and its failure says which proposition was
   violated: the mention, or the dependency the mention stands for. The gate does not judge the wording; a reviewer
   does. This is the executable form of "write the intent in one sentence" and replaces the audit's "in the pull
   request description". The members with a message are counted in the proof file, without a ceiling.

4. **The gate module publishes its own composition.** One proof file under `target/`, first line naming the method,
   then: test classes, executed-test declarations, rules evaluated, textual assertions over a production source,
   textual assertions over another artefact, the unexplained family, the explained one. Each figure is produced by
   an expression held in the gate and proved on a fixture. Documents and audits cite the file and its method, not a
   figure. No assertion bears on these counts beyond decision 2: they are a reading, not a gate.

5. **A coherence check is marked, not moved.** A test class of the gate module that imports no ArchUnit type, reads
   no file under a `src/main` directory and loads no production class carries a JUnit tag; a gate derives that
   property from the class's source and fails when the tag and the property disagree, in either direction. The class
   stays where it is, milestone packages included, so no ratchet and no quadruplet moves. *Rejected*: a dedicated
   verifier outside the module - it lowers `architectureTestsMinimum`. *Rejected*: tagging methods of mixed classes -
   65 of the 69 classes that read production sources also read artefacts; deriving the property per method is a
   parser, and splitting those classes is a refactoring this change has no reason to force.

6. **`.claude/rules/architecture.md` states it, not an ADR**: the criterion of the archived ADR-0103 is a test convention. One section: the family is defined by the gate; its ceiling; the
   message rule; the refusal of a cap on the share and why; the marker. The figures of the archived ADR-0103 are dated
   records and are not rewritten.

## Risks / Trade-offs

- A message can be written without thought, and the family shrinks on paper. The proof file shows the explained
  family growing as the unexplained one shrinks: a transfer is visible, even if its quality is not.
- The expression is narrow by design. A ban written `assertTrue(!source.contains("X"))`, or through a helper
  (`assertNoSourceMentions`), is outside it. Helpers that take an intent are the better form and are not the target;
  the fixture tests say what is and is not counted so that nobody mistakes the gate for a census.
- An exact ceiling makes two pull requests that each remove a member conflict on one constant. That is rare, and it
  is the price of a count that cannot drift.
- The marker gives a failure a name only where the build uses it. Until a workflow runs the tag as its own step,
  it is a selection handle and a classification that cannot lie, no more.

## Open Questions

- **Should the coherence classes run as a separately named step in `ci.yml`?** Recommendation: not in this change; it
  doubles a Maven invocation of the gate module on two platforms. Decide once the marker exists and the count of
  marked classes is known.
- **Should `express-adapter-internal-roles-as-packages` be allowed to retire the family-wide textual assertions that
  its package rules duplicate?** The archived ADR-0103 kept them on purpose (group 1 "removes no assertion"), and `.claude/rules/architecture.md` keeps them, rule and text. Recommendation:
  only those whose banned type declares no compile-time constant, one by one, each lowering the ceiling.
- **Ceiling in the gate class or in a properties file?** Recommendation: the gate class, by decision 2.
