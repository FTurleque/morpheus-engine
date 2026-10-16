# Tasks

## 1. Measure before changing

- [ ] 1.1 Compare the two OpenSpec aggregators on every fixture of `experiments/m0/fixtures/` that both read
      completely: project descriptor, each content list, evidence, diagnostics, and what a publication stores from
      them. Proof: a test, and the list of differences - **this premise is not verified today**.
- [ ] 1.2 For each `ReadCategoryStatus`, say what the strict aggregator does in the same situation (publishes, throws).
      Proof: the table, with the fixture that shows each row (`openspec-partial`, `openspec-unsupported-schema`,
      `openspec-state-matrix`).
- [ ] 1.3 List the constructions of a provider reader and the literals of a source directory under `src/main` of
      `api`, `cli` and `mcp`. Proof: the list; on 2026-10-13, three constructions and two literals, none in `mcp`.
- [ ] 1.4 Obtain the maintainer's answer on `UNSUPPORTED` (`design.md`, open question). Proof: the answer, dated.

## 2. Reproduce before fixing

- [ ] 2.1 A test of the publication use case with the Markdown and the synthetic reader. Proof: it cannot be written
      on `HEAD` without editing `api` or `cli` - the finding, shown.
- [ ] 2.2 A test per transport: a workspace whose read is partial. Proof: on `HEAD`, what is answered today (an
      exception message of the provider), recorded as the behaviour being replaced.
- [ ] 2.3 The textual test on the directory literal and the ArchUnit rule on the non-port aggregator. Proof: both red
      on `HEAD`, naming the sites of 1.3.

## 3. The change

- [ ] 3.1 The application use case that turns a `ProviderReadResult` into publishable content or a named refusal.
      Proof: unit tests on each status, with the store in memory and the synthetic provider; each refusal asserted on
      its code or message.
- [ ] 3.2 `sync` over HTTP, `sync` and `analyze change` in the CLI read through the port and use 3.1. Proof: 2.2 passes
      with the named refusal; the failed sync is recorded; the active snapshot is unchanged.
- [ ] 3.3 Resolve each difference of 1.1, or declare it. Proof: the equivalence test green, or the pull request states
      what a re-sync shows and why.
- [ ] 3.4 The source directories as a constant of the provider module, passed at the composition site. Proof: 2.3
      passes.
- [ ] 3.5 Remove the aggregator without a caller; its test users take the port. Proof: `dependency:analyze` at 0
      warning; the dead-code gate, if `remove-unreachable-and-unreferenced-code` has landed.

## 4. The gates

- [ ] 4.1 Rewrite the assertion of `ProjectSyncApiServiceArchitectureTest` that requires the removed construction, to
      the stricter statement. Proof: the diff, justified in the pull request; no assertion deleted without its
      replacement.
- [ ] 4.2 Extend `ProviderAntiLockInTest` with the publication path on the three providers. Proof: green; red when
      the use case is made to name a provider.
- [ ] 4.3 Break each rule once, then revert: a construction of a non-port reader planted in `api`; `Path.of` of the
      directory planted in `cli`. Proof: the two named failures.
- [ ] 4.4 `AdapterCompositionRootArchitectureTest` unchanged and green: the list did not grow. Proof: the diff.
- [ ] 4.5 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`). Proof: the coherence tests.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`; convergence gates (`PublicHttpRouteConvergenceTest`) unchanged; changed-line coverage
      gate. Proof: green on both platforms through the required checks.
- [ ] 5.2 Pull request description: the finding, why no new port, the table of 1.2, what a broken workspace now
      answers on each transport, and the governance files changed.
