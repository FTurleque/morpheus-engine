## MODIFIED Requirements

### Requirement: A public method of a product module SHALL be referenced or excused by name

Every public method declared in a product module MUST be called by a class of the repository, override a supertype
method, or be listed with the reason it is public without a caller; only the plugin-facing SPI of the published
provider SDK is outside this rule, and the plugin host is judged like any product module; and the build MUST fail
otherwise.

#### Scenario: The sixteen methods are gone

- **WHEN** the gate runs after this change
- **THEN** none of the sixteen methods of the audit exists, and the gate passes

#### Scenario: A new uncalled public method fails the build

- **WHEN** a public method is added to an application service and nothing calls it
- **THEN** the gate fails and names the class and the method

#### Scenario: The plugin SDK is not judged by MORPHEUS's own calls

- **WHEN** a public method of the plugin-facing SPI of `com.morpheus.sdk` has no caller in the repository
- **THEN** the gate reports nothing for it

#### Scenario: An uncalled public method of the plugin host fails the build

- **GIVEN** the plugin host separated from the SPI
- **WHEN** a public method of a discovery, probe, service or projection type of the host has no caller in the
  repository and no listed reason
- **THEN** the gate fails and names the class and the method

#### Scenario: An excuse without a reason, or for a method that is called, fails

- **WHEN** the list names a method with an empty reason, or a method that now has a caller or no longer exists
- **THEN** the gate fails and names the entry

#### Scenario: The misleading peer environment is gone

- **WHEN** `MinosIntegrationSettings` is read after this change
- **THEN** it has no `processEnvironment` method, as NEXUS never had
