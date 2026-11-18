# Spec delta : gate-verdict-currency

## ADDED Requirements

### Requirement: The split between written and generated executions SHALL be implemented once per scripting language

Every validator that counts Surefire executions MUST obtain its written, generated, failed, errored and skipped counts
from one library of `scripts/lib/` per scripting language. The rule that recognises a generated execution MUST NOT be
written in any other file of `scripts/`, and the two libraries MUST give the same counts on the same reports.

#### Scenario: Every validator delegates its count

- **GIVEN** the validators of `scripts/` that compare a count with `testsMinimum`, `generatedTestsMinimum` or
  `architectureTestsMinimum`
- **WHEN** the architecture suite reads them
- **THEN** none of them carries the expression that recognises a generated execution, and each calls the library of
  its language
- **AND** the build passes

#### Scenario: A copy of the rule in a validator is refused

- **GIVEN** a validator edited to recognise `method()[N]` with its own expression
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the script and the line
- **AND** no other script is named

#### Scenario: Both libraries agree on one list of cases

- **GIVEN** a fabricated reactor whose reports hold declared, generated, failed, errored and skipped executions
- **WHEN** the PowerShell library and the shell library count it
- **THEN** both give the same five counts, and a case added to the shared list runs against both without further change

#### Scenario: The summary carries the same counts as before

- **GIVEN** the Surefire reports of a full build
- **WHEN** `validate-m21` and `validate-d2` run on either platform after the change
- **THEN** their written and generated counts, and their verdicts, equal those the copies gave on the same reports
