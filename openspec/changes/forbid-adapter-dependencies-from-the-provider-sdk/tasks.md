# Tasks

## 1. Measure before changing

- [ ] 1.1 List the subject of every rule of `AdapterSiblingArchitectureTest` and of every other ArchUnit rule of the
      module that names `com.morpheus.sdk`. Proof: the table; on 2026-10-13 the SDK is a subject of none.
- [ ] 1.2 List what `morpheus-provider-sdk` depends on today: its POM and the packages its sources import. Proof: the
      list; on 2026-10-13, `domain`, `application` and the JDK only.
- [ ] 1.3 Check whether a class of an adapter declares a compile-time constant the SDK could read without a bytecode
      dependency (ADR-0103, second asymmetry). Proof: the answer decides whether the rule needs a textual twin; the
      design assumes it does not.
- [ ] 1.4 Search `.claude/` and `docs/` (dated records excluded) for `provider.sdk` used as a package name. Proof: the
      list; on 2026-10-13, four lines in three files under `.claude/`.

## 2. The rule

- [ ] 2.1 Add `theProviderSdkDependsOnNoAdapter`: subject `SDK`, targets `STORES`, `PROVIDERS`, `INTEGRATIONS`, `MCP`,
      `API`, `CLI`, with a `because` that cites ADR-0090. Mechanism: ArchUnit rule - intent "the SDK has no dependency
      on an adapter". Proof: green on `HEAD`.
- [ ] 2.2 Break it once, then revert (`.claude/skills/enforcement-choice`): add to one SDK class a reference to a class
      of `com.morpheus.store.memory`, with the POM edge it needs. Proof: the named failure, quoted in the pull request;
      the working tree clean afterwards.
- [ ] 2.3 If the transport already lives in its own package (`give-the-shared-mcp-transport-its-own-package`), add that
      package to the targets and to `NAMED`. Proof: the vacancy check passes.

## 3. The subjects compared to the rules

- [ ] 3.1 Declare the subject families of the class and add the test that fails when one of them is the subject of no
      rule; each rule keeps its own `@Test` method. Proof: red when the SDK rule of 2.1 is removed, green with it.
- [ ] 3.2 Break it once, then revert: add a family to the declared subjects without a rule. Proof: the failure names
      the family.
- [ ] 3.3 Rewrite the Javadoc of the class: it points to the declared subjects, says the SDK rule sees
      `morpheus-provider-sdk` and not the testkit, and keeps the paragraph on vacant patterns.

## 4. The rule pages

- [ ] 4.1 `.claude/rules/architecture.md`: a `JAMAIS` entry for `com.morpheus.sdk..` citing
      `theProviderSdkDependsOnNoAdapter`; `provider.sdk` becomes `sdk.provider` at the two places of 1.4.
- [ ] 4.2 `.claude/skills/milestone-quadruplet/SKILL.md` and `.claude/agents/architect.md`: the same spelling.
- [ ] 4.3 Dated records (audits, `VALIDATION_*`, archived OpenSpec changes) are left as they are.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`; `morpheus-provider-reference` built before the architecture tests. Proof: green on
      both platforms through the required checks.
- [ ] 5.2 `architectureTestsMinimum`: read `config/m21-quality-ratchets.properties`, raise it if the lot chooses to
      (`.claude/skills/coverage-ratchet`), never lower it. Proof: `RepositoryDocumentationCoherenceTest` and
      `ProductionIntegrityContractTest` green.
- [ ] 5.3 Pull request description: the finding, the table of 1.1, the two breaks, and the justification required for a
      change under `morpheus-architecture-tests/`.
