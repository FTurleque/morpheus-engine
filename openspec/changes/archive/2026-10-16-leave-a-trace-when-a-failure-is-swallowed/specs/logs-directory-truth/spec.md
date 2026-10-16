# Spec Delta

## Purpose

Define the relation between the logs directory the product announces and what it writes there.

## ADDED Requirements

### Requirement: An announced logs directory SHALL have a writer

If the product resolves, prints or documents a logs directory, a mode of the operational log MUST write there; otherwise
the directory MUST NOT be resolved, printed or documented.

#### Scenario: The file mode writes where the layout says

- **WHEN** the operational log mode is the file mode
- **THEN** events are appended as JSON-lines to a file under the directory the `paths` command prints

#### Scenario: The file is bounded

- **WHEN** the file reaches its size bound
- **THEN** it is rotated, at most a fixed number of generations is kept, and the oldest is removed

#### Scenario: The file is private

- **WHEN** the file is created
- **THEN** it has the permissions the product gives a private file it writes

#### Scenario: A directory that cannot be written is refused by name

- **WHEN** the file mode is selected and the logs directory cannot be created or written
- **THEN** the process refuses to start with a named refusal, rather than running without the logs it was asked for
