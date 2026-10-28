# Proposal

## Why

AUD-PRF-09 (sprint 5): the remote proxy bounds the wait for the local server on READ routes only. On a WRITE or ADMIN
route the only limit is the five-second connect timeout, so an upstream response that never comes holds the request's
thread and its privileged permit for ever. A few such writes exhaust the privileged ceiling, and the remote server
refuses every write with `429 PRIVILEGED_CONCURRENCY_LIMIT` until it is restarted.

The maintainer closed the finding by decision on 2026-10-17 and accepted the absence of a write deadline as a residual
risk; the change `bound-every-upstream-call-of-the-remote-proxy` was archived on 2026-10-28 with that decision. **On
2026-10-28 the maintainer reopens that decision and asks for the correction.** This change carries it out. Its plan is
the archived change's: decisions 1 to 6 of its design apply, with the durations and the treatment of the response budget
settled in this change's design.

Reproduced on `develop` at `5c3ce4dd`: a WRITE forwarded to an upstream that never answers is still waiting after ten
seconds, where the test gives up (task 2.1).

## What Changes

- Every upstream call has a read timeout: sixty seconds for a READ route, three hundred for a WRITE or ADMIN route,
  provider-plugin probe included.
- A write that times out is answered `504 UPSTREAM_WRITE_TIMEOUT`, whose message says the operation may have completed
  and that its state must be read before a retry.
- The authorization of a request gives the deadline of its role; the boolean that said "bounded or not" goes.
- The operator guide states both durations, what an expired write means, and that the privileged permit is released
  when the facade gives up.

## Capabilities

### New Capabilities

- `remote-proxy-upstream-bounds`: no upstream call of the remote proxy waits or buffers without a bound.

### Modified Capabilities

(none)

## Impact

- `morpheus-api`: `MorpheusRemoteProxyTransport` (`UpstreamDeadline`), `MorpheusRemoteRoutePolicy.Authorization`,
  `MorpheusRemoteHttpServer`.
- Tests: `MorpheusRemoteRoutePolicyTest`, `MorpheusRemoteProxyResponseBudgetTest`, `MorpheusRemoteProxyTransportTest`,
  `MorpheusRemoteHttpServerTest`; architecture pins in `HttpPathReadingArchitectureTest` and
  `RemoteServerArchitectureTest`.
- Documentation: `docs/user/TEAM_REMOTE_SERVER.md`, `docs/developer/REMOTE_SERVER_PLATFORM.md`,
  `docs/user/release-notes/RELEASE_NOTES_1.2.2.md`, `.claude/rules/security.md` and the two `.claude/` review prompts
  that named `usesBoundedUpstreamTimeout`.
- Public contract: no route, tool or command moves; the OpenAPI files declare no error status per operation, so the
  new code adds no line there (archived task 1.3).
- No ADR: a write deadline breaks no persisted data and no published contract, and is additive behind a new code; the
  admission and 429 behaviour it relies on is ADR-0094's.
