## ADDED Requirements

### Requirement: A walk of the whole repository SHALL go through one owner that skips copies of the repository

A test that reads files across the whole repository MUST obtain them from one owner. That owner MUST leave out
directories of generated output, the workspace copies under `.claude/worktrees`, and any directory below the root that
carries its own `.git`, file or directory; it MUST NOT follow symbolic links, and it MUST still read the rest of
`.claude`. A walk of the repository root anywhere else in the test sources MUST be refused.

#### Scenario: A workspace copy sits under the root

- **WHEN** a full copy of the repository lies under `.claude/worktrees` and a gate counts the reactor POMs
- **THEN** the gate counts the POMs of the repository only

#### Scenario: A nested checkout elsewhere in the tree

- **WHEN** a directory below the root carries its own `.git` file
- **THEN** none of its files is returned by the walk

#### Scenario: A test walks the root itself

- **WHEN** a test source walks the repository root without going through the owner
- **THEN** the architecture suite fails and names the source and the line
