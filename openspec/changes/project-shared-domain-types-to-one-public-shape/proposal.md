# Proposal

## Why

AUD-ARC-10 (sprint 9) reports that the projection of a domain type to its public payload is written once per adapter
class, identically, and that no gate compares the payloads. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the
finding **holds as written**; the line numbers it cites are still exact.

- Eight domain types are projected by a private method in more than one class of `morpheus-api` and `morpheus-mcp`:

  | Domain type | Copies | Where |
  |---|---|---|
  | requirement | 4 | `MorpheusRequirementQueryApiService.java:91`, `MorpheusHistoryApiService.java:130`, `MorpheusSpecificationQueryApiService.java:107`, `MorpheusMcpToolService.java:312` |
  | change | 3 | `MorpheusChangeQueryApiService.java:154`, `MorpheusSpecificationQueryApiService.java:125`, `MorpheusMcpToolService.java:321` |
  | specification | 2 | `MorpheusSpecificationQueryApiService.java:116`, `MorpheusMcpToolService.java:303` |
  | scenario | 2 | `MorpheusSpecificationQueryApiService.java:137`, `MorpheusMcpToolService.java:361` |
  | constraint | 2 | `MorpheusChangeQueryApiService.java:166`, `MorpheusMcpToolService.java:333` |
  | acceptance criterion | 2 | `MorpheusChangeQueryApiService.java:170`, `MorpheusMcpToolService.java:337` |
  | decision | 2 | `MorpheusChangeQueryApiService.java:182`, `MorpheusMcpToolService.java:349` |
  | task | 2 | `MorpheusChangeQueryApiService.java:190`, `MorpheusMcpToolService.java:355` |

  Nineteen declarations for eight shapes. A ninth, the versioned requirement record, is written twice inside
  `morpheus-api` (`MorpheusHistoryApiService.java:121`, `MorpheusRequirementQueryApiService.java:82`).
- Read pair by pair, **every copy of a shape lists the same keys and the same values today**; the only textual
  difference is a constant `FIELD_TITLE` where another copy writes the literal. The duplication has not drifted yet.
- Nothing holds it there. `PublicHttpRouteConvergenceTest` compares methods and paths,
  `PublicSurfaceManifestCoversEveryServedToolTest` compares tool names, the pinned `publicManifestAndOpenApiExposeSame*`
  tests compare a few chosen lines. A field added to one of the four `requirement` copies changes the HTTP answer and
  not the MCP one, breaks no gate and contradicts no line of `contracts/public-surfaces.tsv`.

The product's claim is one truth on three transports. Today that is true of the routes by test, and of the payloads by
luck.

## What Changes

- Each domain type shown on more than one transport has one projection to its public shape, and every adapter uses it.
- A test compares what HTTP and MCP answer for the same published content, shape by shape.
- A rule refuses an adapter that declares a projection of a shared domain type of its own.

## Capabilities

### New Capabilities

- `public-payload-convergence`: the public shape of a domain type is the same on every transport that shows it.

### Modified Capabilities

(none - `public-surface-convergence` keeps its two requirements on routes and documents; this capability is its
counterpart for the content of an answer)

## Impact

- **Production code**: the nineteen methods above in five classes of `morpheus-api` and `morpheus-mcp`; one new set of
  projections in `morpheus-application`, which `api`, `mcp` and `cli` already depend on and where the compact views of
  the same types already live.
- **No public payload changes.** `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml` are not
  edited; the convergence test is what proves it.
- One behavioural convergence test and one ArchUnit members rule in `morpheus-architecture-tests`;
  `architectureTestsMinimum` rises.
- **An ADR is needed**: the public shape of a domain type becomes an application contract instead of an adapter
  detail. Read the highest number in `docs/architecture/adr/` at the time (`.claude/skills/live-numbers`); add its line
  to the ADR index.
- Coverage: about nineteen covered methods leave two adapter modules for one module; both scales are read before and
  after, no ratchet is lowered.
- **After** `build-payload-maps-through-one-checked-builder` (AUD-QUA-07): the projections are written with that
  builder. **Before** sprint 12 (AUD-ARC-06, AUD-ARC-09): it empties part of `MorpheusMcpToolService` and of the flat
  `com.morpheus.api` package, and its rule is written on packages, not on class names, so that sprint 12 can move
  classes without touching it. **Compatible with** sprint 11 whichever way it decides: a projection is a pure function
  of a domain value.
