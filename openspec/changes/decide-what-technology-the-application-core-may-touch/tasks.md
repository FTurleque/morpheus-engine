# Tasks

## 1. Measure before deciding

- [x] 1.1 Re-measure the inventory of `design.md` with ArchUnit on the current `develop`: per family, the classes of
      `com.morpheus.application` that depend on it. Proof: the table, with its SHA and date; where it differs from the
      search of 2026-10-13 (1 / 10 / 4 / 1 / 1 / 2 files, 0 for the unused families), the difference is explained.
- [x] 1.2 For each of the ten file-access classes: its production callers, and the architecture tests that read it by
      path. Proof: the list; on 2026-10-13 three classes had no caller under any `src/main`.
- [x] 1.3 Read the two existing textual bans (`ReasoningPlatformArchitectureTest`, `PolicyPlatformArchitectureTest`) and list what
      they refuse that a dependency rule would not. Proof: the list, kept in the ADR.

### Measure of 2026-10-25, `develop` at `b60a9286`

**1.1** A throwaway ArchUnit rule over `com.morpheus.application` (349 sources, 36 packages, 539 classes imported,
nested classes folded into their top-level class), deleted after the run. Classes per family:

| Family | 2026-10-13 | 2026-10-25 | Classes |
|---|---|---|---|
| Outbound network | 1 | 1 | `UpdateDiscoveryService` |
| File access | 10 | 11 | `ProjectDiscoveryService`, `WorkspaceRootResolver`, `SafeWorkspaceFileResolver`, `RotatingOperationalLogFile`, `UpdateDiscoveryService`, `ExternalJarIntegrity`, `LocalWritePermissionHardener`, `LocalSourceInventoryScanner`, `LocalSourceWatcher`, `ScanWorkspaceBoundary`, `SourceFingerprint` |
| Process environment | 4 | 4 | `LocalOperationalRuntime`, `SensitiveValueRedactor`, `ProductMetadata`, `LocalWritePermissionHardener` |
| Standard streams | 1 | 2 | `OperationalRuntime`, `RotatingOperationalLogFile` |
| Threads | 1 | 1 | `UpdateDiscoveryService` |
| Wall clock | 2 | 2 direct + 4 defaults | `Instant.now()` in `LocalOperabilityService`, `OperationalRecorder`; `Clock.systemUTC()` as a constructor default in `ControlledChangeLifecycleMutationService`, `PolicyPackService`, `PortfolioRegistryService`, `SavedViewService` |
| Reflection, class loading | 0 | 2 | `CanonicalJsonSerializer` (`java.lang.reflect`), `ReasoningAdapterRegistry` (`ServiceLoader`) |
| SQL, JSON library, process execution | 0 | 0 | - |
| `Path` only (vocabulary) | 23 files | 26 classes | ports included |

Differences explained: `RotatingOperationalLogFile` (file access and `System.err`) was added on 2026-10-13
by `3ec7b76f` (the bounded operational log file, ADR-0146), and `System.err` moved from `LocalOperationalRuntime` to
`OperationalRuntime`; `WorkspacePathBoundary` no longer touches the disk, its checks went to `ScanWorkspaceBoundary`
(AUD-PRF-08, `affccd9f`). Reflection and class loading were not searched on 2026-10-13: they are in use, and the
`name-every-use-of-reflection` change names them. The `Instant.now()` of `operability` survived
`inject-the-operational-runtime`: design decision 5's "gating them belongs to `inject-the-operational-runtime`" no
longer has a carrier, so the ADR confines them like the other families.

**1.2** The table "les onze classes d'accès fichier" of ADR-0148 (lines, production callers, architecture test files
that name the class). Without a production caller: `LocalSourceWatcher`, `ProjectDiscoveryService`, and
`WorkspaceRootResolver` (called by `ProjectDiscoveryService` only) - unchanged since 2026-10-13, and not listed by
`remove-unreachable-and-unreferenced-code`. `UpdateDiscoveryService` is constructed by `MorpheusProductCli` only; the
MCP tool `check_product_update` does no I/O.

**1.3** What the textual bans refuse that a dependency rule would not: a mention (comment, string), the packages
`com.morpheus.application.store` and `com.morpheus.application.lifecycle.mutation` (reasoning), `ScriptEngine`,
`SELECT * FROM`, `executeQuery(` (policy). Kept in ADR-0148, decision 3.

**Transports and the SQLite runtime** (`restructure-sqlite-store`, identified debt): `SqliteOperationRuntime` is
imported by `ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime`; `SqliteServerMaintenance` by `MorpheusRemoteHttpServer`,
`MorpheusRemoteHttpServerBootstrap`, `MorpheusServerCli`. ADR-0148 decision 4 keeps the runtime out of the application
under every option; whether a transport holds the runtime type or only a port masking it stays ADR-0109's decision.

## 2. The decision

- [x] 2.1 Put options A, B and C of `design.md` to the maintainer with the tables of 1.1 and 1.2. Proof: the choice,
      dated, and for each named class "stays" or "leaves".

      2026-10-25: option **C**, chosen by the maintainer the same day. Fates as written in ADR-0148 decision 2: the
      transport of `UpdateDiscoveryService` (network, threads, its `file:` read) leaves; every other named class
      stays, the three without a production caller for as long as they exist.
- [x] 2.2 Write the ADR from `docs/architecture/adr/gabarit/template.md`, under the next free number - read the directory
      first (`live-numbers`), never a remembered total. It carries the measured table, the families and their type
      lists, the named classes with their fate, the options not taken and why. Index it in
      `docs/architecture/adr/README.md`. Proof: `AdrIndexCoherenceTest`.

      2026-10-25: drafted as ADR-0148, indexed in `docs/architecture/adr/README.md` and
      `docs/architecture/arc42/09-decisions.md`. **Acceptée** on 2026-10-25 with option C.

## 3. The gate

- [x] 3.1 One architecture test class, one `@Test` per intent (`enforcement-choice`): the unused families refused for
      the whole application; each confined family reachable from its named classes only; each named set exact.
      ArchUnit, intent "no class of the application depends on this technology, except the named ones". Proof: green
      on the measured state, with no product code changed.
- [x] 3.2 Break each rule once, then revert: a planted application class depending on `java.sql`; a planted class
      calling `Files.readString`; a name added to a list without the dependency; a class using only `Path`. Proof: the
      three named failures, and the fourth passing.

      2026-10-25: `ApplicationTechnologyBoundaryArchitectureTest`, four `@Test`: unused families refused, confined
      families reached only by their named classes, every named set exact, a `Path` used as a value not counted.
      Green on `b60a9286` with no product code changed. Broken once each, then reverted: a planted
      `PlantedSql` returning `java.sql.Connection` fails the first ("reaches SQL ... has return type
      <java.sql.Connection>"); a planted `PlantedAccess` calling `Files.readString`, `System.getenv` and reading
      `System.out` fails the second for file access, process environment and standard streams, and the third; the
      name `UpdateCheckResult` added to the network list fails the third alone; a planted record holding only a
      `Path` fails nothing. The textual bans of `reasoning` and `policy` are not in the diff (3.3).
- [x] 3.3 Confirm the textual bans of `reasoning` and `policy` are untouched. Proof: their tests in the diff, absent.
- [ ] 3.4 Raise `architectureTestsMinimum` for the added executions (`coverage-ratchet`; count executions, not
      `@Test` annotations). Proof: `RepositoryDocumentationCoherenceTest` and `ProductionIntegrityContractTest` name
      the destinations; all are updated in the same change.

      2026-10-25, **not raised**: the full module run on Windows (`-Djacoco.skip=true`, so `CoverageQualityGateTest`
      refuses to conclude, as it must without coverage reports) counts 768 declared executions, 4 of them from this
      gate, against `architectureTestsMinimum=585`. The repository raises presence ratchets on Surefire counts read on
      both platforms at one exact head (18/10/2026: 693 on both); a one-platform local count is not that evidence.
      Left to the next presence requalification.

## 4. The descriptions

- [x] 4.1 `.claude/CLAUDE.md` and `.claude/rules/architecture.md`: the application layer as the ADR decides it, the new
      test named beside `LayerDependencyTest`, no count copied. Proof: the diff.
- [x] 4.2 `docs/architecture/arc42/05-vue-blocs.md`, `11-risques-dette.md`, `docs/architecture/risks/register.md`: the
      same statement, with the link to the ADR. Proof: `RepositoryDocumentationCoherenceTest`.
- [x] 4.3 If the ADR marks a class as leaving, say in it that
      `move-marked-technology-access-behind-application-ports` carries the move; if it marks none, withdraw that
      change. Proof: the ADR, and the state of the change.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`. Proof: green on both platforms through the required checks.
- [ ] 5.2 Pull request description: the finding, the measured table, the option chosen, why a governance file
      (architecture test, ratchet) changes.
