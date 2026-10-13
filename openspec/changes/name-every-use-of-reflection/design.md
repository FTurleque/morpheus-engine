# Design

## Context

- The sentence of the rule page predates the SQLite scope (a proxy lets every store share one physical connection), the
  canonical serialiser (records are serialised without a per-type writer) and the two plugin mechanisms (M22, M27).
- ArchUnit records a dependency on a JDK type by name even though the JDK is not imported; a rule can therefore forbid
  `java.lang.reflect..` to `com.morpheus..`. What `javac` emits for records, enums, lambdas and string concatenation
  goes through `invokedynamic` bootstraps (`java.lang.invoke`, `java.lang.runtime`), not through `java.lang.reflect` -
  believed, not verified here: task 1.1 measures it.
- `Class#isRecord`, `Class#getRecordComponents`, `Class#getName` and `Class#getSimpleName` are methods of
  `java.lang.Class`. The product calls `getClass().getSimpleName()` in dozens of places to name a failure type; that is
  not what the rule is about.
- `application.policy..` already forbids `Class.forName` and `ScriptEngine` as text
  (`PolicyPlatformArchitectureTest`); `ProviderPluginDiscovery` already forbids itself a loader as text. Those stay.

## Goals / Non-Goals

**Goals**: the rule page is true; a new use of reflection or of a loader is a named decision; a framework cannot arrive
unnoticed.

**Non-Goals**: removing a use that exists; forbidding `java.lang.Class` accessors; test sources (the architecture tests
use reflection to read records, and that is their job); `java.lang.invoke`, unless task 1.1 shows the product names it
by hand.

## Decisions

1. **Three ArchUnit rules, one intent each.** *No product class depends on `java.lang.reflect`*, *no product class
   loads a service or a class*, *no product class depends on an injection framework*. All three are "no dependency"
   propositions (ADR-0103), and the first two must see a use that is not spelled as an import - a lambda implementing
   `InvocationHandler`, a fully qualified `java.lang.reflect.Method` (`SqliteConnectionScope.java:244` is one). A
   textual assertion would miss both. No textual twin: `java.lang.reflect` declares no compile-time constant the
   product could read.

2. **A named list per rule, checked both ways.** The reflection list is the measured set (expected:
   `SqliteConnectionScope`, `SqliteDatabaseLease`, `CanonicalJsonSerializer`); the loading list is
   `ReasoningAdapterRegistry`, `ProviderPluginActivator`, `ProviderPluginActivation`. A class is named by its top-level
   type, nested classes and lambdas included. A second test fails when a named class no longer has the dependency - the
   form `AdapterCompositionRootArchitectureTest#everyNamedCompositionRootStillReachesAConcreteStoreOrProvider` already
   uses - so the list is exactly the measured set and can only shrink. Each entry carries its reason, and for a loader
   the ADR.

3. **What counts as loading.** A dependency on `java.util.ServiceLoader` or on any `java.lang.ClassLoader` subtype
   other than through `Class#getClassLoader`, and a call to `Class.forName`. The two SQLite classes call
   `X.class.getClassLoader()` to create their proxy; whether that call is carved out of the rule or the two classes
   sit on both lists is decided by task 1.2 on what ArchUnit actually reports. *Rejected*: one list for both rules - a
   proxy and a plugin loader are different risks, and the second list is the one a security review reads.

4. **The framework rule has no exception list.** Forbidden targets: `org.springframework..`, `io.quarkus..`,
   `io.micronaut..`, `com.google.inject..`, `jakarta.inject..`, `javax.inject..`, `jakarta.enterprise..`. They match
   nothing today, and here that is the point: `everyPackageTheseRulesNameResolvesToImportedClasses`-style vacancy
   checks do not apply to a target that must stay absent. The subject (`com.morpheus..`) is not empty.

5. **The rule page stops enumerating.** The section states the three interdictions in words and cites the test class;
   it does not copy the lists. A copied list is the kind of line `rules/architecture.md` already admits "nothing
   compares to the class it describes". The skill's sentence is aligned.

6. **No ADR.** Nothing is decided that an ADR does not already hold; the test's `because` clauses cite ADR-0090,
   ADR-0095 and ADR-0109.

## Risks / Trade-offs

- The reflection list may be longer than three once bytecode is read rather than imports. Then the list is what is
  measured, and the proposal's count is corrected in the pull request - neither number is trusted.
- A rule on a JDK package is new in this module. If ArchUnit does not report one of the expected dependencies, the
  two-way check of decision 2 fails on the first run, which is how the lot finds out.

## Open Questions

- `ReasoningAdapterRegistry` sits in `application.reasoning`, which the rule page calls a deterministic core with "no
  execution", and it instantiates whatever `ReasoningAdapter` the class path declares. ADR-0095 allows it.
  **Recommendation**: keep it, named on the loading list with ADR-0095; whether a class-path adapter should require a
  pin as a provider plugin does is a product question for the maintainer, outside this change.
