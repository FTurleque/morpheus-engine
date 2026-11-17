# Spec delta : dead-code-refusal

## ADDED Requirements

### Requirement: A public type of a product module SHALL have a production caller or a written reason
Every public top-level type declared under `src/main/java` of a product module MUST be named by another production source of the repository, be declared in a service registration file of a production module, or be listed with the reason it has none. Tests MUST NOT count as callers. Verification tooling modules and the published plugin SDK are outside this rule. The build MUST fail otherwise.

#### Scenario: A type named only by its tests fails the build
- **GIVEN** a public class of an application package that no other production source names, and that tests construct
- **WHEN** the architecture suite runs
- **THEN** the gate fails and names the file of the class, with the remedy: a production caller, its removal, or a listed reason
- **AND** no exception list is modified by the run

#### Scenario: A type loaded through a service registration is not reported
- **GIVEN** a public class of a production module named only by its own file and by a `META-INF/services` file of a production module
- **WHEN** the architecture suite runs
- **THEN** the gate reports nothing for it

#### Scenario: Verification tooling and the plugin SDK are not judged
- **GIVEN** a public class of the memory store, the synthetic provider, the provider testkit, the reference plugin or the plugin SDK that no production source names
- **WHEN** the architecture suite runs
- **THEN** the gate reports nothing for it

#### Scenario: An excuse without a reason, or for a type that has a caller, fails
- **GIVEN** the exception list names a type with an empty reason, or a type that a production source now names, or a type that no longer exists
- **WHEN** the architecture suite runs
- **THEN** the gate fails and names the entry

#### Scenario: No type of the audit remains without a production caller or a reason
- **GIVEN** the ten classes of `morpheus-application` that the audit of 17/11/2026 found without a production caller
- **WHEN** the gate runs after this change
- **THEN** each of them is either named by a production source, or removed with the types that only it named, and the gate passes
