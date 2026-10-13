# Spec Delta

## Purpose

Define how the product builds a string-keyed payload map from alternating keys and values, what a malformed
construction answers, and what refuses a second builder.

## ADDED Requirements

### Requirement: A malformed payload map construction SHALL be refused by name

The builder MUST refuse, with a failure that names the defect, an odd number of arguments, a key that is not a string
and a key given more than once. The refusal of a key MUST identify it: its position and the type received for a key of
the wrong type, the key itself for a repeated one. A repeated key MUST NOT keep either value silently.

#### Scenario: A key of the wrong type

- **WHEN** a payload map is built with a number where a key is expected
- **THEN** the construction fails with a named refusal that gives the position and the type received
- **AND** the failure is not a class cast failure

#### Scenario: An odd number of arguments

- **WHEN** a payload map is built with a key that has no value
- **THEN** the construction fails with a named refusal, on every adapter

#### Scenario: A key given twice

- **WHEN** a payload map is built with the same key at two positions
- **THEN** the construction fails with a named refusal that gives the key

### Requirement: The caller SHALL state whether an absent value is admitted

A construction MUST either admit an absent value, which the canonical serialisation writes as JSON `null`, or refuse it
with a named refusal that gives the key. Which of the two applies MUST be visible at the call and MUST NOT depend on
the adapter the call is written in.

#### Scenario: An answer that carries an absent side

- **WHEN** the HTTP history answer is built for a difference that has no source
- **THEN** the answer carries `null` for the source, as it does today

#### Scenario: A value that must be present

- **WHEN** a construction that refuses absent values receives one
- **THEN** it fails with a named refusal that gives the key

### Requirement: A built payload map SHALL be unmodifiable and keep the order given

The map returned MUST refuse modification and MUST iterate its entries in the order they were given, on every run.

#### Scenario: Two runs of the product

- **WHEN** the same payload map is built in two separate runs and iterated
- **THEN** the entries come in the same order both times

### Requirement: The public answers SHALL NOT change

Replacing the private builders MUST leave every HTTP answer and every MCP tool result byte-identical for a well-formed
construction.

#### Scenario: The adapter suites

- **WHEN** the HTTP and MCP adapter suites run after the replacement
- **THEN** they pass without a change to an expected payload

### Requirement: A second payload map builder SHALL be refused

The architecture suite MUST fail when a type of the product other than the owner declares a method that builds a map
from an argument array, whatever the method is called, and MUST name the type and the method. The owner MUST depend on
no other MORPHEUS package.

#### Scenario: A pasted builder under a new name

- **WHEN** an adapter class declares a private method taking `Object...` and returning a map
- **THEN** the architecture suite fails and names the class and the method

#### Scenario: The owner grows a dependency

- **WHEN** the owner starts to use a type of another MORPHEUS package
- **THEN** the architecture suite fails
