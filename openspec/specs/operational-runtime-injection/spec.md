# operational-runtime-injection Specification

## Purpose
Define who owns the operational metrics and sink, and what a test may observe.

## Requirements

### Requirement: The operational runtime SHALL be built by the process and handed to its users

The metrics, the sink and the recorder MUST be instances built by the process entry point and passed to the components
that record or observe, and a component MUST NOT require the static default to work.

#### Scenario: Two stores, two sets of counters

- **WHEN** two stores are built with two runtimes in one JVM and one of them runs a transaction
- **THEN** only the metrics of that store's runtime have moved

#### Scenario: The served metrics are the process's

- **WHEN** a transaction runs in a started server and the metrics are requested
- **THEN** the response contains that transaction's counters

### Requirement: A test SHALL NOT read process-wide operational state

No test MUST read the static default runtime, and a test that asserts on metrics MUST assert on an instance it built.

#### Scenario: The contention test asserts absolute values

- **WHEN** the contention observability test runs
- **THEN** it asserts the exact value of counters on its own instance, not a difference of a shared one

#### Scenario: A test reading the default fails the build

- **WHEN** a test source mentions the static default runtime
- **THEN** the architecture suite fails and names the file

### Requirement: The users of the static default SHALL only decrease

The set of product classes that reach the static default runtime MUST be a named list that only shrinks.

#### Scenario: A new static user is refused

- **WHEN** a class not on the list reaches the static default runtime
- **THEN** the architecture suite fails and names the class
