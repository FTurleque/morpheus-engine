# Proposal

## Why

AUD-QUA-06 (sprint 9) reports that each CLI sub-command carries its own copy of the same argument base: the failure
message, the command token, the error line and an `Options` class. Re-read on `develop` at `8b26d3d7` on 2026-10-13,
the finding **holds partly: a shared base exists and is half adopted**.

Measured in the 28 source files of `morpheus-cli`:

| Member | Audit | Today | Bodies |
|---|---|---|---|
| `safeMessage(Throwable)` | 10 | 11 private copies | three writings of one behaviour (nine identical) |
| `command(String[])` | 12 | 12 private, of which **3 only delegate** to `GlobalArgs.command` | nine real copies in two writings |
| `error(PrintStream, CliExitCode, String)` | 6 | 6 | identical |
| the literal `MORPHEUS error [` | 11 classes | 21 sites in 11 files | - |
| private nested `Options` | 6 | 6 | six different bodies |

What the audit did not credit: `GlobalArgs` (global flags and the command token), `SimpleOptions` (key and value
options, used by the policy, portfolio and query commands), `OptionValue` (the one definition of a blank value) and
`OptionOccurrence` (an option given twice) are already shared types of the package, and two governance tests hold
them - `CliOptionParsingRefusesUnknownOptionsTest` and `CliOptionValueHasOneBlankDefinitionTest`. The module is
mid-migration: three commands call the shared base, six still carry a private parser, and nine re-implement
`GlobalArgs.command` next to the class that owns it.

The six private `Options` are not six copies of one thing. Read side by side, they differ by what they accept - value
options only, value options and boolean flags, a confirmation flag - and each declares its own allowed set in a
`switch`. Whether they also differ in *behaviour* on the same malformed input (a duplicate, a value that starts with
`--`, a positional token) was not established by this reading and is the first task.

The cost is the one the audit states: the error line of the CLI is a public format, written at 21 places.

## What Changes

- The error line has one writer; the text a failure contributes to it is derived in one place; the command token is
  read in one place.
- Every sub-command parses its options through a shared parser to which it declares its value options and its flags;
  no sub-command carries a parser of its own.
- The same malformed input is refused the same way by every sub-command, and a table says so.
- Tests refuse a private copy of each of the four.

## Capabilities

### New Capabilities

- `cli-argument-base`: what every CLI sub-command shares when it reads its arguments and reports a failure.

### Modified Capabilities

(none)

## Impact

- **Production code**, one module, one package: `MorpheusCli`, the fourteen `Morpheus*Cli` classes beside it and the
  shared types beside `GlobalArgs`. No layer rule is involved - nothing leaves the module.
- **Public behaviour**: the error line and the exit codes are unchanged, byte for byte. If the table of task 1.2 shows
  two sub-commands refusing the same malformed input differently, aligning them is a visible change: it is listed, one
  line each, in the release notes under `docs/user/release-notes/`. `contracts/public-surfaces.tsv` is not edited: no
  command is added, removed or renamed.
- One ArchUnit members rule, one textual rule, and an extension of the parser discovery that
  `CliOptionParsingRefusesUnknownOptionsTest` already performs; `architectureTestsMinimum` rises.
- No ADR. Coverage: duplicated lines that the adapter suites cover leave the module, and the changed-line gate reads the
  replacement; both scales are read before and after, no ratchet is lowered.
- **Before sprint 12.** AUD-ARC-09 finishes the extraction of one class per capability out of `MorpheusCli`, and
  `express-adapter-internal-roles-as-packages` gives the adapters sub-packages: both are simpler once a sub-command has
  nothing private to carry, and both will need the shared base visible across sub-packages. The rules of this change
  are written on `com.morpheus.cli..`, not on file paths, so they survive the move.
- Independent of the other changes of sprint 9.
