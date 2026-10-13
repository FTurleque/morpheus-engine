# Tasks

## 1. Measure before changing

- [ ] 1.1 Write the reflection rule with an empty list and run it. Proof: the classes ArchUnit reports; on 2026-10-13 a
      scan of imports finds `SqliteConnectionScope`, `SqliteDatabaseLease` and `CanonicalJsonSerializer`. A class
      reported and not among these three is read before it is listed: compiler-generated code is a reason to narrow
      the rule, not to list the class.
- [ ] 1.2 The same for loading: `ServiceLoader`, class loaders, `Class.forName`. Proof: the reported classes; expected
      `ReasoningAdapterRegistry`, `ProviderPluginActivator`, `ProviderPluginActivation`, and the answer for the two
      `getClassLoader()` calls of the SQLite classes (design, decision 3).
- [ ] 1.3 Confirm that no POM of the reactor declares one of the frameworks of the design, decision 4. Proof: the
      search.
- [ ] 1.4 Search `.claude/`, `CONTRIBUTING.md` and the living pages of `docs/` for the sentence or its paraphrase.
      Proof: the list; on 2026-10-13, `.claude/rules/architecture.md` and `.claude/skills/public-capability/SKILL.md`.

## 2. The rules

- [ ] 2.1 The reflection rule and its list, each entry with its reason. Mechanism: ArchUnit - intent "no dependency on
      `java.lang.reflect`". Proof: green.
- [ ] 2.2 The loading rule and its list, each entry with its ADR. Mechanism: ArchUnit - intent "no service or class
      loading". Proof: green.
- [ ] 2.3 The two-way checks: a listed class without the dependency fails. Proof: green, and red when a name that does
      not qualify is added to either list.
- [ ] 2.4 The framework rule. Mechanism: ArchUnit - intent "no dependency on an injection framework". Proof: green.

## 3. Break each rule once, then revert (`.claude/skills/enforcement-choice`)

- [ ] 3.1 A `Proxy.newProxyInstance` in a class of `morpheus-api`. Proof: the named failure.
- [ ] 3.2 An `InvocationHandler` written as a lambda, without an import of `java.lang.reflect`. Proof: the named
      failure - this is the case a textual assertion would miss.
- [ ] 3.3 A `ServiceLoader.load` in a class of `morpheus-mcp`. Proof: the named failure.
- [ ] 3.4 The framework rule cannot be broken without adding a dependency to a POM; break it with a test-scoped
      annotation type declared under one of the forbidden packages in the architecture tests' own sources, or record
      in the pull request that the break was not run and why.

## 4. The rule pages

- [ ] 4.1 `.claude/rules/architecture.md`, section "Pas de framework, pas de magie": the three interdictions in words,
      the name of the test class, no copy of the lists; the paragraph on explicit wiring unchanged.
- [ ] 4.2 `.claude/skills/public-capability/SKILL.md`: the sentence aligned.
- [ ] 4.3 Dated records are left as they are.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`. Proof: green through the required checks, both platforms.
- [ ] 5.2 `architectureTestsMinimum` raised if the lot chooses to, never lowered.
- [ ] 5.3 Pull request description: the finding, the measured lists of 1.1 and 1.2 beside the audit's count, the
      breaks, the justification for a change under `morpheus-architecture-tests/`.
