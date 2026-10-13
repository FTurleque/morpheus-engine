# Proposal

## Why

AUD-QUA-13 (sprint 4) reports that observability rests on a mutable static singleton that forbids JUnit parallelism.
Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding **holds**:

- `LocalOperationalRuntime` holds `METRICS`, `SINK` and `RECORDER` as static finals; `OperationalMetrics` accumulates
  for the life of the JVM and has no reset.
- Ten classes of `src/main` reach it statically, among them `SqliteContentionMetrics`, `LocalSourceInventoryScanner`,
  three application services, two API services and two CLI classes.
- One test reads that process-wide state, `SqliteContentionObservabilityTest`, and asserts an exact delta before and
  after.
- No JUnit parallel configuration exists (no `junit-platform.properties`; Surefire declares neither `forkCount` nor
  `parallel`).

The delta protects the sequential execution of today. Turning on JUnit parallelism - the obvious lever on a reactor of
this size - would make that assertion non-deterministic. `.claude/rules/testing.md` forbids "a mutable static field
shared between tests"; this counter is one by construction. And the two changes before this one need the runtime to be
built by the process: a file sink needs a directory, an unknown mode needs a launcher to refuse it.

## What Changes

- The operational runtime is an object the process builds and hands to what observes or records, starting with the
  paths tests observe.
- A static default remains as the wiring of a process that was given none, and nothing reads it in a test.
- The condition under which JUnit parallelism may be enabled is written down.

## Capabilities

### New Capabilities

- `operational-runtime-injection`: the metrics and the sink are instances owned by the process, not global state.

### Modified Capabilities

(none)

## Impact

- `morpheus-application` (operability package and its three users), `morpheus-store-sqlite`
  (`SqliteContentionMetrics`), `morpheus-api`, `morpheus-cli` (wiring in `MorpheusMain` and the named composition
  roots).
- An ADR. No public surface moves.
- Last of sprint 4: after `leave-a-trace-when-a-failure-is-swallowed`, which adds users of the runtime.
