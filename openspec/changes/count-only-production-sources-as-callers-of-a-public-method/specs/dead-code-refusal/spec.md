# Spec delta : dead-code-refusal

## MODIFIED Requirements

### Requirement: A public method of a product module SHALL be referenced or excused by name

Every public method declared in a product module MUST be called by a production class of the repository, override a
supertype method, or be listed with the reason it is public without a production caller; a call written in a test
MUST NOT count as a reference; the published plugin SDK is outside this rule; and the build MUST fail otherwise.

#### Scenario: The sixteen methods are gone

- **WHEN** the gate runs after this change
- **THEN** none of the sixteen methods of the audit exists, and the gate passes

#### Scenario: A new uncalled public method fails the build

- **WHEN** a public method is added to an application service and nothing calls it
- **THEN** the gate fails and names the class and the method

#### Scenario: A public method called only by tests fails the build

- **GIVEN** a public method of an application service that a test calls and no production class calls
- **WHEN** the gate runs
- **THEN** the gate fails and names the class and the method
- **AND** the exception list is not modified by the run

#### Scenario: A static factory of a record called only by tests fails the build

- **GIVEN** a public static factory of a record of the domain that only tests call
- **WHEN** the gate runs
- **THEN** the gate fails and names the record and the factory, so that the tests use the canonical constructor

#### Scenario: The plugin SDK is not judged by MORPHEUS's own calls

- **WHEN** a public method of `com.morpheus.sdk` has no caller in the repository
- **THEN** the gate reports nothing for it

#### Scenario: An excuse without a reason, or for a method that is called, fails

- **WHEN** the list names a method with an empty reason, or a method that now has a caller or no longer exists
- **THEN** the gate fails and names the entry

#### Scenario: The misleading peer environment is gone

- **WHEN** `MinosIntegrationSettings` is read after this change
- **THEN** it has no `processEnvironment` method, as NEXUS never had
