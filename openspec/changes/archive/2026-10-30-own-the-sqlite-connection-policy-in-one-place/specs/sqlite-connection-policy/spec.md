# Spec Delta

## Purpose

Define how many connections an operation opens and who decides.

## ADDED Requirements

### Requirement: An operation SHALL open one physical connection whichever transport carries it

An operation served through the CLI, the MCP server or an HTTP route MUST open exactly one physical SQLite connection and
run the schema check once.

#### Scenario: An MCP tool call opens one connection

- **WHEN** an MCP tool that reads several stores is called
- **THEN** one physical connection is opened and one schema check runs

#### Scenario: The transports agree

- **WHEN** the same operation is served through the CLI, the MCP server and HTTP
- **THEN** each opens one connection

### Requirement: An adapter SHALL build a SQLite store only inside a connection scope

A class of an adapter that instantiates a SQLite store MUST do so inside an open connection scope, unless it is one of a
closed list of maintenance sites, each with its reason.

#### Scenario: A store built outside a scope fails the build

- **WHEN** a class of an adapter builds a SQLite store outside a scope and is not a named maintenance site
- **THEN** the architecture suite fails and names the class

#### Scenario: A stale exception fails the build

- **WHEN** a named maintenance site no longer builds a store
- **THEN** the suite fails and asks for its removal from the list

### Requirement: Scopes SHALL NOT nest, and sequential runtimes SHALL work

Opening a scope while one is active on the thread MUST be refused with a named refusal, and two runtimes of one adapter
built one after the other on one thread MUST both work.

#### Scenario: A nested scope is refused by name

- **WHEN** a scope is opened inside another on the same thread
- **THEN** the second is refused with a refusal that names the cause

#### Scenario: Two runtimes in sequence

- **WHEN** two runtimes of one adapter are built and closed one after the other on one thread
- **THEN** both operate, each on its own connection
