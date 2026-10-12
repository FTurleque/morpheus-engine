# Proposal

## Why

AUD-SEC-03 (sprint 3) reports that the local HTTP adapter percent-decodes path segments twice, while the remote
authorization decodes them once. Re-read on `develop` at `0f3b0ad0` on 2026-10-12, the finding **holds**:

- `MorpheusHttpServer.java:223` passes `exchange.getRequestURI().getPath()` - already decoded by `java.net.URI` - to
  `MorpheusHttpPathParser.segments`, which applies `URLDecoder.decode` to each segment (`:33`).
- The same parsing is written twice more, with the same second decoding: `MorpheusPolicyHttpRoutes.java:192` and
  `MorpheusQueryHttpRoutes.java:212`.
- The remote server decides the required role on `getPath()` (`MorpheusRemoteHttpServer.java:219`) and forwards
  `getRawPath()` (`MorpheusRemoteProxyTargetResolver.java:39`).

So `%252e`, `%252f` and `+` do not mean the same thing to the code that authorizes a request and to the code that
routes it. No bypass was demonstrated: every path parameter today is typed and fails to parse before it is used. The
protection comes from the identifiers, not from the path handling, and the first free-text path parameter would open the
class of bypass.

## What Changes

- A path is split into segments from its raw form and each segment is percent-decoded exactly once, by one function,
  which never turns `+` into a space.
- The local server, the two routers that carried a copy, and the remote route policy all obtain their segments from
  that function. The two copies disappear.
- A test proves that, for a set of hostile encodings, the segments the remote authorization sees are equal to the
  segments the local router sees.

## Capabilities

### New Capabilities

- `http-path-decoding`: how a request path becomes segments, once, and identically for authorization and for routing.

### Modified Capabilities

(none)

## Impact

- `morpheus-api`: `MorpheusHttpPathParser`, `MorpheusHttpServer`, `MorpheusPolicyHttpRoutes`, `MorpheusQueryHttpRoutes`,
  `MorpheusRemoteRoutePolicy` / `MorpheusRemoteHttpServer`, `MorpheusHttpRouteTable` lookups.
- `HttpRoutesFamilyArchitectureTest` forbids routers to depend on `MorpheusHttpPathParser` today (one of the three family
  bans): the design says how the two routers reach the single function without breaking that ban, or why the ban moves.
- Observable change: a doubly encoded segment is no longer decoded twice, and `+` in a path stays `+`. No route,
  manifest line or OpenAPI operation moves.
