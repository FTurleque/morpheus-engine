# Proposal

## Why

AUD-PRF-09 (sprint 5) reports that the remote proxy imposes neither a read timeout nor a memory budget on WRITE and
ADMIN routes. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding **holds**:

- `MorpheusRemoteProxyTransport.java:75`: `if (boundedUpstreamTimeout) request.timeout(READ_ONLY_UPSTREAM_TIMEOUT);` -
  sixty seconds, for bounded requests only.
- `:89`: a response slot is taken only `if (boundedUpstreamTimeout ...)`.
- A request is bounded only when its role is READ (`MorpheusRemoteRoutePolicy.Authorization.usesBoundedUpstreamTimeout`,
  since sprint 3).
- The only limit on a WRITE or ADMIN call is the connect timeout of five seconds.

On any WRITE or ADMIN route, an upstream response that never comes holds the virtual thread and its concurrency permit
for ever, and its response is read without the budget the reads have. The facade limits how many such requests run, not
how long. A few stuck writes exhaust the privileged ceiling and the remote server refuses every write until it is
restarted.

The reason the code gives is real: the local server cannot cancel an operation once it has started, so a timeout does
not stop the work. That makes the wording of the refusal matter; it does not make waiting for ever right.

## What Changes

- Every upstream call has a read timeout: the reads keep theirs, the writes get a longer one.
- Every proxied response is read within the memory budget.
- A write that times out is answered in terms that say the outcome is unknown.

## Capabilities

### New Capabilities

- `remote-proxy-upstream-bounds`: no upstream call of the remote proxy waits or buffers without a bound.

### Modified Capabilities

(none)

## Impact

- `morpheus-api` (`MorpheusRemoteProxyTransport`, `MorpheusRemoteRoutePolicy.Authorization`, the remote server),
  `docs/user/TEAM_REMOTE_SERVER.md`, the remote OpenAPI file if a status is added to write operations,
  `.claude/rules/security.md` (it names `usesBoundedUpstreamTimeout`).
- Independent of the three database changes of the sprint; can be done first.
