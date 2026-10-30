# Proposal

## Why

AUD-SEC-02 (sprint 3, the only High of the lot) reports that four HTTP routers answer a caller with the raw message of
whatever exception reached them. Re-read on `develop` at `0f3b0ad0` on 2026-10-12, the finding **holds as written**:

- `MorpheusQueryHttpRoutes.java:225`, `MorpheusPolicyHttpRoutes.java:197`, `MorpheusPolicyManagementHttpRoutes.java:148`
  and `MorpheusReasoningHttpRoutes.java:87` each declare a private `safeMessage` that replaces the message only when it
  is null or blank. They feed `BAD_REQUEST`, `STATE_CONFLICT`, `REVISION_CONFLICT`, `QUERY_VALIDATION`,
  `QUERY_BUDGET_EXCEEDED` and `REASONING_VALIDATION` answers.
- `MorpheusHttpServer.java:211-213` and `MorpheusRemoteHttpServer.java:261` use `BoundaryFailureMessage.safe`, which
  replaces a message by the exception's simple name when `ServerLocationDisclosure.isSafeToRelay` refuses it.
  `BoundaryFailureMessage.java:13-15` says "Both servers apply this". The four routers are the routes for which that
  sentence is false.
- The remote facade relays the upstream body byte for byte (`MorpheusRemoteProxyTransport`), so what a local router
  answers is what a caller outside the machine reads.

A fifth `safeMessage` exists, in `MorpheusHttpRequestDecoder.java:79`, for the text of a JSON parsing failure. The audit
sets it aside; the specification does not, because the rule is the same: it either goes through the filter or is shown
to carry no location.

These routers serve query, exports, saved views, policy, policy management and reasoning: about twenty routes reachable
remotely with a READ or WRITE role. An `AccessDeniedException` or a storage failure raised under them names a server
path, and that path reaches the caller on the very surface `ServerLocationDisclosure` exists to close.

## What Changes

- Every failure message a router of `morpheus-api` puts in a response passes through `BoundaryFailureMessage.safe`. The
  four private `safeMessage` methods disappear.
- A rule refuses a method of `morpheus-api` that turns a `Throwable` into response text outside `BoundaryFailureMessage`.
- A test per router family raises a failure whose message names a server location and reads the response.

## Capabilities

### New Capabilities

- `http-failure-disclosure`: what the text of an unexpected failure may tell an HTTP caller, on every router, and how
  that is kept true of a router added later.

### Modified Capabilities

(none)

## Impact

- `morpheus-api`: the four routers, `MorpheusHttpRequestDecoder`, `BoundaryFailureMessage` (its sentence becomes true).
- `morpheus-architecture-tests`: one rule beside `HttpRoutesTransportBoundaryArchitectureTest`.
- Observable change: a caller who used to read a path in an error message now reads the exception's simple name. The
  status codes, the error codes and every message MORPHEUS authored itself are unchanged. No route, manifest line or
  OpenAPI operation moves.
