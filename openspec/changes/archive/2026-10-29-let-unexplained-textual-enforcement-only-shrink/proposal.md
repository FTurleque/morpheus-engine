# Proposal

## Why

AUD-TRV-03 (sprint 12) reports that the enforcement of the repository rests mostly on textual assertions, about one
bytecode rule for 35 of them, and asks that the textual share be capped. Re-read on `develop` at `8b26d3d7` on
2026-10-13, the finding **holds as a description and its remedy does not survive the criterion of the archived ADR-0103, held today by `.claude/rules/architecture.md`**.

The description, re-measured on `morpheus-architecture-tests/src/test/java` (each figure with the command that gives
it, because three countings of the same thing already disagree):

| Quantity | Command | 2026-10-13 | Audit |
|---|---|---|---|
| test source files / lines | `find -name '*.java'`, `wc -l` | 161 / 33 922 | 147 / 31 256 |
| `@Test` annotations | `grep -rcE '^\s*@Test\b'` | 663 | 605 |
| files importing ArchUnit / their `@Test` | `grep -rl 'com.tngtech.archunit'` | 17 / 105 | 15 / 94 |
| rules evaluated | `grep -rhoE '\.check\('` | 44 | 40 or 49 |
| `assertTrue/False(<receiver>.contains(` | receiver `[a-zA-Z0-9_.()]+` | 1 212 + 511 | 1 170 + 498 |
| the same, the archived ADR-0103's regex | receiver `[a-zA-Z0-9_]+` | 1 645 | (ADR: 1 525 on 10/09) |
| mechanical family of the archived ADR-0103 | `assertFalse\([a-zA-Z0-9_]+\.contains\("[A-Z][A-Za-z0-9_]*"\)\)` | 196 in 31 files | (ADR: 198, then 193) |
| `Files.readString` | `grep -rhoE` | 392 | 356 |

Both sides grew in four days; the ratio is of the same order. Three points of the finding do not hold as stated:

- **A cap on the share contradicts the decision the repository took.** `.claude/rules/architecture.md` says that for a `.yml`, `.ps1`,
  `.md`, `.sh`, `.xml` or `.tsv` the text is the only handle, that the two forms coexist as soon as the banned type
  declares a compile-time constant, and that a textual assertion is not a degraded form to migrate by reflex. A ratio is
  lowered by deleting a documentation check or by adding a rule that sees nothing new.
- **"Move the documentation-coherence assertions out of the gate module" lowers a ratchet that never goes down.**
  `architectureTestsMinimum` is the number of Surefire executions of `morpheus-architecture-tests`
  (`scripts/validate-m21.sh`, line 59). And the classes are mixed: by a scan of path literals, 69 test files read
  `src/main/java`, 84 read `docs/`, `scripts/`, `.github/`, `contracts/`, `config/` or `distribution/`, and 65 do
  both. About nineteen classes read artefacts only.
- **"Require the sentence of intent in the pull request description" is not executable**, and `.claude/rules/architecture.md` already asks
  for the sentence. Of the mechanical family, 196 assertions carry no message and 9 carry one.

What does hold, and has no owner: the mechanical family is the one place where text is a **choice** - each member
says "this class must not depend on that one", the statement of a rule - and nothing prevents it from growing.
The archived ADR-0103 (third group, held today by `.claude/rules/architecture.md`) decided that 110 of them do not migrate until a fact reopens the question; it did not decide
that new ones may be added without saying which of the two propositions they mean. And the figures above exist only
as prose in an ADR and in an audit, counted three ways.

## What Changes

- The mechanical family - a textual ban of a type name with no stated intent - becomes a measured population that
  only shrinks. A new textual ban of a type states its intent where the failure will show it.
- The gate module publishes what its own enforcement is made of, by one stated method, as a proof file. No threshold
  bears on it.
- A test class that reads no compiled class and no production source is marked as a coherence check, inside the gate
  module, so that the build can select and name it.
- Nothing is migrated and nothing is moved out of the module by this change.

## Capabilities

### New Capabilities

- `enforcement-mechanism-accounting`: what the gate module measures of its own mechanisms, which part may only
  shrink, and how a check that reads only documents is told apart.

### Modified Capabilities

(none)

## Impact

- `morpheus-architecture-tests`: one new gate class with its fixture tests; a marker on the coherence classes; no
  existing assertion changes. `architectureTestsMinimum` rises with the new tests (`live-numbers`,
  `coverage-ratchet`).
- `.claude/rules/architecture.md` (the criterion of the archived ADR-0103 is a test convention, not an ADR): the family is defined by the gate and no longer by a command in an ADR; its
  ceiling only goes down; the audit's proposed cap on the share is recorded as refused, with the reason.
- `.claude/rules/architecture.md` (section "Regle ArchUnit ou assertion textuelle"), `.claude/skills/enforcement-choice/SKILL.md`,
  `docs/developer/BUILD_AND_TEST.md` if it lists the proof files of the build. No figure is copied into any of them.
- No product code, no public surface, no validator.
- **Does not wait for sprint 11 nor for `express-adapter-internal-roles-as-packages`.** It is better taken first:
  both will add or remove textual assertions, and the ceiling is what makes that visible.
