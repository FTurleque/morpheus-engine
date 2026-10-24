# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change. Lot A (tasks 1 to 5) does not wait for sprint 11; lot B (task 6) does.

## 1. Measure before changing

- [x] 1.1 Re-count on the lot's base: source files and packages of `morpheus-api`, public and package-private
      top-level types, the types of `api` another module imports. Proof: the table; on 2026-10-13 at `8b26d3d7`,
      81 files in one package, 13 public and 68 package-private, 6 types used by `morpheus-cli`.
- [x] 1.2 Inventory every place that pins an `api` class by where it lives: literal source paths in
      `morpheus-architecture-tests`, exact-package and fully-qualified references, `scripts/validate-*` in both
      spellings (`com/morpheus/api/` and `com\morpheus\api\`), the regular expressions of `.claude/hooks/`,
      `docs/`, `.claude/rules/`, `.claude/skills/`. Proof: the list by file; on 2026-10-13, 117 literal paths and 43
      lines in the gate module, six validator pairs.
- [x] 1.3 Inventory every list of production class names in the gate module and say for each: role, debt counter
      under which ADR, or table of facts. Proof: the table. Expected at least `COMPOSITION_ROOTS`, the three
      `ROUTERS_*`, the four maps of `HttpPathReadingArchitectureTest`.
- [x] 1.4 Measure the candidate layout without moving a file: a throwaway ArchUnit rule that assigns each class to a
      candidate package by a predicate and reports the cycles (`archunit.cycles.maxNumberToDetect` raised for the
      run only, as ADR-0109 did). Count separately the readings of a compile-time constant across candidates, which
      ArchUnit does not see. Proof: the cycles, the constant readings, and the extractions each would need. If no
      layering exists short of dismantling `MorpheusHttpServer`, stop and report: see `design.md`, risks.
- [x] 1.5 Take the executed-test count of `morpheus-architecture-tests` from a validation summary of the base
      (`live-numbers`). Proof: the figure and the run it comes from.

### Measure of 2026-10-24, `develop` at `0b90cdcf`

**1.1** `morpheus-api`: 84 source files, 10 014 lines, one package. 13 public and 71 package-private top-level
types. `morpheus-cli` main uses 6: `AllowedWorkspaceRoots` (`of`), `LoopbackHostPolicy` (`requireLoopback`),
`MorpheusHttpServer` (`start`, constants `DEFAULT_HOST`, `DEFAULT_PORT`), `MorpheusRemoteHttpServer` (`start`,
constants `MAX_CONCURRENT_REQUESTS`, `DEFAULT_MAX_CONCURRENT_REQUESTS`), `MorpheusRemoteIdentityFile` (seven methods
and the nested `GeneratedCredential`, `LegacyMigration`), `MorpheusRemoteRole` (`valueOf`). Three of those readings
are inlined constants.

**1.2** In `morpheus-architecture-tests`: 121 literal source paths of `api` in 46 files, 16 of `cli` in 9, 9 of `mcp`
in 8 (146 in all), 4 literal test paths of `api`, 39 non-import lines that name the package or a fully-qualified class,
one test declared in `com.morpheus.api` (`PublicHttpRouteConvergenceTest`), and two compiled references to
`com.morpheus.api.MorpheusRemoteIdentityFile` in `RemoteIdentityDecompositionContractTest`, one of them through
`Class.forName("com.morpheus.api." + ...)`. `morpheus-api` has 108 test sources in
`com.morpheus.api`. Validator pairs citing `com/morpheus/api/`: `validate-m23` to `m27` and `validate-r2` (six pairs).
`.claude/hooks/`: none. `.claude/`: `rules/security.md`, `agents/security-reviewer.md`, `commands/security-audit.md`.
Active ADRs citing an `api` source path: 23; `docs/developer/ASSISTED_REASONING.md`.

**1.3** Lists of production class names in the gate module that concern `api`:

| List | Entries | Kind |
|---|---|---|
| `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` | 5, **fully-qualified** (4 `api`, 1 `mcp`) | debt counter, ADR-0109 |
| `HttpRoutesTransportBoundaryArchitectureTest.ROUTERS_WITHOUT_A_BODY` / `_THROUGH_THE_SHARED_DECODER` / `_REGISTERING_THEIR_OWN_CONTEXT` | 8 / 5 / 4 | role |
| `HttpPathReadingArchitectureTest.RAW_PATH_READERS` / `FORM_DECODERS` / `SLASH_SPLITTERS` / `OTHER_GET_PATH_CALLERS` | 9 / 2 / 2 / 1, by file name | table of facts |

`COMPOSITION_ROOTS` names its roots by package: every move of a root edits it.

**1.4** Candidate slices, by predicate: routers (`*HttpRoutes`, 17), services (`*ApiService`, the request records,
`IntegrationStatusViews`, 23), transport plumbing (15), local server and its protections (10), remote server and its
identity model (16), composition (`ApiRuntime` and the two bootstraps). Compiled edges against the expected layering
(composition > remote > local > routes > services > plumbing):

| Back-edge | Pairs | What closes it |
|---|---|---|
| services -> `ApiRuntime` | 16 | `ApiRuntime` sits below the services that use it, not above them |
| router -> `ApiRuntime` | 1 | `MorpheusPolicyManagementHttpRoutes` |
| routers -> `MorpheusHttpServer` | 6 | nested `ApiSuccess`, `ApiError`, `ApiErrorEnvelope` (4 routers), `ProjectRegistrationRequest`, `SyncRequest` (1 each) |
| server -> its bootstrap | 2 | `MorpheusHttpServer.start` and `MorpheusRemoteHttpServer.start` delegate to their bootstraps, which build the server |
| services -> `AllowedWorkspaceRoots` | 3 | a value type used by `cli`, three services and both servers |
| plumbing -> `TimedBoundedInputReader` | 1 | `HttpRequestBodyReader`: the two `TimedBounded*` are plumbing, not remote |
| remote model -> `MorpheusRemoteIdentityFile` | 4 | nested `Identity`, `AuditRecord`, `Mutation`, `GeneratedCredential` |
| `RemoteAccessJournal` -> `MorpheusRemoteHttpServer` | 1 | static `usesPrivilegedConcurrency` |

Readings of a compile-time constant across slices: 33. Those against the layering are all `MorpheusHttpServer.API_PREFIX`,
read by four routers, `MorpheusHttpPathParser` and `MorpheusHttpRouteTable`.

A layering exists without dismantling `MorpheusHttpServer`. It needs: the five nested records and `API_PREFIX` out of
`MorpheusHttpServer`; the four nested types of `MorpheusRemoteIdentityFile` beside it; each bootstrap in the package of
its server; the `TimedBounded*` in the plumbing; `ApiRuntime` with the services. Two facts do not fit the lot as
written: the services and `ApiRuntime` have to leave the parent package for the routers to stop forming a cycle with
the servers, and the six types `morpheus-cli` uses sit at two levels (the servers and the identity file at the top,
`AllowedWorkspaceRoots`, `LoopbackHostPolicy`, `MorpheusRemoteRole` at the bottom), so no single entry package is
free of cycles.

**1.5** `morpheus-architecture-tests`: 754 executed tests (`architectureTests=754`), from
`validation-output/m21/validation-summary.txt` of the `m21-integrity-Linux` artefact of run `40122307479` (PR #543,
same tree as `0b90cdcf`).

## 2. Make the gates independent of the layout

- [x] 2.1 The source locator of the gate module, with its own tests: found, absent, ambiguous. Intent, in one
      sentence: *a gate names the class it reads, not the folder it is in* - textual by nature, the gate reads a
      `.java` file.
- [x] 2.2 Route every literal path of 1.2 through it. No class of production moves. Proof: the gate module green,
      and a class of `api` moved by hand to a scratch sub-package fails only what names its package; revert.

      Done 2026-10-24: `ProductionSources` (`mainSource`, `testSource`, `relativeMainSource`, `readMain`) and
      `ProductionSourcesTest`. The 150 literal source paths of `api`, `cli` and `mcp` and the eight gates that read an
      `api` package directory read the whole source tree of the module, keyed by file name where a table names files; the
      31 private root locators and 38 locals the rewrite left unused are removed.
      `JsonMediaType` moved by hand to `com.morpheus.api.scratch` failed no gate of the module (the per-module
      coverage gate aside, which needs `clean verify`); on the base, `D2RepositoryHardeningArchitectureTest` read
      it by its folder.

## 3. The entry rule, before any visibility is widened

- [x] 3.1 The ArchUnit rule (*no class outside the adapter depends on a type of the adapter other than the six entry
      types* - a dependency, so a rule) and the scan beside it (*no source outside the adapter names such a type* - a
      mention, and the only form that sees an inlined constant). Both, per `.claude/rules/architecture.md` (in doubt, keep both).
- [x] 3.2 Break each once and revert: a dependency from `morpheus-cli` on a planted public class of a sub-package
      fails the rule; a read of a planted public constant of a non-entry type passes the rule and fails the scan. Proof: the two named
      failures, recorded in the ADR.

      Done 2026-10-24: `ApiEntryTypesArchitectureTest`, the closed list `ENTRY_TYPES` and a guard that each entry
      type is still reached and named. Broken once with a public `com.morpheus.api.planted.PlantedPublic`: the rule
      refused `com.morpheus.cli.PlantedUse` ("calls method <com.morpheus.api.planted.PlantedPublic.use()>") and did
      not see `PlantedConstantRead`, which read only `PlantedPublic.CONSTANT`; the scan named both files. Reverted.

## 4. Move the transport roles, one role per pull request

- [x] 4.1 The remote server and its identity model. Tests of `morpheus-api` move with their subjects;
      `validate-m26.ps1` and `validate-m26.sh` change together (`.ps1` stays ASCII). Proof: `clean verify` on both
      platforms; `scripts\validate.cmd m26 -Version <current>` and its `.sh`.
      Done 2026-10-24: 15 classes to `com.morpheus.api.remote`, `MorpheusRemoteHttpServerBootstrap` among them, and
      32 tests with them (`RemoteHttpTestSupport` included). No class of `com.morpheus.api` names a remote class, so
      the package depends on the parent and never the reverse; the remote model reads the types nested in
      `MorpheusRemoteIdentityFile` inside its own package, so no extraction was needed for this role. Widened for the
      compiler, main sources: 11 types (`ApiFailure`, `BoundaryFailureMessage`, `DatabaseCallWorkers`,
      `DatabaseWorkOffloadingHttpServer`, `HttpQueryBudget`, `MorpheusHttpPathParser`, `MorpheusHttpRouteTable`,
      `MorpheusInternalCapability`, `TimedBoundedInputReader`, `TimedBoundedResponseWriter`,
      `UnforeseenFailureTrace`) and the members the remote classes call, plus `MorpheusHttpServer.startRemote`.
      Widened for the moved tests only: `ApiRuntime` (type, constructor, `snapshots()`),
      `TimedBoundedResponseWriter.RESPONSE_STALL_TIMEOUT`, `MorpheusHttpRouteTable.declaredRoutes()`, and
      `BoundedWait` (type, two poll constants, `until`, `untilObserved`); the copy in `morpheus-mcp-transport`
      widens `until` and `untilObserved` the same way so that the two stay byte-identical, which
      `BoundedWaitOwnershipTest` checks.
- [x] 4.2 The transport plumbing and the local server, with the extractions 1.4 found necessary. The textual pins
      of `.claude/rules/security.md` follow a constant to its new owner in the same pull request.
      Done 2026-10-24, in two commits. First the extractions: `ApiSuccess`, `ApiError`, `ApiErrorEnvelope`,
      `ProjectRegistrationRequest`, `SyncRequest` become top-level records with the same simple names and JSON
      shapes, and `API_PREFIX` is declared by `MorpheusHttpRouteTable`; no router depends on `MorpheusHttpServer`
      any more. Then the moves: 10 classes to `com.morpheus.api.local` (the local server, its bootstrap and
      protections, `DatabaseCallWorkers`, `MorpheusInternalCapability`) with 8 tests, and 20 to
      `com.morpheus.api.transport` (decoder, writer, parser, query, budget, route table and guards, envelopes,
      failures, the timed readers and writers) with 15 tests; `AllowedWorkspaceRoots` stays with the services that
      use it. The local server builds the routers and services it holds as fields, so its move makes 14 of the 17 routers
      public (`MorpheusPolicyHttpRoutes`, `MorpheusPolicyManagementHttpRoutes` and `MorpheusReasoningHttpRoutes`, which
      the local server does not name, stay package-private), with the services it builds, their constructors and their route methods public, and every
      transport type another package uses is public (`HttpRequestBodyReader` and `JsonMediaType` stay package-private):
      89 declarations in main sources, each named by a compiler refusal, plus `HttpQueryBudget.MAX_QUERY_BYTES` and
      `MAX_PARAMETERS` for a moved test. The copy of `PagedEnvelope` in `morpheus-mcp` widens the same four members as
      the one in `morpheus-api`, so that the two stay identical (`PagedResponseVocabularyArchitectureTest`).
- [ ] 4.3 The routers, into their group packages. `HttpRoutesTransportBoundaryArchitectureTest` and
      `HttpRoutesFamilyArchitectureTest` select by package; the text scans beside the rules stay.
- [ ] 4.4 For each pull request: a line per moved file in the table of old and new paths of
      `docs/project/README.md`; dated records are not rewritten (`rules/documentation.md`).

## 5. The rules the packages make possible

- [ ] 5.1 The cycle rule over the packages of `com.morpheus.api`, with a guard that fails when the adapter has fewer
      than two packages. Break it once: a planted back-edge. Revert.
- [ ] 5.2 The classification rule: every `*HttpRoutes` resides in exactly one group package. Break it once: a router
      in the parent package. Revert.
- [ ] 5.3 The remote ban by package. Break it once with a `RemoteIdentity*` class in a router - the case the prefix
      missed. Revert.
- [ ] 5.4 The gate over the remaining lists (1.3): each is declared a debt counter or a table of facts.
- [ ] 5.5 Re-take the count of 1.5. Proof: not lower, pull request by pull request. If it is lower, the split of the
      tests is wrong, not the ratchet.

## 6. Lot B - services and composition (after sprint 11's ADR and `own-the-sqlite-connection-policy-in-one-place`)

- [ ] 6.1 Re-measure the composition roots and say whether each remaining one composes and does nothing else.
- [ ] 6.2 If they do: the composition package, the package rule in both directions, and a consolidated ADR replacing ADR-0109
      that replaces "add a name" by "add a class to the package". If they do not: the list stays, and the ADR says
      what is still missing.
- [ ] 6.3 The package of the `*ApiService` classes. Break the rule that separates it from the routers once.

## 7. Documentation and decision records

- [ ] 7.1 The ADR (next free number, re-read `docs/architecture/adr/*-[0-9][0-9][0-9][0-9]-*.md` and `archive/`; line in its `README.md`), with the
      measures of 1.1 to 1.4 and the breakages of 3.2 and 5.x.
- [ ] 7.2 A consolidated ADR replacing ADR-0109 (the row "not applicable"); `.claude/rules/architecture.md` (placement is classification).
- [ ] 7.3 `.claude/rules/architecture.md` - re-read the classes before rewriting the paragraph on router groups, as
      the paragraph itself asks; `.claude/rules/security.md`; `.claude/CLAUDE.md`;
      `.claude/skills/enforcement-choice/SKILL.md`; `docs/architecture/arc42/05-vue-blocs.md`.

## 8. Verification

- [ ] 8.1 `./mvnw clean verify` and `./mvnw dependency:analyze` on Linux and Windows; changed-line coverage gate.
- [ ] 8.2 `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml` byte-identical to the base.
      Proof: `git diff --stat`.
- [ ] 8.3 Pull request description: the finding, the measures, what each rule replaced, and the justification of
      every change under `morpheus-architecture-tests/` (`rules/governance.md`).
