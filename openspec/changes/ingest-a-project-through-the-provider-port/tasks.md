# Tasks

## 1. Measure before changing

- [x] 1.1 Compare the two OpenSpec aggregators on every fixture of `experiments/m0/fixtures/` that both read
      completely: project descriptor, each content list, evidence, diagnostics, and what a publication stores from
      them. Proof: a test, and the list of differences - **this premise is not verified today**.
      *2026-10-25: `OpenSpecAggregatorEquivalenceTest` (commit `c0585fe7`, removed with the strict aggregator in
      3.5). The strict aggregator reads two fixtures completely, `openspec-basic` and `openspec-upstream-cli`; on both
      the port aggregator returns an equal `NormalizedProjectContent` (record equality: project descriptor, every
      content list, evidence in order, diagnostics). On the four others the strict one throws and the port one returns
      no content or a `FAILED` or `PARTIAL` category. No difference on a complete read.*
- [x] 1.2 For each `ReadCategoryStatus`, say what the strict aggregator does in the same situation (publishes, throws).
      Proof: the table, with the fixture that shows each row (`openspec-partial`, `openspec-unsupported-schema`,
      `openspec-state-matrix`).
      *2026-10-25, measured on the fixtures and by the CLI and HTTP sync tests:*

      | Port status | Situation | Strict aggregator | Shown by |
      |---|---|---|---|
      | `READ`, `ABSENT` | complete read | publishes | `openspec-basic`, `openspec-upstream-cli` |
      | `ABSENT` | no `openspec/changes` | throws (`no readable changes`) | `openspec-partial` |
      | `FAILED` | probe unsupported | throws (`not supported`) | `openspec-unsupported-schema`, `synthetic-basic` |
      | `FAILED` | every change rejected | throws (the file's refusal) | `openspec-state-matrix` |
      | `PARTIAL` + `INVALID_SOURCE` | one change rejected | throws (the file's refusal) | port reader tests |
      | `PARTIAL` | requirement without scenario | publishes | `openspec-partial` |
      | `PARTIAL` | delta requirement skipped, fence never closed | publishes, with warnings | `MorpheusCliTest` |
      | `UNSUPPORTED` | never reported for the published categories | - | - |

      *The premise of decision 2 held for `FAILED` only: three of the four `PARTIAL` situations publish today. The
      maintainer chose on 2026-10-25 to refuse `PARTIAL` only when its report carries `INVALID_SOURCE`.*
- [x] 1.3 List the constructions of a provider reader and the literals of a source directory under `src/main` of
      `api`, `cli` and `mcp`. Proof: the list; on 2026-10-13, three constructions and two literals, none in `mcp`.
      *2026-10-25, unchanged: `new OpenSpecProjectContentReader()` in `MorpheusProjectSyncApiService.sync`,
      `MorpheusCli.sync` and `MorpheusCli.analyzeChange`; `List.of(Path.of("openspec"))` in
      `MorpheusProjectSyncApiService` and `MorpheusCli`. `MorpheusCompositionCli` constructs the port readers.*
- [x] 1.4 Obtain the maintainer's answer on `UNSUPPORTED` (`design.md`, open question). Proof: the answer, dated.
      *2026-10-25: publishable, the category named by a warning. The strict aggregator is removed.*

## 2. Reproduce before fixing

- [x] 2.1 A test of the publication use case with the Markdown and the synthetic reader. Proof: it cannot be written
      on `HEAD` without editing `api` or `cli` - the finding, shown.
      *No use case took a reader: the sequence was written in `MorpheusProjectSyncApiService.sync` and
      `MorpheusCli.sync` around the strict aggregator. `ProviderAntiLockInTest` now publishes through
      `ProjectContentIngestion` with each reader (`oneProviderNeutralPublicationPublishesEachOfTheThreeFormats`).*
- [x] 2.2 A test per transport: a workspace whose read is partial. Proof: on `HEAD`, what is answered today (an
      exception message of the provider), recorded as the behaviour being replaced.
      *On `HEAD`, an untitled specification answered `400 BAD_REQUEST` over HTTP and exit 2 in the CLI, with the
      provider's message (`openspec/specs/broken/spec.md: OpenSpec specification has no title`); a workspace whose
      only change has no proposal answered the same way. Now: `409 STATE_CONFLICT` (the status the OpenAPI declares
      for the route) and exit 4, with the unread categories and the provider's errors
      (`aSyncWhoseChangesCannotBeReadIsRefusedByNameAndLeavesTheActiveSnapshot`, in `MorpheusApiProjectSyncIntegrationTest`
      and `MorpheusCliTest`).*
- [x] 2.3 The textual test on the directory literal and the ArchUnit rule on the non-port aggregator. Proof: both red
      on `HEAD`, naming the sites of 1.3.
      *`TransportsReadAProjectThroughTheProviderPortTest`. The dependency rule refuses any provider type that
      implements neither `SpecificationContentReader` nor `SpecificationProvider`, which covers the strict aggregator
      and any internal reader; the textual rule reads the literals from `SOURCE_DIRECTORIES`.*

## 3. The change

- [x] 3.1 The application use case that turns a `ProviderReadResult` into publishable content or a named refusal.
      Proof: unit tests on each status, with the store in memory and the synthetic provider; each refusal asserted on
      its code or message.
      *`ProjectContentIngestion` and `IncompleteProjectReadException` (an `IllegalStateException`);
      `ProjectContentIngestionTest`, ten tests, each refusal asserted on its message.*
- [x] 3.2 `sync` over HTTP, `sync` and `analyze change` in the CLI read through the port and use 3.1. Proof: 2.2 passes
      with the named refusal; the failed sync is recorded; the active snapshot is unchanged.
      *The CLI commands moved to `MorpheusProjectCli` and `MorpheusChangeCli` in the same change (task 3.3 of
      `give-each-cli-command-and-mcp-tool-a-capability-class`).*
- [x] 3.3 Resolve each difference of 1.1, or declare it. Proof: the equivalence test green, or the pull request states
      what a re-sync shows and why.
      *No difference on a complete read. Declared for the others: a workspace without changes publishes; one with a
      requirement without scenario publishes; a partially normalized read publishes one more warning
      (`Requested content was only partially normalized`); a refusal is a state failure.*
- [x] 3.4 The source directories as a constant of the provider module, passed at the composition site. Proof: 2.3
      passes.
      *`OpenSpecSpecificationProvider.SOURCE_DIRECTORIES`.*
- [x] 3.5 Remove the aggregator without a caller; its test users take the port. Proof: `dependency:analyze` at 0
      warning; the dead-code gate, if `remove-unreachable-and-unreferenced-code` has landed.
      *`OpenSpecProjectContentReader` and its test are removed. The port reader fails a group only on a failure
      attributed to one of its files and lets any other leave the read, which the strict aggregator's last test
      asserted (`aFailureThatIsNotTheFilesLeavesTheReadUnchangedAndFailsNoCategory`).*

## 4. The gates

- [x] 4.1 Rewrite the assertion of `ProjectSyncApiServiceArchitectureTest` that requires the removed construction, to
      the stricter statement. Proof: the diff, justified in the pull request; no assertion deleted without its
      replacement.
- [x] 4.2 Extend `ProviderAntiLockInTest` with the publication path on the three providers. Proof: green; red when
      the use case is made to name a provider.
      *Broken once: `ProjectContentIngestion` refusing every provider but `openspec` fails it with `planted`.*
- [x] 4.3 Break each rule once, then revert: a construction of a non-port reader planted in `api`; `Path.of` of the
      directory planted in `cli`. Proof: the two named failures.
      *`new OpenSpecChangeMetadataReader()` in `MorpheusProjectSyncApiService`: "calls constructor
      OpenSpecChangeMetadataReader.<init>() in (MorpheusProjectSyncApiService.java:33)"; `Path.of("openspec")` in
      `MorpheusPathsCli`: "a transport names a directory a provider reads ... MorpheusPathsCli.java="openspec"".*
- [x] 4.4 `AdapterCompositionRootArchitectureTest` unchanged and green: the list did not grow. Proof: the diff.
- [x] 4.5 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`). Proof: the coherence tests.
      *762 to 769: 779 architecture executions on Windows and Linux at the head of this change, ten under the
      measure, as the previous raise.*

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`; convergence gates (`PublicHttpRouteConvergenceTest`) unchanged; changed-line coverage
      gate. Proof: green on both platforms through the required checks.
- [ ] 5.2 Pull request description: the finding, why no new port, the table of 1.2, what a broken workspace now
      answers on each transport, and the governance files changed.
