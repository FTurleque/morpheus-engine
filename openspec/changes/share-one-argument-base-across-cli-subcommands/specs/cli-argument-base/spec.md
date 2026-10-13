# Spec Delta

## Purpose

Define what every CLI sub-command shares when it reads its arguments and reports a failure, and what refuses a
sub-command that carries its own copy.

## ADDED Requirements

### Requirement: Every CLI failure SHALL be reported through one error line

A failure of any CLI sub-command MUST be written to the error stream as one line that carries the exit code and the
failure's text, in one format, produced by one writer. The text of a failure MUST be its message, or the name of its
type when it has no message or a blank one, on every sub-command. The format and the exit codes MUST NOT change.

#### Scenario: Two sub-commands, the same kind of failure

- **WHEN** two different sub-commands are called with a required option missing
- **THEN** both write an error line of the same format with the usage exit code

#### Scenario: A failure without a message

- **WHEN** a sub-command fails with a failure that has no message
- **THEN** the error line carries the name of the failure's type, on every sub-command

#### Scenario: The format after the change

- **WHEN** the CLI suites run after the change
- **THEN** they pass without a change to an expected error line or exit code

### Requirement: The command token SHALL be read in one place

The token that selects a command MUST be the first argument that is neither a global flag nor the value of one, and
every sub-command MUST obtain it from the same reader.

#### Scenario: Global flags before the command

- **WHEN** a command is called with a layout option and its value before the command token
- **THEN** every sub-command reads the same token as the command

### Requirement: A sub-command SHALL declare its options to one shared parser

Every sub-command MUST parse its options through a shared parser to which it declares the value options and the flags
it accepts. The parser MUST refuse, by name, an option that was not declared, an option given twice, a value option
without a value and a blank value. A sub-command MUST NOT be able to obtain parsed options without having declared
what it accepts.

#### Scenario: An undeclared option

- **WHEN** any sub-command is called with an option it does not declare
- **THEN** the call is refused with the usage exit code and the option is named

#### Scenario: An option given twice

- **WHEN** any sub-command is called with the same value option twice
- **THEN** the call is refused and the option is named

#### Scenario: A flag and a value option on the same sub-command

- **WHEN** a sub-command that declares a flag and a value option is called with both
- **THEN** the flag reads as set and the value option reads as its value, without a parser of the sub-command's own

### Requirement: The same malformed input SHALL be refused the same way by every sub-command

For each kind of malformed option input, every sub-command MUST give the same refusal, unless the sub-command declares
a different treatment. A declared difference MUST be listed where the parser is tested.

#### Scenario: A value that looks like an option

- **WHEN** two sub-commands are given a value option followed by another option instead of a value
- **THEN** both refuse with the same wording, naming the option

#### Scenario: A declared difference

- **WHEN** a sub-command treats a malformed input differently from the others
- **THEN** that difference appears in the tested list of declared differences, with the sub-command's name

### Requirement: A private copy of the argument base SHALL be refused

The architecture suite MUST fail, naming the type and the member, when a CLI type other than the owner declares a
method that turns a failure into text, a raw argument vector into a command token, or a code and a message into an
error line; when the error line's format is spelled in more than one source file; or when a type that parses option
tokens is neither the shared parser nor a named exception.

#### Scenario: A new sub-command pastes the helpers

- **WHEN** a sub-command class is added with its own failure-to-text method under any name
- **THEN** the architecture suite fails and names the class and the method

#### Scenario: A second spelling of the format

- **WHEN** a class writes the error line's literal itself
- **THEN** the architecture suite fails and names the file

#### Scenario: A private option parser

- **WHEN** a sub-command declares a nested type that parses its option tokens
- **THEN** the architecture suite fails and names the type
