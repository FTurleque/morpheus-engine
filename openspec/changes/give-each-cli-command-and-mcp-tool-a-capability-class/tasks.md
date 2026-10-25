# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

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
      *2026-10-25: `CliCommandTranscriptTest` (62 cases) and `McpToolTranscriptTest` (50 cases), green on
      `b60a9286`. Each case records the whole answer in a transcript under `src/test/resources/.../transcripts/`: for
      the CLI the exit code, standard output and standard error of `help`, `version`, an unknown command and every
      command of 1.6, in text and in JSON, with an unknown option or an out-of-range value and an absent entity for
      each; for MCP the text and `isError` of the fourteen tools, with an absent project, a malformed identifier and
      a bound or an absent change, and the whole catalogue the server publishes (58 tools). Only what a run cannot
      reproduce is replaced: generated identifiers, clock instants, the computed age, the temporary directory and its
      separators, the product version. The traversals of `trace-requirement` / `trace_requirement` and
      `change-context` / `get_change_context` order nodes and links by generated UUIDv7, which two runs do not order
      alike within one millisecond (measured: four runs, two transcripts differed); those two JSON answers are
      compared with generated identifiers named alike and every array sorted. Four runs of each test gave identical
      transcripts. The CLI reaches its database through SQLite only and the MCP module depends on no provider, so
      neither can use the memory store; the MCP fixture is built in the test with fixed identifiers.*
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
- [ ] 3.2 The capability package of each adapter, and the classes of 1.6, one family per pull request, read-only
      families first. Proof per pull request: 2.1 and 2.2 untouched and green,
      `CliOptionParsingRefusesUnknownOptionsTest` and `MorpheusHelpInvocationsParseTest` untouched and green.
- [ ] 3.3 `sync` and `projects`, after `ingest-a-project-through-the-provider-port`.
      *Waits for the sprint 11 decision record of that change (AUD-ARC-02): these are the only commands that build a
      provider reader, and moving them first would move the direct instantiation into a class that change rewrites.
      Until then they stay in `MorpheusCli`, in `SERVED_BY_THE_DEFAULT_BRANCH`.*
- [ ] 3.4 The dispatcher rule, as an ArchUnit rule with the exact allowance of 1.3. Intent: *a class that dispatches
      depends on no use case of the application* - a dependency. Check the three obligations of `enforcement-choice`:
      the classes are in the imported set; none of the banned types is reached through a compile-time constant (or
      the text scan is added beside the rule); the rule is broken once.
- [ ] 3.5 Break once, then revert: a command implemented in the dispatch class of `cli`; a tool implemented in the
      server class of `mcp`; an allowance left unused. Proof: the three named failures.

## 4. Documentation and decision records

- [ ] 4.1 The convention in the ADR of `express-adapter-internal-roles-as-packages`, or its own ADR if that change
      was not taken (re-read `docs/architecture/adr/*-[0-9][0-9][0-9][0-9]-*.md` and `archive/` for the next free number; line in the `README.md`).
- [ ] 4.2 `.claude/rules/architecture.md` (the adapters' shape), `.claude/CLAUDE.md` ("Points d'entree cles"),
      `.claude/skills/public-capability/SKILL.md` (where a new command or tool goes), the `docs/developer/` pages of
      the CLI and MCP adapters. A file of `docs/` that moves gets its line in `docs/project/README.md`.
- [ ] 4.3 ADR-0130 names the parser families (decisions 2 and 8). If one of them changes owner, ADR-0130 is
      clarified to say so.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze` on Linux and Windows; changed-line coverage gate.
- [ ] 5.2 `scripts\validate.cmd m28 -Version <current>` and `validate-m28.sh`: the MCP client integration starts the
      real server.
- [ ] 5.3 `contracts/public-surfaces.tsv`, the OpenAPI files and the embedded help byte-identical to the base.
      Proof: `git diff --stat`.
- [ ] 5.4 The executed-test count of `morpheus-architecture-tests` before and after, read in a validation summary:
      higher. `architectureTestsMinimum` is raised by the `coverage-ratchet` procedure, in its own commit.
- [ ] 5.5 Pull request description: the finding, the tables of 1.1 to 1.6, the composition roots before and after.
