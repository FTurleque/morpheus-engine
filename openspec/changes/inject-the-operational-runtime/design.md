# Design

## Context

- The process is wired explicitly in `MorpheusMain`; the per-request runtimes are built by the composition roots named
  in ADR-0109, whose list only shrinks.
- `SqliteContentionMetrics` is a class of static methods called from the transaction runner and the connection
  factory, deep in the store; it is the path the one global-reading test observes.
- `OperationalRecorder.noop()` already exists for a caller that wants none.

## Goals / Non-Goals

**Goals**: a test can observe metrics that only its own code under test touched; the process decides the sink once,
where it can refuse.

**Non-Goals**: enabling JUnit parallelism (this change removes one obstacle and says what the others are); a dependency
injection framework; rewriting every user in one lot.

## Decisions

1. **An `OperationalRuntime` value** - metrics, sink, recorder - built by `MorpheusMain` from the validated mode and the
   layout, and passed by constructor.

2. **The store path first.** The contention metrics take the metrics they write to from the store's construction
   instead of the static runtime; `SqliteContentionObservabilityTest` builds its own and asserts absolute values, not a
   delta. This is the path the audit names, and the hardest one: it goes through the connection scope.

3. **The other users follow in the same lot only if the diff stays reviewable**; otherwise each is a follow-up, and
   `LocalOperationalRuntime` remains the default they read. A textual rule holds the direction: the number of classes
   that mention `LocalOperationalRuntime` only shrinks, as the list of composition roots does.

4. **No test reads the static default.** A textual rule on the test sources.

5. **An ADR** states that JUnit parallelism is conditioned on this injection being complete, and lists what else shares
   state between tests, as far as the lot finds (system properties set by tests, fixed ports, the working directory).

## Risks / Trade-offs

- A constructor parameter added to stores widens many call sites, including the named composition roots; the persistence
  parity tests must pass unmodified apart from construction.
- A half-injected runtime - some counters on the instance, some on the default - would make the metrics view lie by
  omission. Decision 3's rule and a test that the served metrics contain the store counters hold it.
