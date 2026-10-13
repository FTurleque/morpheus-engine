# Design

## Context

- ArchUnit evaluates a rule whose `that()` clause retains no class as a failure ("failed to check any classes") when
  `archRule.failOnEmptyShould` is true, its default in the version the root POM pins (`archunit.version`).
- ADR-0103 recorded one manual verification of it: a rule aimed at a class outside the imported classpath broke the
  build. It was an experiment, not a test.
- The setting has three entrances: `archunit.properties` at the root of the test classpath, a system property of the
  same name prefixed with `archunit.`, and `ArchRule.allowEmptyShould(true)` on one rule. The first two are global;
  the third is local and reads like a harmless convenience in a diff.
- The guard does not cover everything the suite calls "empty": a family that shrinks from seventeen members to
  sixteen still retains classes. `HttpRoutesFamilyArchitectureTest` and `ImportedClasspathCompletenessTest` exist
  for that, and are not in question here.

## Goals / Non-Goals

**Goals**: the guard is a tested property of the build that runs the gates; switching it off, globally or for one
rule, fails that build.

**Non-Goals**: forbidding an ArchUnit configuration file; proving that every rule retains the classes its author
meant (that stays the obligation "break the rule once" of ADR-0103); upgrading ArchUnit.

## Decisions

1. **Behaviour first: evaluate a vacuous rule and expect the failure.** A test builds a rule whose `that()` selects a
   package that cannot exist and checks it against the classes the suite imports; the test passes only if the
   evaluation fails for the reason of emptiness. Intent, in one sentence: *in the configuration these gates run
   under, a rule that selects nothing fails* - neither a dependency nor a mention, so neither ArchUnit nor text: it
   is executed (ADR-0103, amendment CLI-6, same reasoning). This holds against all three entrances at once for the
   global setting, and against a change of the upstream default. *Rejected*: asserting the value of the configuration
   property - it tests the entrance, not the effect, and names an internal of the library.

2. **Text for the local entrance.** The behavioural test cannot see `allowEmptyShould(true)` on another rule. A scan
   of the test sources of the gate module refuses the call, code only (comments and strings neutralised, as the
   module's other scans do). Intent: *this call does not appear here* - a mention, so text.

3. **Text for the two global entrances, as a second line.** A scan of the repository refuses a line that sets
   `archRule.failOnEmptyShould` to anything but true in any `archunit.properties`, and the same property in a POM, a
   validator or a workflow. Decision 1 already catches the effect in the build it runs in; this catches a setting
   that only applies to another invocation - a profile, a validator's own Maven command line. An
   `archunit.properties` that sets something else passes.

4. **The assertion on the reason, not only on the failure.** The vacuous rule must fail *because it checked no
   class*. A test that accepts any failure would pass if the import itself broke (`rules/testing.md`: a failure for
   another reason must not make the test pass).

5. **One class, in the root package of the suite, named for what it proves.** Three tests, by intent: the behaviour,
   the per-rule opt-out, the global setting.

## Risks / Trade-offs

- The wording of ArchUnit's message is not an API. The test pins the smallest stable part of it and says in its
  Javadoc what to re-read when a version bump changes the wording - a red test on an upgrade is the wanted outcome.
- A rule may legitimately be empty for a time (a package emptied by a move, awaiting its first class). The answer is
  to delete or to rewrite the rule, not to allow it to pass on nothing; the scan has no exemption list, on purpose.

## Open Questions

- **Should the scan of decision 3 also refuse `archunit.cycles.maxNumberToDetect` set below the default?** It is the
  other setting that makes a rule say less than it seems to. Recommendation: no, leave it to
  `let-application-package-cycles-only-decrease`, which names the cyclic dependencies instead of counting cycles and
  so stops depending on it.
