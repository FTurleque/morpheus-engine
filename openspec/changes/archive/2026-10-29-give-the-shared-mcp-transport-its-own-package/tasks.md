# Tasks

## 0. Decide before starting

- [x] 0.1 The maintainer confirms the rename (design, first open question) and the name `com.morpheus.transport.mcp`.
      **Recorded 2026-10-28**: rename to `com.morpheus.transport.mcp`, the design's recommendation, taken by default
      when the lot was assigned in sprint 10; the maintainer confirms it in the pull request review.

## 1. Measure before changing

- [x] 1.1 List every file that names `com.morpheus.integration.mcp`, as a package (`.`) and as a path (`/` and `\`),
      over the whole repository: Java, POMs, scripts, workflows, configuration, hooks, documentation. Proof: the list;
      on 2026-10-13, thirty-three files of the module, four importers in three modules, seven architecture test files
      (nine of their lines are file paths read as text), ADR-0109, and dated records that are not edited.
      **2026-10-28**: thirty-four files of the module (twelve production, twenty-two test), four importers in three
      modules, eight architecture test files (two imports, one rule subject, nine file paths read as text), ADR-0109,
      and two more active ADRs that cite test paths of the module (ADR-0106, ADR-0128). Not edited: the dated audit
      records of 2026-10-09, the archived change `decide-adapter-composition-roots`, and the design of the change
      `resolve-both-mcp-peers-through-one-settings-resolver`, which states this finding as context. No POM, script,
      workflow, hook or configuration file names the package.
- [x] 1.2 List every ArchUnit rule whose subject or target matches the transport today, by prefix or by segment.
      Proof: the table; on 2026-10-13, five targets through `com.morpheus.integration..`, three through `"..mcp.."`,
      one subject. **2026-10-28**: the same: `LayerDependencyTest` (domain, application, api),
      `AdapterSiblingArchitectureTest` (providers, stores, through `INTEGRATIONS`), the `"..mcp.."` targets of the
      M24, M25 and M27 platform tests, and the subject of `theMcpTransportDependsOnNoOtherMorpheusPackage`.
- [x] 1.3 For each `"..mcp.."` rule, confirm by breaking it that it refuses a dependency on the transport today.
      Proof: three named failures, then reverted - this fixes what "nothing is relaxed" must preserve.
      **2026-10-28**: a probe class in `application.query.dsl`, `application.policy` and `application.reasoning`
      (test sources of the architecture module, which these importers read) referencing `McpPeerLaunch` failed
      `applicationQueryPlatformDoesNotDependOnTransportOrConcreteStores`,
      `applicationPolicyPlatformDoesNotDependOnAdaptersProvidersOrConcreteStores` and
      `applicationReasoningDoesNotDependOnAdaptersConcreteStoresOrMutationPackages`; reverted. After the move the
      segment `mcp` still matches `com.morpheus.transport.mcp` (4.1), so no explicit target is added to them.
- [x] 1.4 List the root package of every module with production sources. Proof: the table; on 2026-10-13, sixteen
      modules out of eighteen declared, three whose package is not the mechanical reading of their name.
      **2026-10-28**: unchanged; the table is `ModulePackageCorrespondenceTest.ROOTS`.
- [x] 1.5 Check that no branch or pull request touching `morpheus-mcp-transport` is open. Proof: the list.
      **2026-10-28**: no open pull request; the three sprint 10 branches in progress (ARC-08, ARC-11, QUA-11) touch
      no file of the module.

## 2. Reproduce before fixing

- [x] 2.1 The correspondence test with its table as measured in 1.4 and the transport expected under its new root.
      Mechanism: textual, over the POM and the source directories - intent "a package prefix belongs to the modules
      that declare it"; no bytecode rule sees a module. Proof: red on `HEAD` for `morpheus-mcp-transport` only.
      **2026-10-28**: `everyProductionPackageSitsUnderTheRootOfItsOwnModule` red with
      `morpheus-mcp-transport com.morpheus.integration.mcp=under no declared root`, the other checks green.
- [x] 2.2 A rule that the transport's classes reside under neither `com.morpheus.integration..` nor
      `com.morpheus.mcp..`. Mechanism: ArchUnit, on the imported classes. Proof: red on `HEAD`.
      **2026-10-28**: `theSharedMcpTransportResidesOutsideTheIntegrationsAndTheMcpAdapter` (subject: the classes
      whose source location is the module) red on its fifteen classes.

## 3. The move

- [x] 3.1 `git mv` of the thirty-three files, package declarations and imports, in a commit that changes nothing else.
      Proof: the reactor compiles; the module's own tests pass unmodified apart from their package line.
      **Adapted**: the reactor compiles only with the importers, so 3.1 and 3.2 share one rename commit (package lines,
      imports, textual paths and the subject of the transport rule, nothing else). The module's 87 tests pass.
- [x] 3.2 The importers of 1.1 and the nine textual paths. Proof: every architecture test that reads a transport file
      passes. **2026-10-28**: the architecture suite (830 runs, coverage gates excluded) and the tests of
      `morpheus-mcp`, `morpheus-integration-minos` and `morpheus-integration-nexus` pass.
- [x] 3.3 Add `com.morpheus.transport..` to each target list of 1.2 that had `com.morpheus.integration..`, and to the
      `"..mcp.."` rules only if 1.3 shows the segment pattern no longer suffices; update the subject of
      `theMcpTransportDependsOnNoOtherMorpheusPackage` and the constants of `AdapterSiblingArchitectureTest`
      (`NAMED` included, so the vacancy check covers the new pattern). Proof: 2.1 and 2.2 pass.
      **2026-10-28**: `ModulePackageCorrespondenceTest` green (six tests).

## 4. Prove that nothing was relaxed (`.claude/skills/enforcement-choice`)

- [x] 4.1 Break once each, then revert: a reference to a transport class from `api`, from a provider, from a store,
      from `application`, from `application.policy`. Proof: five named failures, each by a rule that names the
      transport's package or matches it. **2026-10-28**: `httpApiMustRemainSiblingOfCliAndMcp`,
      `providersDoNotDependOnStoresTransportsOrIntegrations`, `storesDoNotDependOnProvidersTransportsOrIntegrations`,
      `applicationMustNotDependOnAdapters` and `applicationPolicyPlatformDoesNotDependOnAdaptersProvidersOrConcreteStores`.
      The provider and store probes needed a temporary POM dependency on the transport; all reverted.
- [x] 4.2 Break the transport rule once: a reference from the transport to a class of `application`. Proof: the named
      failure. **2026-10-28**: `theMcpTransportDependsOnNoOtherMorpheusPackage` on a reference to `ProductMetadata`.
- [x] 4.3 Break the correspondence test three ways: a module without an entry, a class outside its root, an undeclared
      nesting. Proof: three named failures. **2026-10-28**: `morpheus-cli` without an entry;
      `morpheus-store-memory com.morpheus.memory=under no declared root`; `morpheus-provider-testkit=morpheus-provider-sdk`
      undeclared. A class of `morpheus-store-memory` under `com.morpheus.api` reported `under the root of morpheus-api`.
      All reverted.

## 5. Documentation

- [x] 5.1 ADR-0109 clarified: its text names the new package and why; the decision and its measures untouched.
      `docs/architecture/adr/README.md` only if its line for the ADR changes (`AdrIndexCoherenceTest`).
      **2026-10-28**: decision 3, its Don't and the compliance section; the index line is unchanged. ADR-0106 and
      ADR-0128 cite test files of the module by path: the paths follow.
- [x] 5.2 `.claude/rules/architecture.md`: the entry on the transport titled by the package the rule judges, the
      correspondence test cited where the page explains how to add a module boundary. The domain and api
      prohibition lists and the quoted rule name `transport..` too.
- [x] 5.3 `docs/developer/` and `docs/architecture/arc42/`: confirm that they name the module only. Dated records are
      left as they are. **2026-10-28**: confirmed. `docs/architecture/arc42/05-vue-blocs.md` still files the module
      in its "Intégrations" row while describing it as the shared transport; left to the maintainer.

## 6. Verification

- [x] 6.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through the
      required checks. **2026-10-28**: `dependency:analyze` green locally; `clean verify` is run once on the merged
      sprint 10 branch and by the required checks.
      **2026-10-29**: PR #583, merged as `b0368076`: `MORPHEUS CI / exact-head` green on `ubuntu-latest` and `windows-latest` at the merged head `e0e40750`; changed-line coverage 94.12 % lines / 90 % branches.
- [x] 6.2 The real-process tests of the transport and the two peer integrations (they start child JVMs by class).
      Proof: green on both platforms; no orphan JVM left behind. **2026-10-28**: green on Windows, no JVM of the
      worktree left; Linux through the required checks.
      **2026-10-29**: PR #583, merged as `b0368076`: `MORPHEUS CI / exact-head` green on `ubuntu-latest` and `windows-latest` at the merged head `e0e40750`; changed-line coverage 94.12 % lines / 90 % branches.
- [x] 6.3 Coverage evidence of both scales: the population is the same modules. Proof: `population=` unchanged in the
      aggregate evidence. `architectureTestsMinimum` never lowered. **2026-10-28**: no module added or removed, six
      architecture tests added; the evidence comes from the integration's `clean verify`.
      **2026-10-29**: PR #583, merged as `b0368076`: `MORPHEUS CI / exact-head` green on `ubuntu-latest` and `windows-latest` at the merged head `e0e40750`; changed-line coverage 94.12 % lines / 90 % branches. `architectureTestsMinimum` stays at 813, not lowered.
- [x] 6.4 Pull request description: the finding, the tables of 1.2 and 1.4, the answer to 0.1, the breaks of 4.x, the
      justification for a change under `morpheus-architecture-tests/`. **2026-10-28**: the material is in this file
      for the integrator's description.
      **2026-10-29**: written in the description of PR #583, which carries the four sprint 10 lots.
