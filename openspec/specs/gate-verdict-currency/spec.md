# gate-verdict-currency Specification

## Purpose
Guarantee that a validator or a gate never renders PASS on a value that is older than its source of truth, or on a
measurement that is older than the code it claims to measure, and that a refusal names which of its causes it hit.

## Requirements

### Requirement: A validator SHALL read the presence ratchets from the living file and MUST NOT compare an observed count to a literal

`scripts/validate-m21.*` and `scripts/validate-d2.*` MUST obtain the minimum number of tests and the minimum number of
architecture tests from `testsMinimum` and `architectureTestsMinimum` in `config/m21-quality-ratchets.properties`, and
MUST refuse with the key's name when the file, a key, or an integer value above zero is missing. A script's comparison of
an observed Surefire count with an integer literal is forbidden.

#### Scenario: D2 refuses a repository that lost most of its tests

- **WHEN** `validate-d2.sh` and `validate-d2.ps1` run on a tree whose Surefire total is below `testsMinimum`
- **THEN** each exits non-zero with a message naming `testsMinimum`, the observed count and the required count, and
  prints no "D2 tests: PASS" line

#### Scenario: A raised ratchet is followed without editing the script

- **WHEN** `testsMinimum` is raised in `config/m21-quality-ratchets.properties` and nothing else is changed
- **THEN** `validate-d2.sh` and `validate-d2.ps1` require the new value on their next run

#### Scenario: A missing or invalid key is not replaced by a default

- **WHEN** `architectureTestsMinimum` is removed from the properties file, or `testsMinimum` is set to `0` or to a
  non-integer
- **THEN** each of the four validators refuses with a message naming that key and exits non-zero

#### Scenario: A properties file with Windows line endings is read correctly

- **WHEN** the properties file has CRLF line endings and a D2 or M21 script runs under a POSIX shell
- **THEN** the minimums are read as integers without a trailing carriage return and the comparison is made, not skipped
  or failed with an arithmetic error

#### Scenario: A literal minimum is refused by the architecture suite

- **WHEN** a D2 script compares `TESTS`, `ARCH_TESTS`, `$tests` or `$architectureTests` with an integer literal
- **THEN** the architecture suite fails and names the script and the line

#### Scenario: A literal updated to the correct value is still refused

- **WHEN** a D2 script is edited to read `TESTS < 3820`, the value `testsMinimum` has today
- **THEN** the architecture suite still fails, because the rule forbids the literal operand and not a particular
  number (the previous assertion, `script.contains("820")`, accepted this script because `"3820"` contains `"820"`)

#### Scenario: The version assertion survives the replacement

- **WHEN** a D2 script no longer contains the current product version
- **THEN** the architecture suite fails and names the script

#### Scenario: The source and the keys must be present

- **WHEN** a D2 script no longer mentions `config/m21-quality-ratchets.properties` (`config\m21-quality-ratchets.properties`
  in the PowerShell script), `testsMinimum` or `architectureTestsMinimum`
- **THEN** the architecture suite fails and names the script and the missing token

### Requirement: A coverage gate SHALL refuse a JaCoCo report older than the sources it measures

`CoverageQualityGateTest` MUST refuse the report of a module when any regular file under that module's `src/main/java` or
`src/test/java` has a modification time later than the report's. `AggregateCoverageGateTest` MUST apply the same rule to
`jacoco-aggregate/jacoco.xml` against the sources and tests of every module of its population and of
`morpheus-architecture-tests`, whose tests feed that report.

#### Scenario: A source edited after the report is refused

- **WHEN** a module's report exists, its `target/classes` exists, and a file under its `src/main/java` is given a
  modification time later than the report's
- **THEN** the per-module gate fails, naming the module, the cause `stale` and `./mvnw clean verify`

#### Scenario: A removed or edited test also makes the report stale

- **WHEN** a file under a module's `src/test/java` is given a modification time later than the report's
- **THEN** the per-module gate fails with the cause `stale`, because a report older than a test change may hide a drop
  in coverage

#### Scenario: A fresh report is accepted

- **WHEN** every file under the module's `src/main/java` and `src/test/java` is older than its report
- **THEN** the gate accepts the report and applies its ratios as before

#### Scenario: A stale aggregate report is refused

- **WHEN** a file under `src/main/java` or `src/test/java` of any module of the aggregate population, or under
  `morpheus-architecture-tests/src/test/java`, is newer than
  `morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml`
- **THEN** the aggregate gate fails naming the module and the cause `stale`, and fails before it writes
  `m21-aggregate-coverage-summary.txt`

#### Scenario: The tests set the times explicitly

- **WHEN** the staleness scenarios above run
- **THEN** they set modification times with `Files.setLastModifiedTime` and do not sleep or wait for the file system
  clock

### Requirement: A stale report SHALL be refused under its own named cause, measured against the sources

The refusal MUST name the cause `stale` apart from `never-built` and `built-without-report`, with the module, the two
times and the instruction `./mvnw clean verify`. The comparison MUST be strict and its reference MUST NOT be the
modification time of a `target/classes` directory.

#### Scenario: A nested edit is detected although the class directory did not move

- **WHEN** a file in a nested package of `src/main/java` is newer than the report while the `target/classes` directory
  itself is older than the report
- **THEN** the gate refuses the report, because the reference is the sources and not the class directory

#### Scenario: The three causes stay distinguishable

- **WHEN** a reactor holds one module never built, one built without a report and one with a stale report
- **THEN** the refusal lists the three modules each under its own cause, and the existing assertions on the first two
  causes still pass

### Requirement: A validator SHALL count the executions of declared test methods apart from generated executions

`scripts/validate-m21.*` and `scripts/validate-d2.*` MUST derive from the Surefire reports two counts — the executions
of declared test methods, and the executions generated by a test factory or by any source that yields more than one
execution per method — MUST print both, and MUST write both in `validation-summary.txt`; the two counts together MUST
equal the total Surefire reports.

#### Scenario: The summary carries both counts

- **WHEN** a validator passes
- **THEN** its summary states the written count and the generated count, and their sum is the Surefire total

#### Scenario: A parameterised test introduced later is counted as generated

- **WHEN** a test method that yields several executions is added to any module
- **THEN** every one of its executions is counted as generated, without a change to the validators

#### Scenario: Both platforms agree on the split

- **WHEN** `validate-m21.sh` and `validate-m21.ps1` read the same Surefire reports
- **THEN** they report the same two counts

### Requirement: Each of the two counts SHALL have its own minimum in the living ratchet file

The minimum of written executions and the minimum of generated executions MUST be two keys of
`config/m21-quality-ratchets.properties`, read under the rules that already apply to a presence minimum, and a
validator MUST refuse each count against its own key, naming that key, without comparing either count to the other
key or to their sum.

#### Scenario: Removing an option is refused under the generated minimum only

- **WHEN** an option is removed from a command and the factories generate fewer executions than the generated minimum
- **THEN** the validator exits non-zero with a message naming the generated key, the observed count and the required
  count, and reports the written count as passing

#### Scenario: Deleting written tests is refused under the written minimum

- **WHEN** declared test methods are removed and the written count falls below its minimum while the generated count is
  unchanged
- **THEN** the validator exits non-zero with a message naming `testsMinimum`

#### Scenario: Generated executions cannot make up for written ones

- **WHEN** the written count is below its minimum and an option added to a command raises the generated count by more
  than the shortfall
- **THEN** the validator still refuses

#### Scenario: A missing generated key is not replaced by a default

- **WHEN** the generated key is removed from the properties file or set to a value that is not an integer above zero
- **THEN** each validator refuses with a message naming that key

#### Scenario: The architecture minimum counts written executions

- **WHEN** a factory is added to the architecture test module
- **THEN** its executions are counted under the generated key and `architectureTestsMinimum` is compared with the
  written executions of that module

### Requirement: A test factory SHALL fail when it generates nothing

Every test factory of the repository MUST fail, by an assertion of its own, when the collection it builds its dynamic
tests from is empty.

#### Scenario: A factory whose source returns nothing

- **WHEN** the list of invocations a factory iterates over is empty
- **THEN** the factory fails and names what was empty, instead of reporting zero tests and no failure

#### Scenario: A new factory without the assertion is refused

- **WHEN** a test factory is added that does not assert its source is not empty
- **THEN** the architecture suite fails and names the method
