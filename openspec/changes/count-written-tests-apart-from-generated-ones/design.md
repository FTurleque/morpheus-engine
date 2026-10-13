# Design

## Context

- A presence ratchet exists to make a silent loss of tests fail the build. It counts executions as Surefire reports
  them; a factory that yields forty dynamic tests counts forty.
- `testsMinimum` and `architectureTestsMinimum` are read from `config/m21-quality-ratchets.properties` by
  `validate-m21.*` and `validate-d2.*` (capability `gate-verdict-currency`), through the readers of `scripts/lib/`.
- The repository's rule is that a ratchet does not go down. It already keeps two keys per coverage scale so that one
  measure does not govern another (ADR-0104).
- The three factory classes build their cases from the CLI's real commands and options: their count is a function of
  the product's surface.

## Goals / Non-Goals

**Goals**: a change of the option matrix and a loss of written tests are two different refusals with two different
names; removing a dead option no longer requires touching the ratchet that guards written tests.

**Non-Goals**: reducing the factories or their matrix; a ratchet on the number of test methods in the sources (a scan
of annotations is not what ran); the coverage ratchets; the performance budgets.

## Decisions

1. **Two counts, defined by where an execution comes from.** A *written* execution is the one execution of a declared
   test method. A *generated* execution comes from a factory or from any other source that produces more than one
   execution per method. The definition is by origin, so a parameterised test introduced later is generated without
   anyone having to remember this change.

2. **How the two are told apart is measured before it is chosen.** Whether Surefire's XML lets a reader recognise a
   dynamic test is not verified in this specification. It is the first task. If the report does not allow it, the
   count is taken by class: the executions of a class that declares a generating method, less its declared methods. The
   requirement states the result, not the mechanism.

3. **`testsMinimum` keeps its name and is re-based once on the written count; a new key bounds the generated count.**
   The two new values are taken from one qualification on both platforms, on the lowest measurement, and at the split
   their sum is not lower than the single minimum they replace: nothing that was required stops being required. The
   number written beside `testsMinimum` goes down because it measures something smaller, and the ADR amendment says so.
   *Rejected*: the audit's two keys with `testsMinimum` left as the total — the total is the confusion.
   *Rejected*: no minimum at all on generated executions — a factory deleted whole would pass.

4. **The generated minimum follows the option matrix; the written minimum only rises.** Lowering the generated key is
   legitimate in the change that removes the option or the command that produced the executions, and that change says
   which. This is a rule of governance, written in `rules/testing.md`; no test can tell a justified shrink from an
   unjustified one, and the design does not pretend otherwise. What a test does guarantee is that the shrink is
   reported under its own name.

5. **An empty factory fails.** Each factory asserts that its matrix is not empty. Without it a factory whose source of
   commands returned nothing would generate no test and report no failure; the generated minimum would catch it only
   if the margin is smaller than that factory's share.

6. **ADR amended, not new.** The decision to hold presence by ratchet stands; the amendment adds the second measure.

## Risks / Trade-offs

- The re-basing will be read as a ratchet going down. The pull request shows the three numbers of the qualifying runs
  — total, written, generated — per platform, and the sum.
- Every page that states "the Surefire baseline" changes meaning. The coherence tests enumerate them; none is edited
  from a list.
- If the report cannot distinguish the two origins and the count is taken by class, a class mixing declared and
  generating methods depends on a count of declared methods read from the report, not from the source. Task 1.2 decides
  on evidence.

## Open Questions

1. **Keep the name `testsMinimum` for the written count, or retire it for two new names?** Recommendation: keep it. New
   names avoid a number that visibly decreases, but they rename a key that the capability `gate-verdict-currency`, the
   D2 textual assertions and a dozen pages name; the decrease is explained once, in the ADR and in the file's comment.
2. **Should `architectureTestsMinimum` be split too?** No factory lives in `morpheus-architecture-tests` today.
   Recommendation: no second key; the requirement makes that minimum count written executions, so a factory added there
   later is counted under the generated key.
