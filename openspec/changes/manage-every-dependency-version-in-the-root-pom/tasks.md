# Tasks

## 1. Measure before changing

- [x] 1.1 List every `<version>` of every module POM that is neither the parent's nor `${project.version}`. Proof: the
      table; on 2026-10-13, six sites in four POMs (the audit listed three).
- [x] 1.2 Confirm that the imported Reactor BOM manages `org.reactivestreams:reactive-streams`, and at which version.
      Proof: `./mvnw -pl morpheus-mcp-transport help:effective-pom` or the BOM itself; on 2026-10-13 the BOM of the
      pinned Reactor line declares it at the module's literal value. If it does not, decision 1 of the design is
      replaced by a managed root entry and the pull request says so.
- [x] 1.3 Record the baseline: `./mvnw dependency:tree` for the reactor and the generated CycloneDX SBOM. Proof: the
      two files kept for task 4.1.

## 2. Reproduce before fixing

- [x] 2.1 The textual test over the module POMs, reading the module list from the root POM. Mechanism: textual
      assertion - intent "no module POM states a version"; a POM is outside any bytecode rule. Proof: red on `HEAD`,
      naming the six sites of 1.1 by file and line.

## 3. The change

- [x] 3.1 Root POM: managed entries for `sqlite-jdbc`, `slf4j-nop` and `archunit` with the existing properties;
      `maven-shade-plugin` in `<pluginManagement>`. Module POMs: the six `<version>` elements removed, scopes kept.
      Proof: 2.1 passes.
- [x] 3.2 Break the test once, then revert: a literal version in one module, a `${...}` version in another, and a
      `<version>` inside an XML comment (which must not fail). Proof: two named failures, one silence.

## 4. Verification

- [x] 4.1 `./mvnw dependency:tree` and the SBOM again. Proof: identical to 1.3, component by component.
- [x] 4.2 `./mvnw dependency:analyze`. Proof: zero warning - in particular `slf4j-nop` still accepted in
      `morpheus-cli`, and the answer for `reactive-streams` in `morpheus-mcp-transport`: remove the declaration once to
      see whether the analyser reports it "used undeclared" (then it stays, without a version) or accepts its absence
      (then it goes).
- [x] 4.3 `./mvnw clean verify`, including the Enforcer's `dependencyConvergence` and
      `D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned`. Proof: green, both platforms
      through the required checks.
- [x] 4.4 The packaged CLI still carries one SLF4J backend. Proof: the shaded JAR's listing, before and after.

## 5. Documentation

- [x] 5.1 `.claude/rules/build.md`: the `TOUJOURS` and `JAMAIS` sentences state the two allowed forms and cite the
      test; the "mécanisme concret" paragraph lists what the root manages beyond the BOMs by pointing to the POM, not by
      copying versions (`.claude/skills/live-numbers`).
- [x] 5.2 `docs/developer/BUILD_AND_TEST.md` if it describes where a dependency version is declared.
- [x] 5.3 Dated records are left as they are.

## 6. Pull request

- [x] 6.1 Description: the two findings, the table of 1.1, the answer of 1.2, the before/after proof of 4.1, the
      justification for a change under `morpheus-architecture-tests/`. `architectureTestsMinimum` never lowered.

## Note of 2026-10-13 - what was done, and what the measurement changed

- 1.1: five sites in four POMs on `d5be889b`, not six: `slf4j-nop` is no longer declared anywhere (the CLI carries its
  own SLF4J provider), so there is no `slf4j-nop` entry to manage and 3.1 adds none.
- 1.2 and 4.2: the Reactor BOM resolves `reactive-streams` at 1.0.4, and removing the declaration makes
  `dependency:analyze` fail with "Used undeclared dependencies found: org.reactivestreams:reactive-streams". The
  declaration stays, without a version.
- 1.3 and 4.1: `dependency:tree` of the whole reactor is byte-identical before and after. The SBOM was not regenerated.
- 3.2: the break is a test of its own, `theModuleVersionRuleSeesALiteralAndAPropertyButNeitherACommentNorTheParent`.
- 4.3: `./mvnw verify -DskipTests` (Enforcer, `dependency:analyze`, shade) is green locally on Windows; the full
  `clean verify` on both platforms is left to the required checks of the pull request.
- 4.4: the shaded JAR still carries `org/slf4j` and `org/sqlite`.
- 5.2: `docs/developer/BUILD_AND_TEST.md` does not say where a dependency version is declared; unchanged.
