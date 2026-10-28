# Tasks

## 1. Measure before changing

- [x] 1.1 Write the reflection rule with an empty list and run it. Proof: the classes ArchUnit reports; on 2026-10-13 a
      scan of imports finds `SqliteConnectionScope`, `SqliteDatabaseLease` and `CanonicalJsonSerializer`. A class
      reported and not among these three is read before it is listed: compiler-generated code is a reason to narrow
      the rule, not to list the class.
      *2026-10-28, on `65a97b06`: in `application`, `CanonicalJsonSerializer` is already confined by ADR-0150
      (`ApplicationTechnologyBoundaryArchitectureTest`, family "reflection and class loading"), so the new rule covers
      `com.morpheus..` outside `application`. With an empty list it reports `SqliteConnectionScope`,
      `SqliteDatabaseLease` and `SqliteContentionMetrics` (`Proxy.isProxyClass`, `Proxy.getInvocationHandler`), the
      third one missed by the audit and by the 2026-10-13 count. No compiler-generated access is reported: lambdas,
      records and string concatenation leave no `java.lang.invoke` dependency in ArchUnit.*
- [x] 1.2 The same for loading: `ServiceLoader`, class loaders, `Class.forName`. Proof: the reported classes; expected
      `ReasoningAdapterRegistry`, `ProviderPluginActivator`, `ProviderPluginActivation`, and the answer for the two
      `getClassLoader()` calls of the SQLite classes (design, decision 3).
      *2026-10-28: outside `application`, `ProviderPluginActivator` and `ProviderPluginActivation`;
      `ReasoningAdapterRegistry` is the second name of the ADR-0150 list. The two `X.class.getClassLoader()` calls of
      the SQLite classes produce no `ClassLoader` dependency in ArchUnit (the value goes straight to
      `Proxy.newProxyInstance`): no carve-out is needed, and the SQLite classes are on the reflection list only.
      No production class calls `Class.forName`.*
- [x] 1.3 Confirm that no POM of the reactor declares one of the frameworks of the design, decision 4. Proof: the
      search.
      *2026-10-28: `grep` of `pom.xml` and `*/pom.xml` for `springframework`, `quarkus`, `micronaut`, `google.inject`,
      `guice`, `jakarta.inject`, `javax.inject`, `jakarta.enterprise`: no match.*
- [x] 1.4 Search `.claude/`, `CONTRIBUTING.md` and the living pages of `docs/` for the sentence or its paraphrase.
      Proof: the list; on 2026-10-13, `.claude/rules/architecture.md` and `.claude/skills/public-capability/SKILL.md`.
      *2026-10-28: `.claude/rules/architecture.md` (section "Pas de framework, pas de magie"),
      `.claude/rules/code-review.md` (line "Sans framework"), `.claude/skills/public-capability/SKILL.md`.
      `CONTRIBUTING.md` and the living pages of `docs/` hold no absolute sentence; `.claude/CLAUDE.md` and
      `docs/architecture/arc42/05-vue-blocs.md` already describe the ADR-0150 confinement correctly.*

## 2. The rules

- [x] 2.1 The reflection rule and its list, each entry with its reason. Mechanism: ArchUnit - intent "no dependency on
      `java.lang.reflect`". Proof: green.
      *2026-10-28: `ReflectionAndClassLoadingBoundaryArchitectureTest#reflectionIsReachedOnlyByTheClassesNamedForIt`,
      list `REFLECTION` (the three SQLite runtime classes, each with its reason). Inside `application`, the ADR-0150
      rule already holds it. A textual twin
      (`noSourceOutsideTheApplicationNamesReflectionOrLoadingUnlessItsClassIsNamed`) is added: see 3.2.*
- [x] 2.2 The loading rule and its list, each entry with its ADR. Mechanism: ArchUnit - intent "no service or class
      loading". Proof: green.
      *2026-10-28: `serviceAndClassLoadingAreReachedOnlyByTheClassesNamedForThem`, list `LOADING` (the two
      `sdk.provider` classes, SEC-0129). `ReasoningAdapterRegistry` (ADR-0095) stays named by ADR-0150 in
      `application`.*
- [x] 2.3 The two-way checks: a listed class without the dependency fails. Proof: green, and red when a name that does
      not qualify is added to either list.
      *2026-10-28: `everyNamedClassStillReachesItsFamily` green; red with `com.morpheus.cli.MorpheusMain` added to
      `REFLECTION` and `SqliteDatabaseLease` added to `LOADING`, both names reported.
      `theApplicationKeepsItsOwnConfinementOfReflectionAndLoading` fails if the ADR-0150 family these rules leave the
      application to disappears from `ApplicationTechnologyBoundaryArchitectureTest`.*
- [x] 2.4 The framework rule. Mechanism: ArchUnit - intent "no dependency on an injection framework". Proof: green.
      *2026-10-28: `noProductClassDependsOnAnInjectionFramework`, on every `com.morpheus..` class, application
      included.*

## 3. Break each rule once, then revert (`.claude/skills/enforcement-choice`)

- [x] 3.1 A `Proxy.newProxyInstance` in a class of `morpheus-api`. Proof: the named failure.
      *2026-10-28: `com.morpheus.api.transport.ZzBreakProxy reaches reflection through ... calls method
      <java.lang.reflect.Proxy.newProxyInstance(...)>`; reverted.*
- [x] 3.2 An `InvocationHandler` written as a lambda, without an import of `java.lang.reflect`. Proof: the named
      failure - this is the case a textual assertion would miss.
      *2026-10-28: measured the other way round. `(java.lang.reflect.InvocationHandler) (proxy, method, args) -> null`
      leaves no dependency ArchUnit reports (the type lives in the `invokedynamic` descriptor), so the bytecode rule
      passed; the textual twin failed and named `ZzBreakLambda`. Hence the twin, against design decision 1, which
      assumed the opposite. A lambda handed to `Proxy.newProxyInstance` is seen by the bytecode rule through the
      call.*
- [x] 3.3 A `ServiceLoader.load` in a class of `morpheus-mcp`. Proof: the named failure.
      *2026-10-28: `com.morpheus.mcp.ZzBreakLoader reaches service or class loading through ... calls method
      <java.util.ServiceLoader.load(java.lang.Class)>`, and the textual twin named it too; reverted.*
- [x] 3.4 The framework rule cannot be broken without adding a dependency to a POM; break it with a test-scoped
      annotation type declared under one of the forbidden packages in the architecture tests' own sources, or record
      in the pull request that the break was not run and why.
      *2026-10-28: the importer excludes test sources, so the break was made in production sources: a temporary
      `javax.inject.Inject` annotation in `morpheus-api/src/main/java` on a class of `com.morpheus.api.transport`.
      Failure: `Class <com.morpheus.api.transport.ZzBreakInjected> is annotated with <javax.inject.Inject>`;
      reverted.*

## 4. The rule pages

- [x] 4.1 `.claude/rules/architecture.md`, section "Pas de framework, pas de magie": the three interdictions in words,
      the name of the test class, no copy of the lists; the paragraph on explicit wiring unchanged.
- [x] 4.2 `.claude/skills/public-capability/SKILL.md`: the sentence aligned.
      *2026-10-28: and `.claude/rules/code-review.md`, found by 1.4.*
- [x] 4.3 Dated records are left as they are.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`. Proof: green through the required checks, both platforms.
      *Left to the integrator of the sprint 10 lot.*
- [ ] 5.2 `architectureTestsMinimum` raised if the lot chooses to, never lowered.
      *Not raised here: six declared executions are added; the integrator decides on the measured total.*
- [ ] 5.3 Pull request description: the finding, the measured lists of 1.1 and 1.2 beside the audit's count, the
      breaks, the justification for a change under `morpheus-architecture-tests/`.
