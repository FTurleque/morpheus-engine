# Spec Delta

## Purpose

Define what accompanies a suppressed warning in the product's sources, and what a boundary does instead of an unchecked
cast.

## ADDED Requirements

### Requirement: A suppressed warning in the product's sources SHALL state its reason

Every `@SuppressWarnings` under `src/main/java` MUST carry, on its own line or the line above, a comment that says why
the warning is wrong or accepted for that code.

#### Scenario: A bare suppression fails the build

- **WHEN** a `@SuppressWarnings` is added under `src/main/java` without a comment
- **THEN** the architecture suite fails and names the file and the line

#### Scenario: The rule key is not a reason

- **WHEN** the only comment repeats the key of the suppressed rule
- **THEN** the suite fails in the same way

### Requirement: A boundary SHALL validate a structured value instead of casting it

A value received at the MCP or HTTP boundary and used as a map MUST be checked to be a map with string keys, and a value
that is not MUST be refused with a named refusal that identifies the field.

#### Scenario: A list where an object is expected

- **WHEN** an MCP tool receives a list for an argument that must be an object
- **THEN** the call is refused with a named refusal naming the argument, not a class cast failure
