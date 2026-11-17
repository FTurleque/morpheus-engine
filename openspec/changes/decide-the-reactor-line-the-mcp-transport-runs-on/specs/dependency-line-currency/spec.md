## RENAMED Requirements

- FROM: `### Requirement: Reactor SHALL be on a patched release of the line the MCP SDK is built on`
- TO: `### Requirement: Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on`

## MODIFIED Requirements

### Requirement: Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on

The Reactor BOM version of the root POM MUST belong to the line the POM comment names, that line MUST still receive
open-source fixes, the MCP SDK version of the POM MUST be qualified on it by the real-process transport tests, and it
MUST NOT be the first release of that line while later ones exist.

#### Scenario: The shipped Reactor is a patched one

- **WHEN** the root POM is read after this change
- **THEN** `reactor-bom.version` is a release of the line the comment beside the import names, with a third number
  greater than zero, and the comment is still beside it

#### Scenario: The transport still works on it

- **WHEN** the real-process tests of the bounded STDIO transport and the MCP client timeout tests run
- **THEN** they pass unmodified

#### Scenario: The shipped reactor-core is outside the ranges of the two known advisories

- **GIVEN** CVE-2026-47863 and CVE-2026-47857, whose ranges end at `reactor-core` 3.7.19 on the 3.7 line
- **WHEN** the dependency tree of `morpheus-mcp-transport` is produced
- **THEN** the resolved `reactor-core` is a release outside both ranges
- **AND** no suppression is added to `config/dependency-check-suppressions.xml` for either advisory

#### Scenario: A line without open-source fixes fails the build

- **GIVEN** a root POM whose comment names a Reactor line that the line-currency test does not declare maintained
- **WHEN** the architecture suite runs
- **THEN** the test fails and names the line and the property `reactor-bom.version`
- **AND** no file is rewritten by the test
