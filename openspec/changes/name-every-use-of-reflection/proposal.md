# Proposal

## Why

AUD-ARC-11 (sprint 10) reports that the repository rules forbid reflection absolutely while production classes use it.
Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and the sentence is wrong in more places than
the audit counted**.

The sentence, `.claude/rules/architecture.md:180-181`, section "Pas de framework, pas de magie": "Jamais Spring /
Quarkus / Micronaut / Guice, jamais de réflexion, de classpath scanning ou d'annotations d'injection."
`.claude/skills/public-capability/SKILL.md:16-17` repeats it ("ni framework, ni réflexion, ni scan de classpath").

**Reflection** - three classes import `java.lang.reflect` (the audit's title says four, its evidence lists these three):

- `SqliteConnectionScope.java:3-5`, `:114`, `:194-198`, `:244`: a dynamic proxy of `java.sql.Connection`, and its
  nested `ScopedConnectionHandler`.
- `SqliteDatabaseLease.java:8-9`, `:114`: a dynamic proxy of `java.sql.Connection`.
- `CanonicalJsonSerializer.java:3-5`, `:156-175`: `RecordComponent` and its accessor, to serialise any record.

**Service and class loading** - not counted by the audit, and contradicting "jamais de classpath scanning" in the same
sentence:

- `ReasoningAdapterRegistry.java:63`: `ServiceLoader.load(ReasoningAdapter.class)` on the process class path, reached
  from the three transports through `ReasoningService.standard`. ADR-0095 decides it ("des providers classpath peuvent
  être découverts via `ServiceLoader`").
- `ProviderPluginActivator.java:52` and `:62`, `ProviderPluginActivation.java:19`: a dedicated `URLClassLoader` and
  `ServiceLoader.load(MorpheusProviderPlugin.class, loader)`. ADR-0090 decides it.

**Frameworks and injection annotations** - true today (no POM names one), and enforced by nothing: no rule of
`morpheus-architecture-tests` names `org.springframework`, `io.quarkus`, `io.micronaut`, `com.google.inject`,
`jakarta.inject` or `javax.inject`.

So one sentence makes four claims; two are false as written, and none is executed. `rules/meta.md` asks that a
divergence between a rule and the code be reported, not settled silently: the code is right here - canonical JSON and
the shared SQLite connection are structural, and the two loaders are decided by ADR - and the rule is wrong. An agent
that applies the sentence refuses a legitimate fix, or believes it may delete these uses.

## What Changes

- The rule says what is forbidden - a framework, injection by annotation, reflection or class loading used to wire the
  product or to build an object from configuration - and points to the test that names the uses that exist.
- Reflection is allowed to a named list of classes, and the list can only shrink.
- Service and class loading is allowed to a second named list, each entry with the ADR that decides it.
- No product class depends on a dependency-injection framework or on an injection annotation, and the build says so.

## Capabilities

### New Capabilities

- `reflection-boundary`: reflection, service loading and class loading exist in the product only where a named list
  says so, no framework wires it, and the repository rule describes exactly that.

### Modified Capabilities

(none)

## Impact

- `morpheus-architecture-tests`: one test class of ArchUnit rules.
- `.claude/rules/architecture.md` (the section above), `.claude/skills/public-capability/SKILL.md` (one sentence).
- No product code: the five uses stay as they are.
- No ADR: ADR-0090 and ADR-0095 decide the two loaders, ADR-0109 decides explicit wiring; the two reflection uses are
  implementation techniques, named in the test with their reason.
- `architectureTestsMinimum` rises by the tests added; never lowered.
- Related: `own-the-sqlite-connection-policy-in-one-place` touches `SqliteConnectionScope`; if it removes a proxy, the
  list shrinks in that lot, which the two-way check of this change requires.
