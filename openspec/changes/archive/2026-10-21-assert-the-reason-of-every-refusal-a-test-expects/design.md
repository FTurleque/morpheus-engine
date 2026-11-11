# Design

## Context

- `assertThrows` returns the exception. A test asserts a reason only if it keeps that value and compares something of
  it: `getMessage()`, a code accessor of a project exception, a cause.
- The project names its refusals (`rules/code-style.md`): messages such as `--id requires a non-blank value`, codes such
  as `PLUGIN_SHA256_REQUIRED`. `Objects.requireNonNull(value, "name")` gives a `NullPointerException` whose message is
  the argument's name, so most null refusals have a reason to assert.
- `BoundedWaitOwnershipTest` is the precedent: a textual test over test sources, in `morpheus-architecture-tests`, that
  refuses a form by reading the text.
- The test classes of the other modules are not on the classpath of `morpheus-architecture-tests`, and whether a return
  value is read is a property of the source, not of a dependency.

## Goals / Non-Goals

**Goals**: a refusal test fails when the product refuses for another reason; the next bare `assertThrows` on a JDK type
is refused the day it is written; the backlog is drained without one unreviewable commit.

**Non-Goals**: judging that the asserted message is the right one — the test makes the assertion exist, review reads it;
changing the wording of a product message; `assertThrows` on a type of the project (counted, see open question 1);
`assertDoesNotThrow`, `fail()` in a `try`, and other forms of the same weakness, which the scan of task 1.2 counts
without gating.

## Decisions

1. **Textual assertion, in `morpheus-architecture-tests`.** Intent in one sentence: "an `assertThrows` on a JDK type
   whose result nobody reads must not appear under `src/test/java`". It is a ban on a form of the source, over files
   outside the imported classpath: text is the only hold (ADR-0103). No ArchUnit twin.

2. **"JDK type" means a type the repository does not declare.** The test builds the set of type names declared under
   `morpheus-*/src/main/java` and `src/test/java` and treats any other expected type as generic. *Rejected*: a fixed
   list of JDK names — it misses the next one (`DateTimeException`, `UncheckedIOException`).

3. **"Reads the result" means: bound and used, or passed on.** The call is accepted when its value is passed directly
   to another call, or assigned to a variable whose name appears again in the same method. A variable assigned and never
   named again is refused like a discarded result. The test cannot tell that the later use is an assertion on the
   reason; it tells that a reader was given the chance.

4. **A list that can only shrink, by file, with an exact count.** Until a module is corrected its offending files are
   listed with the number of sites each still holds. The measured set must equal the list: a new site fails, and a
   corrected file fails until its entry is lowered or removed. The boundary modules (`morpheus-api`, `morpheus-cli`,
   `morpheus-mcp`) leave the list in the first lot. The list is not a permanent allowlist: its last entry is removed
   with the list itself. *Rejected*: correcting everything in one pull request — more than three hundred assertions
   whose expected message has to be read in the product each time. *Rejected*: gating only new files — a file that
   exists keeps receiving bare assertions.

5. **The architecture tests are a lot of their own, early.** A proof that a gate refuses a violation, written
   `assertThrows(AssertionError.class, ...)` with nothing read, passes when the gate fails for an unrelated reason —
   for instance because its fixture no longer parses. These are corrected right after the boundaries.

6. **No ADR.** The rule exists in `rules/testing.md`; this change makes it executable.

## Risks / Trade-offs

- Asserting a message couples a test to wording. That is the rule's intent — the named refusal is the contract — and
  the cost is one test edit when a message is reworded on purpose.
- Some refusals carry no message (an implicit null dereference, an exception rethrown bare). Asserting `null` would
  satisfy the gate and prove nothing. Those sites are listed in the pull request; at a system boundary a refusal without
  a reason is a finding for the product, reported, not fixed here.
- The textual reading of "used again in the method" is approximate. Task 2.2 runs it against the shapes found in the
  repository before it is trusted, and the rule is broken once in each shape.

## Open Questions

1. **Should a discarded `assertThrows` on a type of the project be refused too?** 127 sites today. A type such as
   `SyncBaselineInconsistentException` is its own reason; a type such as `KnowledgeStoreException` or `CliFailure`
   covers many. Recommendation: not in this change. Task 1.3 lists the project types by how many distinct refusals each
   carries; the maintainer then decides whether the broad ones join the rule.
2. **Should the list live in the test or in a file under `config/`?** Recommendation: in the test, as
   `AdapterCompositionRootArchitectureTest` holds its named roots — the list is transitional and a governance file is
   not.
