# test-jvm-state-isolation Specification

## Purpose
Define which tests may change state of the whole JVM, and on what condition a module runs its tests in parallel.

## Requirements

### Requirement: A test that changes state of the whole JVM SHALL run isolated

In every module, a test source that sets or clears a system property, replaces the standard streams, changes the
default locale or time zone, or registers or deregisters a JDBC driver MUST be a top-level class marked to run alone.
A lock declared on a resource does not satisfy this: the tests that read the changed state declare none.

#### Scenario: A locale test without isolation

- **WHEN** a test class changes the default locale and is not marked to run alone
- **THEN** the architecture suite fails and names the file and the call

#### Scenario: Isolation on a nested class only

- **WHEN** only a nested class of the test source is marked to run alone
- **THEN** the architecture suite fails, because the outer class still runs beside other tests

### Requirement: The transport modules SHALL run their tests in parallel

The modules of the SQLite store, the HTTP adapter, the MCP adapter and the CLI MUST run their test classes and
methods concurrently on a fixed pool, and a count a test asserts on MUST come from the operation it ran, never from a
counter of the process.

#### Scenario: Two counting tests run at once

- **WHEN** two tests that count connections run concurrently in the same JVM
- **THEN** each reads only the connections and schema checks of its own operation
