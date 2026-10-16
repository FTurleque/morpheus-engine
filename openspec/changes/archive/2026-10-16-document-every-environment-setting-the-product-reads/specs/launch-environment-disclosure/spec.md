# Spec Delta

## Purpose

Define that every environment variable the product reads is documented where an operator looks for it, with its
precedence where another source can set the same value, and that the build refuses a variable that is not documented.

## ADDED Requirements

### Requirement: Every MORPHEUS environment variable the product mentions SHALL be named in the operator documentation

Every string literal of the form `MORPHEUS_<NAME>` under a module's `src/main/java` MUST appear as a whole word in a
page under `docs/user` or `docs/developer`, unless the literal is listed by name, with a reason, as not being an
environment variable; and the build MUST fail otherwise.

#### Scenario: The current variables are all documented

- **WHEN** the documentation coherence test runs after this change
- **THEN** it finds a non-empty set of names, every one of them in an operator page or excused, and passes

#### Scenario: A new variable without documentation fails the build

- **WHEN** a main source starts reading `MORPHEUS_SOMETHING_NEW` and no page under `docs/user` or `docs/developer` names
  it
- **THEN** the test fails and names the variable and the source file that mentions it

#### Scenario: A mention outside the operator documentation does not count

- **WHEN** a variable is named only in an ADR, an audit report, a release note or an OpenSpec artifact
- **THEN** the test fails for that variable

#### Scenario: A name that is not a variable is excused by name

- **WHEN** a literal such as a refusal code matches the pattern
- **THEN** it passes only because it is listed with a reason, and an entry without a reason, or one that no main source
  mentions any more, makes the test fail and names the entry

#### Scenario: A longer name does not satisfy a shorter one

- **WHEN** the documentation names `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIRECTORY` and the source reads
  `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR`
- **THEN** the test fails for the name the source reads

### Requirement: The remote server guide SHALL document the provider-plugin directory variable and its precedence

`docs/user/TEAM_REMOTE_SERVER.md` MUST list `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` among the environment variables of the
remote server, MUST state the order in which the option, the variable, the JVM property and the default are consulted,
and MUST NOT state that the option is the only source of that directory.

#### Scenario: The variable is in the table with its alternative

- **WHEN** the environment table of the remote server guide is read
- **THEN** it holds `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` beside the other `MORPHEUS_SERVER_*` variables, described as
  the alternative to `--provider-plugin-dir`

#### Scenario: The stated precedence is the implemented one

- **WHEN** the remote server is launched with the option and the variable set to different directories
- **THEN** the directory of the option is used, as the guide states; with the option absent the variable is used; with
  both absent the JVM property, then `<config>/provider-plugins`

#### Scenario: The guide no longer says the option is the only source

- **WHEN** the paragraph on the plugin directory is read
- **THEN** it says the client never chooses the directory and that the server takes it from its own launch
  configuration — option, environment or property — not from the option alone
