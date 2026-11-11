# Proposal

## Why

The last sentence of AUD-TRV-03 (sprint 12) is a finding of its own: `archRule.failOnEmptyShould`, which `.claude/rules/architecture.md`
treats as load-bearing, "comes from the library's default: an `archunit.properties` added elsewhere or a change of
the upstream default would disable the guard in silence". Re-read on `develop` at `8b26d3d7` on 2026-10-13, it
**holds**:

- The archived ADR-0103, lines 156-159: "`archRule.failOnEmptyShould` is `true` by default and nothing in this repository
  disables it. [...] Do not disable it." `.claude/rules/architecture.md` and `.claude/skills/enforcement-choice`
  repeat the instruction.
- No test asserts it. `grep -rn 'failOnEmptyShould\|allowEmptyShould\|ArchConfiguration'` over the gate module finds
  four Javadoc mentions and no code. No `archunit.properties` exists in the repository, and the Surefire
  configuration of `morpheus-architecture-tests` sets no `archunit.*` system property.
- The guard can be switched off in three ways, none of which any gate would notice: a property file on the test
  classpath, a system property on the Surefire command line, and `allowEmptyShould(true)` on a single rule.
- The dependence is growing. Changes specified this month lean on it explicitly
  (`decide-what-technology-the-application-core-may-touch`, `give-the-shared-mcp-transport-its-own-package`,
  `build-payload-maps-through-one-checked-builder`), and `express-adapter-internal-roles-as-packages` replaces
  selections by name with selections by package - the form that goes vacuous when a package is renamed.
  `let-application-package-cycles-only-decrease` notes that a cycle measure needs an ArchUnit setting "which no
  committed configuration sets": the first committed `archunit.properties` may well arrive with it.

The audit withdrew, on re-reading, the claim that the absence of the file is a gap, and it was right to. What remains
is that an invariant the whole suite rests on is an instruction to people.

## What Changes

- The architecture suite proves, by evaluating one, that a rule which retains no class fails.
- It refuses the three ways of switching the guard off, wherever they are written.
- An ArchUnit configuration file stays allowed: only the settings that make a rule pass on nothing are refused.

## Capabilities

### New Capabilities

- `architecture-rule-vacuity-guard`: a rule of the architecture suite that selects nothing fails, and that is tested.

### Modified Capabilities

(none)

## Impact

- `morpheus-architecture-tests`: one new test class. `architectureTestsMinimum` rises with it (`live-numbers`).
- No ADR: the guard is obligation 1 of `.claude/rules/architecture.md` (section 4 of the archived ADR-0103); the rule page says it is proved, no longer assumed.
- `.claude/rules/architecture.md` and `.claude/skills/enforcement-choice/SKILL.md`: the sentence "do not disable it"
  names the test that fails if someone does.
- No product code, no public surface, no validator, no workflow.
- Independent of every other change, sprint 11 included. Best taken before
  `express-adapter-internal-roles-as-packages` and before the first committed ArchUnit configuration.
