# Spec Delta

## Purpose

Define that a dependency deliberately held on a release line still receives the patches of that line, and that the
update bot is configured to propose those patches and not a change of line.

## ADDED Requirements

### Requirement: Reactor SHALL be on a patched release of the line the MCP SDK is built on

The Reactor BOM version of the root POM MUST belong to the line the POM states the MCP SDK requires, and MUST NOT be the
first release of that line while later ones exist.

#### Scenario: The shipped Reactor is a patched one

- **WHEN** the root POM is read after this change
- **THEN** `reactor-bom.version` is a `2024.0.x` release with `x` greater than zero, and the comment naming the line is
  still beside it

#### Scenario: The transport still works on it

- **WHEN** the real-process tests of the bounded STDIO transport and the MCP client timeout tests run
- **THEN** they pass unmodified

### Requirement: The update bot SHALL propose the patches of a held line and nothing else

While the POM holds Reactor on a line, the bot configuration MUST ignore major and minor updates of the Reactor BOM and
MUST NOT ignore its patch updates, and the build MUST fail when one exists without the other.

#### Scenario: The ignore entry exists with both update types

- **WHEN** the bot configuration is read
- **THEN** the Maven ecosystem carries an `ignore` entry for `io.projectreactor:reactor-bom` listing
  `version-update:semver-major` and `version-update:semver-minor`, and not `version-update:semver-patch`

#### Scenario: Removing the entry while the line is held fails the build

- **WHEN** the entry is deleted and the POM still states the Reactor line
- **THEN** the test fails and says the bot would propose a change of line

#### Scenario: Releasing the line removes the entry

- **WHEN** the POM no longer states that the transport stays on a Reactor line
- **THEN** the test fails until the ignore entry is removed, and names it
