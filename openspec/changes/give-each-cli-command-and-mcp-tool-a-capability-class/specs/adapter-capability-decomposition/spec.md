# Spec Delta

## Purpose

Define where the behaviour of a CLI command and of an MCP tool lives, what the class that dispatches to it may hold,
and what an extraction from a dispatcher must leave unchanged.

## ADDED Requirements

### Requirement: A CLI command and an MCP tool SHALL be served by a capability class, and a dispatch class SHALL hold no use case

Every command word the CLI accepts and every tool the MCP server publishes MUST be served by a class of the adapter's
capability package. A class of `morpheus-cli` or `morpheus-mcp` that maps a command word or a tool name to its
handler MUST NOT depend on a use case of `com.morpheus.application..`; the application types it may depend on are a
named, exact set, limited to what dispatching itself needs.

#### Scenario: A command is added to the dispatcher

- **WHEN** a new command is implemented inside the class that dispatches CLI commands, calling an application service
- **THEN** the architecture suite fails and names the dispatch class and the service

#### Scenario: A tool is added to the server class

- **WHEN** a new tool is implemented inside the class that registers MCP tools, calling an application service
- **THEN** the architecture suite fails in the same way

#### Scenario: An allowance that is no longer used

- **WHEN** a type named in the set of application types a dispatcher may depend on is no longer used by any
  dispatcher
- **THEN** the suite fails until the name is removed

### Requirement: Every published command word and tool name SHALL be claimed by exactly one capability class

The dispatch of each adapter MUST be exhaustive and unambiguous against what the adapter publishes: the commands of
the embedded help for the CLI, the tool catalogue for MCP. A word that no capability class claims, and a word that
two claim, MUST fail the build. A word nobody publishes MUST still be answered by the dispatcher as an unknown
command or an unknown tool.

#### Scenario: A command documented by the help and claimed by no class

- **WHEN** the embedded help lists a command that no capability class claims
- **THEN** a test fails and names the command

#### Scenario: Two classes claim the same word

- **WHEN** two capability classes claim the same command word or the same tool name
- **THEN** a test fails and names the word and both classes, whatever order the dispatcher would try them in

#### Scenario: An unknown command

- **WHEN** the CLI is invoked with a word nobody claims
- **THEN** it answers with the usage exit code and the message it gives today

### Requirement: An extraction from a dispatcher SHALL change no output, no exit code, no tool name and no payload

For every command and every tool moved out of a dispatch class, the text output, the JSON output, the exit code or
failure code of a refusal, the option names and the tool's input schema MUST be identical before and after, and a
test written and passing before the move MUST prove it without being edited by the move.

#### Scenario: A moved command prints what it printed

- **WHEN** a command served by a capability class is run on a fixture, in text and in JSON
- **THEN** both outputs are byte-identical to those recorded when the dispatch class served it

#### Scenario: A moved command refuses as it refused

- **WHEN** that command is given an unknown option, and when it is given a project that does not exist
- **THEN** the exit code and the message are those recorded before the move

#### Scenario: The contracts did not move

- **WHEN** the extraction is complete
- **THEN** `contracts/public-surfaces.tsv`, the tool catalogue served by the MCP server and the embedded help are
  identical to what they were

### Requirement: An extraction SHALL NOT add a composition root

A capability class created by splitting a dispatcher MUST NOT depend on a concrete store or provider. The named
composition roots of ADR-0109 MUST NOT gain a name by this change.

#### Scenario: A tool class reads a store of a concrete type

- **WHEN** an extracted MCP capability class reads a field or calls a method whose type is a concrete SQLite store
- **THEN** `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider` fails and names the class, and the remedy is a
  port type, not a new name in the list

#### Scenario: The split root leaves the list

- **WHEN** the class that used to serve the historical MCP tools no longer reaches a concrete store, or no longer
  exists
- **THEN** `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider` fails until its name is removed
