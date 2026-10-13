# Tasks

## 1. Measure before deciding

- [ ] 1.1 Re-measure the inventory of `design.md` with ArchUnit on the current `develop`: per family, the classes of
      `com.morpheus.application` that depend on it. Proof: the table, with its SHA and date; where it differs from the
      search of 2026-10-13 (1 / 10 / 4 / 1 / 1 / 2 files, 0 for the unused families), the difference is explained.
- [ ] 1.2 For each of the ten file-access classes: its production callers, and the architecture tests that read it by
      path. Proof: the list; on 2026-10-13 three classes had no caller under any `src/main`.
- [ ] 1.3 Read the two existing textual bans (`ReasoningPlatformArchitectureTest`, `PolicyPlatformArchitectureTest`) and list what
      they refuse that a dependency rule would not. Proof: the list, kept in the ADR.

## 2. The decision

- [ ] 2.1 Put options A, B and C of `design.md` to the maintainer with the tables of 1.1 and 1.2. Proof: the choice,
      dated, and for each named class "stays" or "leaves".
- [ ] 2.2 Write the ADR from `docs/architecture/adr/template.md`, under the next free number - read the directory
      first (`live-numbers`), never a remembered total. It carries the measured table, the families and their type
      lists, the named classes with their fate, the options not taken and why. Index it in
      `docs/architecture/adr/README.md`. Proof: `AdrIndexCoherenceTest`.

## 3. The gate

- [ ] 3.1 One architecture test class, one `@Test` per intent (`enforcement-choice`): the unused families refused for
      the whole application; each confined family reachable from its named classes only; each named set exact.
      ArchUnit, intent "no class of the application depends on this technology, except the named ones". Proof: green
      on the measured state, with no product code changed.
- [ ] 3.2 Break each rule once, then revert: a planted application class depending on `java.sql`; a planted class
      calling `Files.readString`; a name added to a list without the dependency; a class using only `Path`. Proof: the
      three named failures, and the fourth passing.
- [ ] 3.3 Confirm the textual bans of `reasoning` and `policy` are untouched. Proof: their tests in the diff, absent.
- [ ] 3.4 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`; count executions, not
      `@Test` annotations). Proof: `RepositoryDocumentationCoherenceTest` and `ProductionIntegrityContractTest` name
      the destinations; all are updated in the same change.

## 4. The descriptions

- [ ] 4.1 `.claude/CLAUDE.md` and `.claude/rules/architecture.md`: the application layer as the ADR decides it, the new
      test named beside `LayerDependencyTest`, no count copied. Proof: the diff.
- [ ] 4.2 `docs/architecture/arc42/05-vue-blocs.md`, `11-risques-dette.md`, `docs/architecture/risks/register.md`: the
      same statement, with the link to the ADR. Proof: `RepositoryDocumentationCoherenceTest`.
- [ ] 4.3 If the ADR marks a class as leaving, say in it that
      `move-marked-technology-access-behind-application-ports` carries the move; if it marks none, withdraw that
      change. Proof: the ADR, and the state of the change.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`. Proof: green on both platforms through the required checks.
- [ ] 5.2 Pull request description: the finding, the measured table, the option chosen, why a governance file
      (architecture test, ratchet) changes.
