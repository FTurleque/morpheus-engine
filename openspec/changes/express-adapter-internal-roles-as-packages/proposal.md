# Proposal

## Why

AUD-ARC-06 (sprint 12) reports that `morpheus-api` is one flat package, so that every boundary inside the HTTP adapter
is kept as a list of class names in a gate instead of being carried by the structure. Re-read on `develop` at
`8b26d3d7` on 2026-10-13, the finding **holds, with different numbers and one consequence the audit did not weigh**:

- `morpheus-api`: 81 source files in the single package `com.morpheus.api` (the audit counted 80), of which 17
  `*HttpRoutes` and 19 `*ApiService`. `morpheus-mcp`: 20 files, one package. `morpheus-cli`: 28, one package.
  `morpheus-store-sqlite`: 30, one package (the audit counted 29).
- `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` (lines 38-70) names 32 classes, 20 of `api` and 12 of
  `mcp`. `HttpRoutesTransportBoundaryArchitectureTest` (lines 70-89) names the 17 routers in three groups of 8, 5 and
  4. `HttpPathReadingArchitectureTest` (lines 33-60) names files of `api` in four maps of 14 entries.
  `HttpRoutesFamilyArchitectureTest` selects the routers by the suffix `HttpRoutes` and the remote model by the prefix
  `MorpheusRemote` - a name standing in for a role.
- ADR-0109 records the consequence itself: "cycles between slices of `com.morpheus.api.(*)..` : not applicable, `api`
  has no sub-package".

What the audit did not weigh is what the flat package **does** enforce today, by the compiler:

- 68 of the 81 top-level types of `api` are package-private (17 of 20 in `mcp`, 24 of 28 in `cli`). The other modules
  can name 13 types of `api` and use 6 (`AllowedWorkspaceRoots`, `LoopbackHostPolicy`, `MorpheusHttpServer`,
  `MorpheusRemoteHttpServer`, `MorpheusRemoteIdentityFile`, `MorpheusRemoteRole`), and 1 of `mcp`
  (`MorpheusMcpServer`). A class moved to a sub-package must become `public` to be used by its neighbours: the split
  removes a boundary the compiler holds unless a rule replaces it.
- The gates pin the flat layout as text. 117 string literals of the form
  `morpheus-api/src/main/java/com/morpheus/api/X.java` in `morpheus-architecture-tests` (14 for `cli`, 10 for `mcp`),
  43 lines that name the exact package or a fully-qualified class, six validator pairs (`validate-m23` to `m27`, `validate-r2`)
  that cite `com/morpheus/<adapter>/` paths, 97 test files of `morpheus-api` in the same package for package-private
  access, and two convergence tests of the gate module declared in the adapters' own packages for the same reason
  (`PublicHttpRouteConvergenceTest` in `com.morpheus.api`, `PublicSurfaceManifestCoversEveryServedToolTest` in
  `com.morpheus.mcp`).

And the two lists the finding cites are **not the same kind of list**:

- The three router groups are a classification by role in the transport: a package can carry it.
- `COMPOSITION_ROOTS` is a debt counter (ADR-0109: "the named list is its counter"). It crosses every role: 15 of the
  19 `*ApiService` are roots and 4 are not, one router is a root (`MorpheusPolicyManagementHttpRoutes`), and so are
  both servers, both bootstraps and both runtimes. The audit's example - "only `api.service` and `api.remote` reach
  `store..`" - would grant the reach to the four services that do not have it and withdraw it from a router that
  does. Replacing that list by a role package today widens what ADR-0109 says only shrinks.

## What Changes

- `morpheus-api` gets sub-packages by role; the rules that select its classes by a suffix, a prefix or a list of
  names standing in for a role select them by package.
- A gate keeps the adapter's packages free of cycles, which ADR-0109 could not write.
- A rule holds what package-private visibility held: the types another module may use are a named entry package, and
  nothing outside the adapter depends on the rest.
- The gates stop pinning a production class by the path of its file, so that moving a class changes no gate whose
  subject is not its package.
- `COMPOSITION_ROOTS` stays a named list until its members coincide with a role; the condition and the replacement
  rule are written down. It does not grow by this change.

## Capabilities

### New Capabilities

- `adapter-internal-boundaries`: how a boundary inside a transport adapter is stated, what may still be a list of
  names, and what a move of classes must not loosen.

### Modified Capabilities

(none)

## Impact

- `morpheus-api`: every source file changes package or stays in the entry package; its tests move with their
  subjects. No behaviour, no route, no payload changes.
- `morpheus-architecture-tests`: `HttpRoutesFamilyArchitectureTest`, `HttpRoutesTransportBoundaryArchitectureTest`,
  `HttpPathReadingArchitectureTest`, `AdapterCompositionRootArchitectureTest`, the thirteen
  `Local*HttpRoutesArchitectureTest`, and every class that reads an `api` source by a literal path.
  `architectureTestsMinimum` does not go down (`config/m21-quality-ratchets.properties`, procedure `live-numbers`).
- `scripts/validate-m23` to `m27` and `validate-r2`, `.ps1` and `.sh` together.
- **A new ADR** (the highest number attributed on 2026-10-13 is `0110`; re-read the directory before assigning), and
  a consolidated ADR replacing ADR-0109 (the row "not applicable" of its measure, the condition under which the list becomes a
  package rule), and `.claude/rules/architecture.md` (a classification by placement is a classification by name).
- `.claude/rules/architecture.md`, `.claude/rules/security.md`, `.claude/CLAUDE.md` ("Points d'entree cles"),
  `.claude/skills/enforcement-choice/SKILL.md`, `docs/developer/` pages that name a class by path,
  `docs/architecture/arc42/05-vue-blocs.md`, `docs/project/README.md` (table of old and new paths).
- No public surface: `contracts/public-surfaces.tsv` and the OpenAPI files do not move.
- `morpheus-mcp` and `morpheus-cli` are split by `give-each-cli-command-and-mcp-tool-a-capability-class`, where their
  packages have a rule to carry. `morpheus-store-sqlite` is out of scope: no gate lists its classes.
- **Order**: after `own-the-sqlite-connection-policy-in-one-place` and `decode-http-path-segments-once`, which edit
  the same classes. What waits for sprint 11 and what does not is in `design.md`.
