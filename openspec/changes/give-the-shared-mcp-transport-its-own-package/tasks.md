# Tasks

## 0. Decide before starting

- [ ] 0.1 The maintainer confirms the rename (design, first open question) and the name `com.morpheus.transport.mcp`.
      Proof: the answer recorded in this file. If the answer is "declare the exception", tasks 3.x are replaced by one
      table entry and the specification's third and fourth requirements are withdrawn before any code is written.

## 1. Measure before changing

- [ ] 1.1 List every file that names `com.morpheus.integration.mcp`, as a package (`.`) and as a path (`/` and `\`),
      over the whole repository: Java, POMs, scripts, workflows, configuration, hooks, documentation. Proof: the list;
      on 2026-10-13, thirty-three files of the module, four importers in three modules, seven architecture test files
      (nine of their lines are file paths read as text), ADR-0109, and dated records that are not edited.
- [ ] 1.2 List every ArchUnit rule whose subject or target matches the transport today, by prefix or by segment.
      Proof: the table; on 2026-10-13, five targets through `com.morpheus.integration..`, three through `"..mcp.."`,
      one subject.
- [ ] 1.3 For each `"..mcp.."` rule, confirm by breaking it that it refuses a dependency on the transport today.
      Proof: three named failures, then reverted - this fixes what "nothing is relaxed" must preserve.
- [ ] 1.4 List the root package of every module with production sources. Proof: the table; on 2026-10-13, sixteen
      modules out of eighteen declared, three whose package is not the mechanical reading of their name.
- [ ] 1.5 Check that no branch or pull request touching `morpheus-mcp-transport` is open. Proof: the list.

## 2. Reproduce before fixing

- [ ] 2.1 The correspondence test with its table as measured in 1.4 and the transport expected under its new root.
      Mechanism: textual, over the POM and the source directories - intent "a package prefix belongs to the modules
      that declare it"; no bytecode rule sees a module. Proof: red on `HEAD` for `morpheus-mcp-transport` only.
- [ ] 2.2 A rule that the transport's classes reside under neither `com.morpheus.integration..` nor
      `com.morpheus.mcp..`. Mechanism: ArchUnit, on the imported classes. Proof: red on `HEAD`.

## 3. The move

- [ ] 3.1 `git mv` of the thirty-three files, package declarations and imports, in a commit that changes nothing else.
      Proof: the reactor compiles; the module's own tests pass unmodified apart from their package line.
- [ ] 3.2 The importers of 1.1 and the nine textual paths. Proof: every architecture test that reads a transport file
      passes.
- [ ] 3.3 Add `com.morpheus.transport..` to each target list of 1.2 that had `com.morpheus.integration..`, and to the
      `"..mcp.."` rules only if 1.3 shows the segment pattern no longer suffices; update the subject of
      `theMcpTransportDependsOnNoOtherMorpheusPackage` and the constants of `AdapterSiblingArchitectureTest`
      (`NAMED` included, so the vacancy check covers the new pattern). Proof: 2.1 and 2.2 pass.

## 4. Prove that nothing was relaxed (`.claude/skills/enforcement-choice`)

- [ ] 4.1 Break once each, then revert: a reference to a transport class from `api`, from a provider, from a store,
      from `application`, from `application.policy`. Proof: five named failures, each by a rule that names the
      transport's package or matches it.
- [ ] 4.2 Break the transport rule once: a reference from the transport to a class of `application`. Proof: the named
      failure.
- [ ] 4.3 Break the correspondence test three ways: a module without an entry, a class outside its root, an undeclared
      nesting. Proof: three named failures.

## 5. Documentation

- [ ] 5.1 ADR-0109 clarified: its text names the new package and why; the decision and its measures untouched.
      `docs/architecture/adr/README.md` only if its line for the ADR changes (`AdrIndexCoherenceTest`).
- [ ] 5.2 `.claude/rules/architecture.md`: the entry on the transport titled by the package the rule judges, the
      correspondence test cited where the page explains how to add a module boundary.
- [ ] 5.3 `docs/developer/` and `docs/architecture/arc42/`: confirm that they name the module only. Dated records are
      left as they are.

## 6. Verification

- [ ] 6.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through the
      required checks.
- [ ] 6.2 The real-process tests of the transport and the two peer integrations (they start child JVMs by class).
      Proof: green on both platforms; no orphan JVM left behind.
- [ ] 6.3 Coverage evidence of both scales: the population is the same modules. Proof: `population=` unchanged in the
      aggregate evidence. `architectureTestsMinimum` never lowered.
- [ ] 6.4 Pull request description: the finding, the tables of 1.2 and 1.4, the answer to 0.1, the breaks of 4.x, the
      justification for a change under `morpheus-architecture-tests/`.
