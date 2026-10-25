# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

**Order.** The proposal places this lot after `project-shared-domain-types-to-one-public-shape` and
`share-one-argument-base-across-cli-subcommands`. The lot runs before both, by the maintainer's decision of 2026-10-25:
the extraction does not wait for sprint 9. What those two changes replace moves once and is not copied: the
projections of `MorpheusMcpToolService` go into one collaborator of the MCP capability package, the parser
`CommandOptions` and the error mapping of `MorpheusCli` into collaborators of the CLI capability package. Each change
then replaces one class in place, and its `tasks.md` names that class.

## 1. Measure before changing

- [x] 1.1 Re-count on the lot's base: lines, imports, dispatched commands and private records of `MorpheusCli`;
      lines, dispatched tools and projection methods of `MorpheusMcpToolService`; capability classes of each
      adapter. Proof: the table; on 2026-10-13 at `8b26d3d7`, 908 lines / 67 imports / 13 commands besides `help` and
      `version` / 14 records; 412 lines / 14 tools; 14 and 12 capability classes.
      *2026-10-25, `develop` at `b60a9286`:*

      | Class | Lines | Imports | Dispatched | Private records | Projection methods |
      |---|---|---|---|---|---|
      | `MorpheusCli` | 922 | 69 | 13 commands besides `help` and `version` | 14 | - |
      | `MorpheusMcpToolService` | 415 | 37 | 14 tools | - | 10 (eight shared shapes, `lifecycleFacts`, `finding`) |

      *Capability classes: 14 `Morpheus*Cli` beside `MorpheusCli` and 12 in `mcp` (11 `*McpTools`,
      `MorpheusPolicyMcpManagementTools`), as on 2026-10-13. `MorpheusMain.run` asks 13 of the 14 by `handles`;
      `MorpheusReasoningCli` is reached through `MorpheusProductCli`, and its own `handles` has no caller. The
      `version` arm of `MorpheusCli.dispatch` is unreachable from the launcher: `MorpheusProductCli` claims `version`
      and `--version` first, so `MorpheusCli` serves `help` and the thirteen commands.*
- [x] 1.2 For each command and each tool, list the tests that pin its text output, its JSON output and one refusal.
      Proof: the table, with the gaps. `MorpheusCliTest` held 17 tests on 2026-10-13.
      *2026-10-25: `MorpheusCliTest` holds 18 tests. Before this lot no test pinned a whole output: every check was a
      `contains` on a line or a key.*

      | Command | Text | JSON | Refusal with its exit code and message |
      |---|---|---|---|
      | `paths` | - | keys (`MorpheusCliTest`) | blank `--data-dir` (`MorpheusCliTest`) |
      | `projects list` / `add` | `projectId=` line | `projects list` contains the id | - |
      | `sync` | `mode`, `published`, diagnostics lines | diagnostic keys | blank `--revision`, invalid content |
      | `sync-status` | - | `state`, `lastSuccessfulMode` | - |
      | `requirements find` | id lines | `totalMatches`, a key | Unicode-space `--query` |
      | `changes list` / `get` | id lines / - | - / - | absent project, exit 3 |
      | `constraints list`, `decisions list`, `tasks list` | - | - | - |
      | `trace-requirement` | - | - | `--depth 0`, exit 2 without message |
      | `change-context` | - | operation name | - |
      | `analyze-change` | warning and truncation lines | truncation reason | - |
      | `quality` | coverage fields | metric keys | - |

      *Beside it, `BlankOptionValueRefusalTest`, `DuplicateOptionRefusalTest` and
      `NothingIsCreatedBeforeTheOptionsAreAcceptedTest` generate the option refusals of every command. For MCP,
      `MorpheusMcpToolServiceTest` (2 tests) calls the fourteen tools and checks a `contains` each,
      `McpFailureContractTest` the refusal shape of every served tool, `GrowingCollectionToolsPagingTest` the pages.
      The gap is the same on both sides: no output and no refusal message pinned whole.*
- [x] 1.3 List the application types each dispatch class uses for dispatch itself (failure types, product metadata)
      and those it uses to serve a command. Proof: the two lists, measured with ArchUnit on the compiled classes,
      not by reading imports.
      *2026-10-25, measured on the compiled classes (`getDirectDependenciesFromSelf`, nested classes included):*

      | Dispatch class | `com.morpheus.application..` types it needs to dispatch | Types it uses to serve |
      |---|---|---|
      | `MorpheusMain` | `operability.LocalOperationalRuntime`, `OperationalLogFileException`, `OperationalLogMode`, `OperationalRuntime` | none |
      | `MorpheusCli` | `operability.LocalOperationalRuntime`, `OperationalRecorder`, `OperationalRuntime`; `product.ProductMetadata`; `store.KnowledgeStoreException`; `query.compact.CanonicalJsonSerializer` (the JSON error line) | 49 others: the services, results and pages of `analysis`, `identity`, `ingestion`, `quality`, `query`, `store`, `sync`, `traceability`, and `provider.openspec.OpenSpecProjectContentReader` |
      | `MorpheusMcpServer` | `context.DisabledTechnicalContextProvider`, `TechnicalContextProvider`; `lifecycle.mutation.ChangeWriteCapabilityObservation`, `ChangeWriteCapabilityResolver`; `operability.OperationalRecorder`; `product.ProductMetadata`; `reference.ExternalReferenceResolverRegistry`; `store.KnowledgeStoreException` | `snapshot.RuntimeSnapshotRecovery`, the start-up recovery, which is a use case |
      | `MorpheusMcpToolService` | - | 31 application types; it is the monolith, not a dispatcher |

      *`MorpheusMain` also reads `query.dsl.QueryBudgets.MAX_EXPORT_ROWS` for its help text. It is a compile-time
      constant that `javac` inlines: no dependency is left in the bytecode, so the rule of 3.4 needs a text scan
      beside it (obligation 2 of `enforcement-choice`).*
- [x] 1.4 Say, for each field of `MorpheusMcpRuntime` and `CliRuntime`, the concrete type it exposes and the
      application port that type implements. Proof: the table. State whether
      `own-the-sqlite-connection-policy-in-one-place` has already changed it.
      *2026-10-25: it has. Neither runtime has a store field any more: each holds one `SqliteOperationRuntime` and its
      accessors answer application types, so no concrete type is exposed.*

      | Accessor | `CliRuntime` | `MorpheusMcpRuntime` |
      |---|---|---|
      | `snapshots()` | `store.SpecificationKnowledgeStore` | same |
      | `requirements()` | `store.VersionedRequirementStore` | same |
      | `content()` | `store.SnapshotBusinessContentStore` | same |
      | `traceability()` | `store.TraceabilityStore` | same |
      | `externalReferences()` | `store.ExternalReferenceStore` | same |
      | `identities()` | `identity.EntityIdentityStore` | - |
      | `syncState()` | `store.SyncStateStore` | same |
      | `lifecycleMutations()` | `lifecycle.mutation.ChangeLifecycleMutationStore` | same |
      | `compositions()` | `composition.CompositionStateStore` | same |
      | `portfolios()` | `store.PortfolioStore` | same |
      | `policyPacks()` | - | `store.PolicyPackStore` |
      | `queries()`, `views()`, `exports()` | `query.dsl.QueryExecutionService`, `query.saved.SavedViewService`, `query.export.QueryExportService` | same |
      | `registry()`, `evaluation()` | `policy.PolicyPackService`, `policy.PolicyEvaluationService` | same |

      *`COMPOSITION_ROOTS` of `AdapterCompositionRootArchitectureTest` names `MorpheusMcpRuntime` and not
      `MorpheusMcpToolService`.*
- [x] 1.5 Measure the precedence of the `handles(args)` chain of `MorpheusMain`: is any argument vector claimed by
      two classes today? Proof: a test that enumerates the first words of the help and counts the claimants.
      *2026-10-25: none. `CliCommandDispatchIsExhaustiveTest` reads the chain from the bytecode of
      `MorpheusMain.run` (13 classes), offers each of the 84 paths of `CliCommandPaths` (the help is compared with
      that list by `CliCommandPathsTest`) to every class of the chain, and counts the claimants: no path has two.
      `constraints list` and `constraints evaluate` share their first word; `MorpheusConstraintSemanticsCli` claims
      the second only, so the order of the chain decides nothing today.*
- [x] 1.6 Propose the grouping of the thirteen commands and fourteen tools into capability classes from the families
      of `contracts/public-surfaces.tsv`. Proof: the table, in the pull request of 2.1.
      *2026-10-25, proposed:*

      | Manifest family | CLI | MCP |
      |---|---|---|
      | `requirement.*` | `requirements find`, `trace-requirement` | `find_requirements`, `trace_requirement` |
      | `change.*` | `changes list`, `changes get`, `constraints list`, `decisions list`, `tasks list`, `change-context`, `analyze-change` | `get_change`, `list_changes`, `get_constraints`, `get_design_decisions`, `get_implementation_tasks`, `get_change_context`, `get_change_status`, `get_blocking_conditions` |
      | `specification.*` | - | `get_current_specification`, `get_specification_context` |
      | `acceptance.criteria` | (`acceptance-criteria list`, served already) | `get_acceptance_criteria` |
      | `sync.status` | `sync-status` | `get_sync_status` |
      | `quality.report` | `quality` | - |
      | no row (`paths`) | `paths` | - |
      | `project.*` | `projects list`, `projects add`, `sync` (3.3) | - |

      *`help` stays the dispatcher's own answer, with `unknown command`. The eight projections the MCP families share
      move once, into one collaborator of the capability package, until
      `project-shared-domain-types-to-one-public-shape` replaces them.*

## 2. Characterise before extracting

- [x] 2.1 The missing tests of 1.2, on `experiments/m0/fixtures/`, with `morpheus-store-memory` or
      `morpheus-provider-synthetic` wherever the command allows it. Their own pull request, green on the base, no
      production line changed. A refusal is asserted by `assertThrows` or exit code **and** by its message.
      *2026-10-25: `CliCommandTranscriptTest` (69 cases) and `McpToolTranscriptTest` (56 cases), green on
      `b60a9286` on Windows and Linux. Each case records the whole answer in a transcript under
      `src/test/resources/.../transcripts/`. For the CLI: the exit code, standard output and standard error of `help`,
      `version`, an unknown command and every command of 1.6 in text and in JSON; an unknown option or an out-of-range
      value for each; an absent project for each command that takes one, an absent change for `changes get`,
      `constraints list`, `decisions list`, `tasks list` and `change-context`, an absent requirement for
      `trace-requirement`. For MCP: the text and `isError` of the fourteen tools, each with an absent project and a
      malformed identifier, every tool that takes a change with an absent change, an absent requirement and
      specification, the page and depth bounds, and the whole catalogue the server publishes (58 tools). Only what a
      run cannot reproduce is replaced: generated identifiers, clock instants, the computed age, the temporary
      directory and its separators, the product version. The answers that list entities in the order of their
      generated UUIDv7 (`requirements find`, `constraints list`, `decisions list`, `tasks list`, and the traversals of
      `trace-requirement` / `trace_requirement` and `change-context` / `get_change_context`) are compared with generated
      identifiers named alike and the entities as a set: item lines of a text listing, arrays of objects of a JSON
      answer; arrays of values keep their order. Two identifiers generated in one millisecond are in no fixed order,
      and the traversal transcripts were seen to differ between runs. Three runs of each test give identical
      transcripts. The CLI reaches its database through SQLite only and the MCP module depends on no provider, so
      neither can use the memory store; the MCP fixture is built in the test with fixed identifiers, and its sync
      state is the `UNKNOWN` one (no synchronisation is recorded), where the CLI cases record a `FRESH` one.*

      *Two answers are recorded as they are, not as they should be: `paths` accepts and ignores an unknown option
      (`paths --bogus x`, exit 0), and `constraints list`, `decisions list` and `tasks list` answer an absent change with
      an empty page and exit 0, where the MCP tools refuse it (`change not found`). Changing either is a visible change,
      outside this extraction.*
- [x] 2.2 The exhaustiveness test of the dispatch against the help and against the tool catalogue. Proof: green on
      the base; red when a help line is added by hand without a claimant; revert.
      *2026-10-25: `CliCommandDispatchIsExhaustiveTest` (architecture suite) and `ServedToolsAreClaimedOnceTest`
      (`morpheus-mcp`, which reads the owner of each served tool from the class that declares its handler's lambda).
      Each names what its dispatch class still serves itself (`SERVED_BY_THE_DEFAULT_BRANCH`, 16 paths;
      `SERVED_BY_THE_SERVER_CLASS`, 14 tools): a list that only shrinks, and the only edit these tests receive from
      3.2. Broken once each, then reverted:*

      ```text
      frobnicate added to CliCommandPaths and to the help:
        the declared paths that no capability class claims must be exactly those MorpheusCli serves ...
        but was: <[analyze-change, ..., frobnicate, help, ...]>
      frobnicate_tool added to MorpheusMcpToolCatalog:
        the tools whose handler the server class declares must be exactly those it still serves itself ...
        but was: <[find_requirements, frobnicate_tool, ...]>
      MorpheusQueryCli.handles also claiming portfolio:
        command paths that two classes of the dispatch claim ==> expected: <{}> but was:
        <{portfolio add-project=[MorpheusPortfolioCli, MorpheusQueryCli], ...}>
      MorpheusProductMcpTools wired twice:
        tool names that two specifications publish, with their owners ==> expected: <{}> but was:
        <{check_product_update=[MorpheusProductMcpTools, MorpheusProductMcpTools], ...}>
      ```

## 3. The change

- [x] 3.1 The runtime hands out application ports where 1.4 found concrete types read by a future capability class.
      Proof: the tests of 2.1 pass unmodified; if a root no longer reaches a concrete store, its name leaves
      `COMPOSITION_ROOTS` in the same pull request, with ADR-0109 clarified to match.
      *2026-10-25: nothing to do. 1.4 found no concrete type: `own-the-sqlite-connection-policy-in-one-place` made
      both runtimes answer ports, and `MorpheusMcpToolService` had already left `COMPOSITION_ROOTS`.*
- [x] 3.2 The capability package of each adapter, and the classes of 1.6, one family per pull request, read-only
      families first. Proof per pull request: 2.1 and 2.2 untouched and green,
      `CliOptionParsingRefusesUnknownOptionsTest` and `MorpheusHelpInvocationsParseTest` untouched and green.
      *2026-10-25: the packages first (`com.morpheus.cli` / `.launch` / `.command`; `com.morpheus.mcp.server` above
      `com.morpheus.mcp`, so that `MorpheusMain` keeps the class name the distribution launches and
      `MorpheusMcpRuntime` the name ADR-0109 lists), then the families, read-only first:*

      | Pull request | CLI | MCP |
      |---|---|---|
      | packages | 14 capability classes into `command`, the server wiring into `launch` | the server into `server` |
      | `requirement.*` | `MorpheusRequirementCli` | `MorpheusRequirementMcpTools` |
      | `change.*` | `MorpheusChangeCli` | `MorpheusChangeMcpTools` |
      | `specification.*`, `acceptance.criteria`, `sync.status` | `MorpheusSyncStatusCli` | `MorpheusSpecificationMcpTools`, `MorpheusAcceptanceCriteriaMcpTools`, `MorpheusSyncStatusMcpTools`; `MorpheusMcpToolService` goes |
      | `quality.report`, `paths` | `MorpheusQualityCli`, `MorpheusPathsCli` | - |

      *The small families of the last two rows share a pull request each: one family per pull request would have
      cost a full two-platform verification for a single tool. What the families share moved once: `CommandOptions`,
      `CliCommandRun` and `CliFailure` in `command`, `McpContentPayloads` and `McpCatalogTool` in `com.morpheus.mcp`.
      In every pull request `CliCommandTranscriptTest` and `McpToolTranscriptTest` pass with their transcripts
      unchanged, and `CliOptionParsingRefusesUnknownOptionsTest` and `MorpheusHelpInvocationsParseTest` are not
      edited. The edits the tests of 2.1 and 2.2 receive are: the import of `MorpheusMcpServer`, which moved package
      (2.1); the names leaving the two shrinking lists, and, once `SERVED_BY_THE_SERVER_CLASS` is empty, its
      replacement by the stricter assertion that no class of the server's package owns a tool (2.2).*

- [ ] 3.3 `sync` and `projects`, after `ingest-a-project-through-the-provider-port`.
      *Waits for the sprint 11 decision record of that change (AUD-ARC-02): these are the only commands that build a
      provider reader, and moving them first would move the direct instantiation into a class that change rewrites.
      Until then they stay in `MorpheusCli`, in `SERVED_BY_THE_DEFAULT_BRANCH`.*
      *`analyze-change` waits with them: it builds the OpenSpec reader itself (`MorpheusCli.analyzeChange`), and
      `ingest-a-project-through-the-provider-port` rewires it too (its proposal lists `sync` and `analyze change`). The
      design names `sync` and `projects` as the only commands that build a provider reader; `projects` builds none,
      `analyze-change` does. The three stay in `MorpheusCli` and in `MORPHEUS_CLI_DEBT`.*

- [x] 3.4 The dispatcher rule, as an ArchUnit rule with the exact allowance of 1.3. Intent: *a class that dispatches
      depends on no use case of the application* - a dependency. Check the three obligations of `enforcement-choice`:
      the classes are in the imported set; none of the banned types is reached through a compile-time constant (or
      the text scan is added beside the rule); the rule is broken once.
      *2026-10-25: `DispatchersHoldNoUseCaseArchitectureTest`. Its classes are those of `com.morpheus.cli` and
      `com.morpheus.mcp.server`, in the imported set (the rule refuses fewer than four). Its allowance is the exact set
      of 1.3, minus the start-up recovery, which moved to `MorpheusMcpRuntime.recoverInterruptedPublications`. The
      source of each dispatch class is scanned for every qualified application type it names, so the inlined
      `QueryBudgets.MAX_EXPORT_ROWS` is held. `MorpheusCli` carries the named debt of the three commands of 3.3.*

- [x] 3.5 Break once, then revert: a command implemented in the dispatch class of `cli`; a tool implemented in the
      server class of `mcp`; an allowance left unused. Proof: the three named failures.
      *2026-10-25, all four at once, then reverted:*

      ```text
      a command in MorpheusMain:  Method <com.morpheus.cli.MorpheusMain.requirementsBreak(...)> calls constructor
                                  <com.morpheus.application.query.RequirementQueryService.<init>(...)>
      a tool in the server:       Method <com.morpheus.mcp.server.MorpheusMcpServer.syncBreak(...)> calls constructor
                                  <com.morpheus.application.sync.SyncFreshnessService.<init>(...)>
      a constant by its name:     a dispatch class names an application type outside the allowance ...
                                  com.morpheus.mcp.server.MorpheusMcpServer=[com.morpheus.application.query.PageRequest, ...]
                                  (no bytecode violation: javac inlines PageRequest.MAX_LIMIT)
      an unused allowance:        names of the allowance no dispatch class uses: remove them ==> expected: <[]> but
                                  was: <[com.morpheus.application.query.dsl.QueryExecutionService]>
      ```

## 4. Documentation and decision records

- [x] 4.1 The convention in the ADR of `express-adapter-internal-roles-as-packages`, or its own ADR if that change
      was not taken (re-read `docs/architecture/adr/*-[0-9][0-9][0-9][0-9]-*.md` and `archive/` for the next free number; line in the `README.md`).
      *2026-10-25: ADR-0149 (`ARCH-0149-...`, status Proposée), its own record: ADR-0147 leaves `morpheus-cli` and
      `morpheus-mcp` to their own decision, and `.claude/rules/adr.md` forbids changing an accepted record's meaning
      in place. The highest number was 0148 (accepted the same day). Index line added.*

- [x] 4.2 `.claude/rules/architecture.md` (the adapters' shape), `.claude/CLAUDE.md` ("Points d'entree cles"),
      `.claude/skills/public-capability/SKILL.md` (where a new command or tool goes), the `docs/developer/` pages of
      the CLI and MCP adapters. A file of `docs/` that moves gets its line in `docs/project/README.md`.
      *2026-10-25: done in the pull request of ADR-0149, with `docs/architecture/arc42/05-vue-blocs.md` and
      `09-decisions.md`. `docs/developer/MCP.md` is the MCP adapter page; the CLI has no developer page of its own
      (`docs/user/CLI.md` speaks of the installed product and names no class). No file of `docs/` moved.*

- [x] 4.3 ADR-0130 names the parser families (decisions 2 and 8). If one of them changes owner, ADR-0130 is
      clarified to say so.
      *2026-10-25: `CommandOptions` left `MorpheusCli` for `com.morpheus.cli.command`; ADR-0130 names it by its
      class, in the pull request of the requirement family.*

## 5. Verification

- [x] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze` on Linux and Windows; changed-line coverage gate.
      *2026-10-25: `clean verify` green on Windows and on Linux (WSL clone) at the head of each pull request of the
      lot, and the changed-line gate passed on each (cumulative from `develop`: 93.8 to 96.1 % of changed lines, 82.4
      to 83.2 % of changed branches). `dependency:analyze` runs inside `verify` (`failOnWarning`): no module gained or
      lost a dependency.*

- [x] 5.2 `scripts\validate.cmd m28 -Version <current>` and `validate-m28.sh`: the MCP client integration starts the
      real server.
      *2026-10-25, on the pull request of the packages (the server changes package there): `validate-m28` green on
      Windows and Linux, with `m23` to `m27` and `r2`, the validators whose JAR entries that pull request edits.*

- [x] 5.3 `contracts/public-surfaces.tsv`, the OpenAPI files and the embedded help byte-identical to the base.
      Proof: `git diff --stat`.
      *2026-10-25: `git diff --stat b60a9286 -- contracts/public-surfaces.tsv docs/developer/reference/openapi` is empty
      at the head of the lot; the help and the catalogue are held by the `help` and `catalogue` transcripts, unchanged
      since the pull request of 2.1.*

- [x] 5.4 The executed-test count of `morpheus-architecture-tests` before and after, read in a validation summary:
      higher. `architectureTestsMinimum` is raised by the `coverage-ratchet` procedure, in its own commit.
      *2026-10-25, the same on Windows and Linux: 764 executions on `b60a9286`, 765 after 2.1 and 2.2, 769 after the
      packages (two cycle rules, two entry-type checks), 772 after the dispatcher rule. `architectureTestsMinimum`
      rises from 585 to 762, ten under the measure, in its own commit.*

- [x] 5.5 Pull request description: the finding, the tables of 1.1 to 1.6, the composition roots before and after.
      *2026-10-25: each pull request of the lot carries its part of 1.1 to 1.6; the composition roots are the same
      five before and after (`COMPOSITION_ROOTS` is not edited), and `MorpheusMcpToolService`, which was none, is gone.*
