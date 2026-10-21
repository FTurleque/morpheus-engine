# validator-self-test Specification

## Purpose
Guarantee that a script which gives a verdict on the repository is itself exercised on both platforms, that a refusal
it claims is provoked by a test, and that every script of `scripts/` is launched by something and tested or excused by
name.

## Requirements

### Requirement: The refusals of a validator SHALL be provoked by a test that does not build the product

For every `scripts/validate-<target>.sh` and `scripts/validate-<target>.ps1` that is not excused by name, a test MUST
run the real script against a fabricated checkout and provoke each refusal the validator claims; each MUST end with a
non-zero exit code, a message naming its cause, no PASS line for the refused check and no passing
`validation-summary.txt`.

#### Scenario: A tracked change in the tree is refused

- **WHEN** a validator that requires a clean tree runs on a fabricated checkout with a modified tracked file
- **THEN** it exits non-zero before it launches the build and names the requirement

#### Scenario: A missing supply-chain file is refused

- **WHEN** the build stub succeeds and a file of the supply chain the validator checks is absent
- **THEN** the validator exits non-zero and names the missing file

#### Scenario: A failing Surefire report is refused

- **WHEN** a validator runs on a fabricated checkout whose Surefire reports hold one failure
- **THEN** it exits non-zero, names the failures, prints no "Tests: PASS" line and writes no passing summary

#### Scenario: A count below the living minimum is refused with the key

- **WHEN** `validate-m21` or `validate-d2` runs on a fabricated checkout whose Surefire total is one below
  `testsMinimum` as the fabricated ratchet file states it
- **THEN** it exits non-zero and its message carries the observed count and the required count

#### Scenario: Coverage evidence of the other scale is refused

- **WHEN** the evidence file a validator reads begins with `coverageScope=per-module`
- **THEN** the validator exits non-zero before it reads a ratio

#### Scenario: A complete fabricated checkout passes

- **WHEN** every file the validator reads is present and consistent and the build stub succeeds
- **THEN** the validator exits zero and its summary names the commit it validated

#### Scenario: The build the validator asked for is the one expected

- **WHEN** a validator runs against the build stub
- **THEN** the stub's record shows the Maven command the validator is documented to run

#### Scenario: A validator that stops refusing is noticed

- **WHEN** the comparison of the Surefire total with the minimum is removed from a validator
- **THEN** the test of that validator fails

### Requirement: Both implementations of a validator SHALL meet the same cases

The cases of the previous requirement MUST be run against the `.sh` and against the `.ps1` of each target, and a
library of `scripts/lib/` that exists in two languages MUST be tested in both on one shared list of cases.

#### Scenario: The PowerShell ratchet reader refuses what the shell reader refuses

- **WHEN** the ratchet file lacks a key, holds zero, holds a non-integer, or has Windows line endings
- **THEN** `Read-QualityRatchets.ps1` and `read-quality-ratchet.sh` give the same verdict on each case, and the refusals
  name the key

#### Scenario: A case added for one implementation runs against the other

- **WHEN** a case is added to the shared list
- **THEN** it is executed against both implementations without further change

### Requirement: The dispatcher SHALL be tested for what it forwards and what it refuses

A test MUST run `scripts/validate.ps1` and assert that it lists exactly the targets that exist, refuses a missing or
malformed target by naming the available ones, forwards the remaining arguments to the validator unchanged, and ends
non-zero when the validator it launched fails.

#### Scenario: An unknown target is refused with the list

- **WHEN** the dispatcher is run with a target that has no validator
- **THEN** it ends non-zero and its message names the available targets

#### Scenario: A failing validator fails the dispatcher

- **WHEN** the validator the dispatcher launches exits non-zero
- **THEN** the dispatcher exits non-zero

### Requirement: The script tests SHALL run on Linux and on Windows in CI and MUST NOT pass by skipping

The continuous-integration workflow MUST run the tests of `scripts/tests/` on its Linux leg and on its Windows leg, and
in a CI run a case whose interpreter or tool is missing MUST fail instead of being skipped.

#### Scenario: The Windows leg runs the PowerShell cases

- **WHEN** the Windows leg of the workflow runs
- **THEN** the cases that launch a `.ps1` are executed with the interpreter the workflow uses for the validators

#### Scenario: A missing tool is a failure in CI

- **WHEN** the script tests run in CI on a runner where `bash`, `jq` or `powershell` is not found
- **THEN** the run fails and names the missing tool

#### Scenario: A developer without the tool is not blocked

- **WHEN** the same tests run outside CI on a machine without the tool
- **THEN** the cases that need it are reported skipped

#### Scenario: A leg that stops running the tests is refused

- **WHEN** the step that runs the script tests is removed from one leg of the workflow
- **THEN** the architecture suite fails and names the leg

### Requirement: Every script of `scripts/` SHALL be launched by something, and tested or excused by name

The inventory of the script tests MUST find, for every script under `scripts/` outside `scripts/tests/`, a workflow
step or a launched script that launches it, or an entry naming it as launched by hand with a reason; and a test
that runs it, or an entry excusing it with a reason. A documentation page MUST NOT count as a launch. A script
with no launcher, a script neither tested nor excused, and a stale entry MUST each fail the build.

#### Scenario: A new script without a test is refused

- **WHEN** a script is added under `scripts/` and no test runs it and no entry excuses it
- **THEN** the script tests fail and name the script

#### Scenario: A script that nothing launches is refused

- **WHEN** the last workflow step or launched script that launched a script stops naming it
- **THEN** the script tests fail and name the script

#### Scenario: A page is not a launch

- **WHEN** the only remaining mention of a script is in a current documentation page, and no entry names it as
  launched by hand
- **THEN** the script tests still fail and name the script

#### Scenario: A mention in a comment is not a launch

- **WHEN** the only remaining mention of a script is in a comment
- **THEN** the script tests still fail

#### Scenario: An entry point launched by hand is named and documented

- **WHEN** an entry names a script as launched by hand while a workflow launches it, without a reason, or without a
  current page that names it
- **THEN** the script tests fail and name the entry

#### Scenario: Reading a script is not testing it

- **WHEN** the only test that names a script reads its text without running it
- **THEN** the script counts as untested

#### Scenario: A declaration the test code does not back is refused

- **WHEN** a test file declares a validator that none of its generated classes hands to the harness, a library that no
  validator it runs names, or any other script that appears in no string of its code outside the declaration
- **THEN** the script tests fail and name the test file and the script

#### Scenario: A stale excuse is refused

- **WHEN** an excused script gains a test that runs it, or is removed
- **THEN** the script tests fail and name the excuse to delete
