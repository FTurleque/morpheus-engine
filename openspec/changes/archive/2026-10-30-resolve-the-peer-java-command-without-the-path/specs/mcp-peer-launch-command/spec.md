# Spec Delta

## Purpose

Define which executable starts an MCP peer and what a configured launcher must be to be accepted, so that the launch
never depends on the search path of the MORPHEUS process.

## ADDED Requirements

### Requirement: Without a configured launcher, a peer SHALL be started with the runtime MORPHEUS runs on

When no Java command is configured for a peer, the settings MUST resolve the launcher to the Java executable under the
`java.home` of the running JVM, as an absolute path.

#### Scenario: The default names no bare command

- **WHEN** the MINOS and NEXUS settings are resolved with no Java command configured
- **THEN** the launcher is an absolute path under `java.home`, and the literal `java` appears in neither settings class
  as a default

#### Scenario: A manipulated PATH changes nothing

- **WHEN** the environment given to the settings puts another `java` first on `PATH` and configures no launcher
- **THEN** the resolved launcher is still the one under `java.home`

### Requirement: A configured launcher SHALL be an absolute path to an existing regular file

A configured Java command MUST be an absolute path naming an existing regular, executable file; otherwise the settings
MUST report a configuration error that names the setting and the reason, and the integration MUST NOT start.

#### Scenario: A bare name is refused

- **WHEN** the launcher is configured as `java`
- **THEN** the settings report an error saying the value must be an absolute path, the integration is not enabled, and
  no process is started

#### Scenario: A relative path is refused

- **WHEN** the launcher is configured as `bin/java`
- **THEN** the same error is reported

#### Scenario: A missing file or a directory is refused

- **WHEN** the launcher names a path that does not exist, or a directory
- **THEN** the settings report an error naming that reason

#### Scenario: A link is resolved, and the real file is what runs

- **WHEN** the launcher is configured as an absolute path that is a symbolic link to a Java executable
- **THEN** the settings accept it, and the launcher they hold and report is the resolved real path

### Requirement: The launcher path SHALL follow the existing disclosure boundary

The resolved launcher MUST be shown by local status views and MUST NOT be added to the details the remote projection
relays.

#### Scenario: The remote view gains nothing

- **WHEN** the remote status of an integration is read after this change
- **THEN** it carries no launcher path, and the allowlist of remote-safe detail keys is unchanged
