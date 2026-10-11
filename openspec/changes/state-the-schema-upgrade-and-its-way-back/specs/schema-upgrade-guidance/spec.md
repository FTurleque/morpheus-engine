# Spec Delta

## Purpose

Define what the operator documentation states when a MORPHEUS version advances the SQLite schema — the version reached,
the backup that keeps a way back possible, and the way back — and how that statement is kept equal to the code.

## ADDED Requirements

### Requirement: The latest upgrade guide SHALL state the schema version the code supports

The upgrade guide with the highest version among `docs/user/UPGRADE_*.md` MUST state, as the schema version reached, the
value of `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, and the build MUST fail when the two differ.

#### Scenario: The guide of the version being released names the schema reached

- **WHEN** the documentation coherence test runs on a checkout whose constant is `N`
- **THEN** it selects the guide with the highest version in its file name, finds `V0N` (three digits, as the migration
  files are named) stated as the schema reached, and passes

#### Scenario: A migration added without touching the guide fails the build

- **WHEN** a migration is added and `SUPPORTED_SCHEMA_VERSION` is raised by one while no upgrade guide changes
- **THEN** the test fails, names the guide it selected, the version it states and the version the constant declares

#### Scenario: A patch release without a migration needs no new guide

- **WHEN** the product version advances and the constant does not
- **THEN** the test passes without any new `UPGRADE_*.md`

#### Scenario: The rule holds no copy of the value

- **WHEN** the test source is read
- **THEN** it contains no integer literal equal to the schema version; the expected value is the constant

### Requirement: The latest upgrade guide SHALL make the pre-upgrade backup mandatory and say which program takes it

The latest upgrade guide MUST present the backup as a required step, placed before the installation step, taken and
verified with the version already installed, and MUST state that a backup created after the new version has been started
is at the new schema.

#### Scenario: The backup step comes first and is not optional

- **WHEN** the latest guide is read
- **THEN** a section whose title marks the backup as mandatory appears before the installation section, names
  `server backup create` and `server backup verify`, and contains no wording that makes the backup conditional on the
  operator's wish

#### Scenario: The guide warns against a backup taken by the new version

- **WHEN** the latest guide is read
- **THEN** it states that `server backup create` brings the database to the schema of the program that runs it, and
  therefore that the backup allowing a way back is the one taken before the new version is started

### Requirement: The latest upgrade guide SHALL describe the way back and quote the refusal an older program gives

The latest upgrade guide MUST state that returning to an earlier version requires restoring a backup taken before the
upgrade, and MUST quote the refusal the earlier program gives on a database that was migrated, in the words of the
source.

#### Scenario: The quoted refusal is the one in the code

- **WHEN** the documentation coherence test runs
- **THEN** it finds the sentence `is newer than supported` both in the latest guide and in `SqliteSchemaManager.java`

#### Scenario: A reworded refusal fails the build

- **WHEN** the refusal message of `SqliteSchemaManager` is reworded and the guide is not
- **THEN** the test fails and names the sentence the guide still quotes

#### Scenario: The way back names the offline restore

- **WHEN** the latest guide is read
- **THEN** its rollback section orders: stop the server, reinstall the earlier version, `server restore --confirm` of the
  pre-upgrade backup, start — and says that work recorded after the backup is not carried back

### Requirement: An earlier upgrade guide SHALL name the release it describes and point to the latest guide

Every `docs/user/UPGRADE_*.md` other than the latest MUST open with a notice naming the release it describes and linking
the latest guide.

#### Scenario: A statement true of an earlier release no longer reads as current

- **WHEN** `UPGRADE_1_2.md` is read
- **THEN** its opening notice says it describes the upgrade to 1.2.0 and links the latest guide, and the sentence about
  an unchanged schema on the way back to 1.1.0 is bounded to 1.2.0

#### Scenario: A new guide demotes the previous one

- **WHEN** a guide with a higher version is added and the previously latest guide receives no notice
- **THEN** the test fails and names the guide that lacks the link to the latest one
