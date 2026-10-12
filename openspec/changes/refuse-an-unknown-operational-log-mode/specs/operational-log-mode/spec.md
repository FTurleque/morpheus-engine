# Spec Delta

## Purpose

Define what the operational log mode can be, and what happens when the configured value is none of them.

## ADDED Requirements

### Requirement: The operational log mode SHALL be one of a closed list or a refusal

The product MUST accept, for `MORPHEUS_OPERATIONAL_LOGS`, only the values of a closed list, compared after trimming and
case folding, and MUST refuse to start on any other non-blank value. An absent or blank variable MUST mean that no
operational log is written.

#### Scenario: A mistyped mode is refused

- **WHEN** the process is started with `MORPHEUS_OPERATIONAL_LOGS=jsno`
- **THEN** it does not start, the message names the variable and lists the accepted values, and the exit code is the
  usage code

#### Scenario: A boolean is not a mode

- **WHEN** the variable is `true`, `1` or `yes`
- **THEN** the process refuses to start in the same way

#### Scenario: Case and surrounding spaces are not an error

- **WHEN** the variable is ` JSONL `
- **THEN** the JSON-lines mode is selected

#### Scenario: An absent variable is the default

- **WHEN** the variable is absent or blank
- **THEN** the process starts and writes no operational log

### Requirement: The refusal SHALL come from the launcher

The mode MUST be validated by the process entry point before any other product class is initialized, so that the refusal
is a message of the launcher and not an initialization error of whichever class used the runtime first.

#### Scenario: The refusal is not an initialization error

- **WHEN** an unknown mode is configured and any command is run
- **THEN** standard error carries the named refusal once, and no stack trace of a class initializer

### Requirement: The variable SHALL be documented for the user

The user documentation MUST name the variable, its accepted values and its default, and the gate that refuses an
undocumented environment variable MUST see a variable read through a constant.

#### Scenario: The gate sees it

- **WHEN** the architecture suite lists the environment variables the product reads
- **THEN** `MORPHEUS_OPERATIONAL_LOGS` is among them and its absence from the user documentation fails the build
