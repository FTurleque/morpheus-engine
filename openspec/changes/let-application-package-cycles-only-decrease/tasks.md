# Tasks

## 1. Measure before changing

- [ ] 1.1 Re-measure with ArchUnit on the current `develop`, as ADR-0109 did: cycles reported between slices of
      `com.morpheus.application.(*)..` with `archunit.cycles.maxNumberToDetect` raised for the measurement only, and
      the slices involved. Proof: the two figures with SHA and date. **Do not copy 167 / 16 from the ADR or from this
      change**: they are of 2026-10-08.
- [ ] 1.2 Compute, from the imported classes, the slice graph, its strongly connected components and the dependencies
      inside them. Proof: the list; compare it with the import graph of 2026-10-13 (one component of 16 slices, 47
      dependencies) and explain every difference - a dependency the bytecode sees and the imports do not, or the
      reverse.
- [ ] 1.3 List every dependency from `application.store` to another application package, with the pair of types.
      Proof: the list; by imports, three interfaces and fourteen type pairs towards `policy`, `query`, `sync`.
- [ ] 1.4 List the textual assertions and rules that name `com.morpheus.application.store` or one of the three
      interfaces by path. Proof: the list, with what each would stop covering after a move.

## 2. The ratchet, before any move

- [ ] 2.1 The two tests: no unnamed dependency in a cycle; no name that no longer lies on one. Bytecode, intent "no
      dependency between application packages lies on a cycle, except the named ones" (`enforcement-choice`). Proof:
      green on the measured state with the full list of 1.2 - the gate exists before the first decrease.
- [ ] 2.2 Break each once, then revert: a planted class in a package outside every cycle depending on one inside, and
      depended on by it; a name added for a dependency that does not exist; a planted dependency outside any cycle.
      Proof: two named failures, and the third passing.
- [ ] 2.3 Prove the computation is not empty and not capped: it reports the component of 1.2 in full. Proof: an
      assertion on the component the measurement found, written from the measurement.

## 3. The first decrease

- [ ] 3.1 Move each port of 1.3 to the package of its capability; update both stores and the importers. Proof: the
      `*PersistenceParityTest` suites green, unchanged; no behaviour test edited.
- [ ] 3.2 Remove from the list every name the suite now reports as stale. Proof: the list before and after, in the
      pull request.
- [ ] 3.3 The rule that `application.store` takes part in no cycle; break it once by making a store port import a
      type of a capability that uses it. Proof: the named failure.
- [ ] 3.4 Repoint or keep each assertion of 1.4. Proof: none deleted without an equivalent or stricter one.
- [ ] 3.5 `./mvnw dependency:analyze`. Proof: 0 warning.

## 4. Record and descriptions

- [ ] 4.1 Replace ADR-0109 by a consolidated ADR: decision 3 and the measurement table - the rule, the measurements of 1.1 and 1.2 with their
      date, the list after 3.2, the residual components and what holds each. Keep the index line of
      `docs/architecture/adr/README.md` true. Proof: `AdrIndexCoherenceTest`.
- [ ] 4.2 `.claude/CLAUDE.md` and `.claude/rules/architecture.md`: replace "deferred" and its dated count by the rule
      and the name of the list. Proof: the diff; no count presented as current.
- [ ] 4.3 `docs/architecture/arc42/11-risques-dette.md` and `docs/architecture/risks/register.md` if they carry the
      deferral. Proof: `RepositoryDocumentationCoherenceTest`.
- [ ] 4.4 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`, `live-numbers`). Proof: the
      coherence tests name the destinations.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`. Proof: green on both platforms through the required checks.
- [ ] 5.2 Pull request description: the finding, why the exceptions were not moved, the two measurements, the list
      before and after, the governance files changed.
