# Spec Delta

## Purpose

Define that the repository ignores the files that hold credentials or key material for a MORPHEUS server, and that the
build refuses such a file when it is tracked.

## ADDED Requirements

### Requirement: The repository SHALL ignore identity files and key material by default

`.gitignore` MUST carry a line for each of `remote-auth*.txt`, `*.p12`, `*.jks`, `*.pfx`, `*.pem` and `*.key`, and the
build MUST fail when one is removed.

#### Scenario: A keystore created in the working tree is not offered for commit

- **WHEN** a file `server.p12` or `remote-auth.txt` is created at the root of the checkout
- **THEN** `git status` does not list it as untracked

#### Scenario: A removed pattern fails the build

- **WHEN** the line `*.p12` is deleted from `.gitignore`
- **THEN** the hardening gate fails and names the missing pattern

### Requirement: No tracked file SHALL match a credential pattern

The build MUST fail when a file tracked by Git matches one of those patterns, and MUST say so by name.

#### Scenario: Nothing of the kind is tracked today

- **WHEN** the gate lists the tracked files
- **THEN** it reads a non-empty list and finds no match

#### Scenario: A forced addition is refused

- **WHEN** a file `fixtures/test.pem` is added with `git add -f`
- **THEN** the gate fails and names the file

#### Scenario: Outside a checkout the check does not pass by default

- **WHEN** the gate cannot list tracked files
- **THEN** it reports that it could not conclude, as an explicit skip with its reason, never as a pass
