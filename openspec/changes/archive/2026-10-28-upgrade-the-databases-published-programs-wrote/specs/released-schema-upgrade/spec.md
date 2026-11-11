## ADDED Requirements

### Requirement: The upgrade SHALL also be executed on a database that the released program wrote

For each released schema level, the repository MUST keep one database written by the CLI of a tag that shipped
that level, with its tag, commit and SHA-256, and a test MUST upgrade a copy of it with the current runtime, find every
row the level held with its values unchanged (a value a later migration rewrites on purpose is expected rewritten), and
read its identities and history back through the stores.

#### Scenario: A database written by the first release is upgraded

- **WHEN** the database that the 1.0.0 CLI wrote at level 12 is opened by the current store
- **THEN** the ledger reaches `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, the conflicts it stored as
  `SELECTED_BY_PRECEDENCE` read `PRECEDENCE_RECORDED`, and every other row is unchanged

#### Scenario: A declared level without a published database is refused

- **WHEN** a level is declared and no database written by the CLI of its tag is kept for it
- **THEN** the test fails and names the directory to produce

#### Scenario: A kept database that was altered is refused

- **WHEN** the bytes of a kept database differ from the SHA-256 recorded with it
- **THEN** the test fails and names the tag that wrote it

### Requirement: The release gate SHALL count one upgrade case per declared level

`validate-r2` MUST read the declared levels and refuse an upgrade report in which a factory did not run exactly one
case per distinct declared level.

#### Scenario: A level is dropped from the run

- **WHEN** a factory of the upgrade report ran fewer cases than there are distinct declared levels
- **THEN** `validate-r2` refuses and writes no summary
