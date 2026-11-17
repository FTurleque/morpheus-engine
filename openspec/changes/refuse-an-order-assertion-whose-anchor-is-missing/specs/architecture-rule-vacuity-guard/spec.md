# Spec delta : architecture-rule-vacuity-guard

## ADDED Requirements

### Requirement: A textual order assertion of the suite SHALL fail when one of its anchors is absent

An assertion of the architecture suite that requires one text to come before another in a file MUST fail, naming the
missing anchor and the file, when either text is absent, whatever the order of what remains. The suite MUST refuse an
assertion whose condition compares the position of a text found by `indexOf` outside the one helper that applies this
rule, naming the class and the line.

#### Scenario: The role check of the remote server is renamed

- **GIVEN** a `MorpheusRemoteHttpServer` whose role check is no longer written `identity.role().allows(route.role())`
- **WHEN** the architecture suite runs
- **THEN** the remote access journal contract fails and names that anchor as missing
- **AND** it does not pass on the ground that `-1` precedes the accepted record

#### Scenario: The build step disappears from the Sonar job

- **GIVEN** a `ci.yml` whose `sonar` job no longer contains `clean verify`
- **WHEN** the architecture suite runs
- **THEN** the workflow contract fails and names `clean verify` as missing from the job

#### Scenario: A step of the security workflow is renamed

- **GIVEN** a `security.yml` whose OWASP scan step over build and test dependencies is renamed, or whose trusted update
  step no longer calls `org.owasp:dependency-check-maven`
- **WHEN** the architecture suite runs
- **THEN** the corresponding contract fails and names the missing step or call

#### Scenario: Anchors present in the right order pass

- **GIVEN** the files of the repository as they stand
- **WHEN** the architecture suite runs
- **THEN** every order assertion passes

#### Scenario: A new bare comparison is refused

- **GIVEN** a test of the suite edited to assert `text.indexOf("a") < text.indexOf("b")` without the helper
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the class and the line
