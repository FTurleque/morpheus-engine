# Tasks

## 1. Measure before changing

- [x] 1.1 For each hostile encoding of the second requirement, record what `getPath()`, the three parsers and the remote
      policy produce today. Proof: the table; at least one row where authorization and routing differ.
      *2026-10-12: measured with `RemoteAuthorizationLocalRoutingParityTest` against the unfixed code, the policy being
      given `getPath()` as the remote server gave it. Ten rows of thirty-four differ. Eight are refused by the policy
      and routed by the local server (`/api/v1/health//`, `/api/v1health`, `.../%2561rchive`, `/api/v1/saved-viewsx`,
      `/api/v1/policy-packsx`, `.../%2565valuate`, `/api/v1/policy-activations%2Fx`, `.../versions/%2563ompare`); two
      are authorized by the policy and refused locally (`/api/v1/provider-plugins%2Fprobe`, `/api/v1/server%2Fstatus`).
      No row grants a role the caller did not hold: the finding is a divergence, not a demonstrated bypass.*
- [x] 1.2 Read how the remote role policy matches a path against the route table, and whether it splits on `/` itself.
      Proof: the lines.
      *2026-10-12: `MorpheusRemoteRoutePolicy.apiSegments` split the decoded path itself (`suffix.split("/", -1)`) and
      decoded nothing; a third reading beside the parser and the two router copies.*

## 2. Reproduce before fixing

- [x] 2.1 Unit tests of the parser for the four scenarios of the first requirement. Proof: `%252e%252e` and `a+b` fail
      on `HEAD`.
      *2026-10-12: `MorpheusHttpPathParserTest`; the former test pinned `hello+world` to `hello world`.*
- [x] 2.2 The equality test of the second requirement, parameterised over the encodings. Proof: red on `HEAD` for the
      rows of 1.1.
      *2026-10-12: `RemoteAuthorizationLocalRoutingParityTest`, one real local server and every path in a single
      method that reports all disagreements at once rather than the first.*
- [x] 2.3 The textual rule on `URLDecoder` with its named exceptions. Proof: red on `HEAD`, naming the parser and the
      two routers.
      *2026-10-12: `HttpPathReadingArchitectureTest`, a dependency rule rather than a text scan (`URLDecoder` declares
      no constant a compiler could inline), with a companion rule that fails when a named exception stops applying.*

## 3. The change

- [x] 3.1 The single function (raw split, one decoding, no `+`). Proof: 2.1 passes.
      *2026-10-12: the prefix is matched by decoded segment, not by text, so `/api/v1health` is no longer `health`.*
- [x] 3.2 Local server, the two routers and the remote policy use it; the copies are deleted. Decision 3 of the design
      is taken and written down. Proof: 2.2 and 2.3 pass; `HttpRoutesFamilyArchitectureTest` green, and its ban broken
      once to prove it still bites.
      *2026-10-12: decision 3 ends on (a). The routers obtain their segments from `MorpheusHttpRequestDecoder`
      (`pathSegments`, `pathSegmentsBelow`); no rule moved and ADR-0103 is not amended. The exact-path checks of the
      query, policy-management and reasoning routers read segments too, so no source of the adapter reads
      `getRequestURI().getPath()` any more. Broken once in `MorpheusReasoningHttpRoutes` with a single line that
      form-decoded a split of the decoded path and named the parser: four failures, one per rule
      (`noClassOfTheAdapterFormDecodesAnythingButAQuery`, `noSourceOfTheAdapterReadsTheDecodedPathOfARequest`,
      `onlyTheParserSplitsARequestPath`, `noRouterParsesThePathItself`).*
- [x] 3.3 `PublicHttpRouteConvergenceTest` (it starts a real local server and routes every table entry). Proof: green
      unmodified.
      *2026-10-12: green, unmodified.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate replayed. Proof: green.
      *2026-10-12: by the required checks of pull request 457 on both platforms, the changed-line gate included; not run locally.*
- [x] 4.2 Release note in the terms of a caller. Pull request description: finding, the table of 1.1, decision 3.

## Not in the specification, found while doing it

- A malformed escape (`%zz`) cannot reach the parser through the JDK server, which refuses the request line itself
  with a 400. The 404 of the first requirement is proved on the parser, not over HTTP.
- A router that checks the method before the path answers an unknown path with a 405 whose `Allow` is empty. The parity
  test reads that as "no route", which is what RFC 9110 says of an empty `Allow`; the order of the two checks is not
  changed by this lot.
- The remote policy had two refusal messages, `unknown remote API path` and `invalid remote API path`. It now has the
  first only: it no longer splits the path, so it no longer knows which of the two it was.

## After the reviews of 2026-10-12 and 2026-10-13

- The design spoke of "two routers"; four register their own context, and all four read segments now.
- Decision 3 promised a test that proves the family ban still bites. The ban held, but only on a name: a router the
  server holds could ask the decoder for the path. `onlyARouterThatRegistersItsOwnContextAsksTheDecoderForThePath`
  closes it, and ADR-0103 is amended after all — not to narrow the ban, to say what it holds.
- The remote server authorizes a request once (`MorpheusRemoteRoutePolicy.authorize`) and asks the result for the
  route and the role; the proxy resolver still reads the path once, through the same parser, and knows no role.
- The readers of the raw path are a closed, named list, and none compares it as text.
- `RemoteAuthorizationLocalRoutingParityTest` reads the route the local server selected in its `Allow` header, which
  is computed from the same table as the policy's: it proves that both select a route, not that the route runs the
  action. `HttpPathSegmentDecodingTest#anEncodedActionRunsTheActionItNamesAndNoOther` observes the action itself.
- A trailing slash is now ignored on the fixed-path routes too (`/queries/execute/`), which answered 404 locally while
  the remote policy authorized them. Stated in the release note.
