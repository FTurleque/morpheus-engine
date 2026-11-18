# Spec delta : skipped-test-justification

## Purpose

Guarantee that a test of the reactor is skipped only for a reason its build can name, that no condition switches a
test off on every CI platform, and that the skips of a build are published. Durable page:
`docs/developer/specifications/skipped-test-justification.md`.

## ADDED Requirements

### Requirement: A test of the reactor SHALL be skipped only by an operating-system condition

After the reactor has written its Surefire reports, the build MUST fail when a test of a declared module was skipped
for any reason other than an operating-system condition, naming the test and the reason it recorded. An aborted
assumption MUST count as an unexplained skip.

#### Scenario: A failed assumption is refused on the platform where it happens

- **GIVEN** a test that calls an assumption which does not hold on the running machine
- **WHEN** the reactor is verified
- **THEN** the build fails and names the test with the reason `Assumption failed`

#### Scenario: An operating-system condition is accepted

- **GIVEN** a test enabled on Windows only, run on Linux
- **WHEN** the reactor is verified
- **THEN** the skip is accepted, and the test still counts as an execution of the report

### Requirement: An operating-system condition MUST leave its test enabled on Linux or on Windows

The architecture suite MUST refuse a test method whose combined operating-system conditions, those of its class and
its own, disable it on Linux and on Windows alike, naming the class, the method and the conditions. A condition that
leaves the test enabled on one of the two MUST be accepted.

#### Scenario: A test enabled on macOS only is refused

- **GIVEN** a test method annotated `@EnabledOnOs(OS.MAC)`
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the class, the method and `@EnabledOnOs(OS.MAC)`

#### Scenario: A test disabled on both CI platforms is refused

- **GIVEN** a test method annotated `@DisabledOnOs({OS.LINUX, OS.WINDOWS})`
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the method

#### Scenario: Two conditions that exclude each other are refused

- **GIVEN** a test class annotated `@EnabledOnOs(OS.WINDOWS)` holding a method annotated `@EnabledOnOs(OS.LINUX)`
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the method and both conditions

#### Scenario: A test for one CI platform is accepted

- **GIVEN** the operating-system conditions of the repository's test sources, each enabling Linux, Windows, or both
- **WHEN** the architecture suite runs
- **THEN** none of them is refused

### Requirement: A presence validator SHALL publish the number of skipped executions

`validate-m21` and `validate-d2`, on both platforms, MUST state the number of skipped executions in their verdict line
and in `validation-summary.txt`, next to the written and generated counts.

#### Scenario: The summary names the skips

- **GIVEN** Surefire reports holding skipped executions
- **WHEN** `validate-m21.sh` or `validate-m21.ps1` passes
- **THEN** its summary carries the skipped count, and the two platforms report the count of their own run

#### Scenario: A run with no skip says so

- **GIVEN** Surefire reports without any skipped execution
- **WHEN** a presence validator passes
- **THEN** its summary states a skipped count of zero rather than omitting it
