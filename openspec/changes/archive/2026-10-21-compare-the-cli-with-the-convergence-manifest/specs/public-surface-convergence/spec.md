# Spec Delta

## Purpose

Extend the convergence guarantee to the CLI transport: the commands the CLI serves are declared once, served as
declared, named by the manifest in both directions, and documented by the embedded help.

## ADDED Requirements

### Requirement: The CLI SHALL serve exactly the command paths it declares

The CLI MUST hold one declaration of its command paths, MUST refuse as an unknown command any invocation whose path is
not declared, and every declared path MUST be routed to a behaviour.

#### Scenario: A declared path is routed

- **WHEN** the CLI is invoked with a declared command path and no option against an empty store
- **THEN** its answer is not the refusal of an unknown command or of an unknown action

#### Scenario: An undeclared path is refused before any sub-command is asked

- **WHEN** the CLI is invoked with a path that the declaration does not hold
- **THEN** it exits with the usage exit code and a message that names the unknown command

#### Scenario: A branch added without a declaration is not reachable

- **WHEN** a sub-command gains a new action in its routing and the declaration is not extended
- **THEN** invoking that action is refused as unknown, and the gate that runs the declared paths does not list it

#### Scenario: A declared path that nothing routes is refused by the build

- **WHEN** a path is added to the declaration and no sub-command routes it
- **THEN** the build fails and names the path

### Requirement: The `cli` column of the manifest and the served commands SHALL agree in both directions

Every cell of the `cli` column that is not a sentinel MUST name, by the words that precede its first option, one
declared command path; every declared command path MUST be named by exactly one row of the manifest or by one
exclusion that states its reason; and the build MUST fail otherwise.

#### Scenario: A manifest cell that names no served command

- **WHEN** a row names a CLI command that the declaration does not hold
- **THEN** the build fails and names the manifest line and the command

#### Scenario: A served command without a row

- **WHEN** a command path is declared and no row and no exclusion names it
- **THEN** the build fails and names the command path

#### Scenario: A sentinel does not hide a served command

- **WHEN** the row of a capability carries `EXPLICITLY_NOT_EXPOSED` in the `cli` column while the CLI serves the command
  of that capability
- **THEN** the build fails because that command path has no row, and the failure names it

#### Scenario: The portfolio reads are declared as served

- **WHEN** the gate runs on the corrected manifest
- **THEN** the rows `portfolio.list`, `portfolio.members` and `portfolio.conflicts` name `portfolio list`,
  `portfolio members` and `portfolio conflicts`

#### Scenario: A command named twice is refused

- **WHEN** two rows name the same command path
- **THEN** the build fails and names both lines

#### Scenario: A cell that carries an option is compared on its path

- **WHEN** a cell reads `server restore --confirm`
- **THEN** it is compared as the command path `server restore`

#### Scenario: An exclusion that excuses nothing is refused

- **WHEN** an exclusion names a path that the declaration does not hold, or a path that a row names
- **THEN** the build fails and names the exclusion

#### Scenario: An empty read is a failure

- **WHEN** the gate reads no row from the manifest or no path from the declaration
- **THEN** it fails instead of passing

### Requirement: The embedded help SHALL document every declared command path

Every declared command path MUST appear in a usage line of the embedded help, unless it is one of the forms the help
itself is reached by.

#### Scenario: A served action missing from the help

- **WHEN** a declared path such as `portfolio members` appears in no usage line of the help
- **THEN** the build fails and names the path

#### Scenario: The help and the parser still agree the other way

- **WHEN** the help documents an invocation
- **THEN** the parser accepts it, as before this change
