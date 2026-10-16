# Proposal

## Why

AUD-SEC-04 (sprint 4) reports that the authenticated principal of a remote request is recorded nowhere. Re-read on
`develop` at `bd2294b6` on 2026-10-13, the finding **holds**:

- `MorpheusRemoteHttpServer.serve` authenticates into a local variable and reads only its role
  (`identity.role().allows(route.role())`); the principal's name leaves the method with the stack frame.
- `MorpheusRemoteRuntimeState` holds anonymous counters.
- The audit kept beside the identity file (`RemoteIdentityAudit`, bounded) records mutations of credentials - create,
  revoke, rotate - never operations.
- A request identifier is generated for every request and returned as `X-Request-Id`, and since sprint 3 the server
  holds, for each authorized request, the route template and the role (`MorpheusRemoteRoutePolicy.Authorization`). The
  pieces of an access record exist; nothing puts them together.

After a WRITE or ADMIN token is misused - a project registered, a sync, a lifecycle transition, a policy override, a
backup - nothing says which principal acted, or when. The token to rotate cannot be targeted and the incident cannot be
reconstructed. `SECURITY.md` speaks of an audit; it is the audit of credentials, not of access.

## What Changes

- Every request the remote server authorizes leaves one record without a secret: when, which principal, which role,
  which method and route template, which status, which request identifier.
- A refusal of authorization (403) is recorded with its principal. A failed authentication (401) has no principal and
  stays a counter.
- The journal is bounded and separate from the identity file.
- `SECURITY.md` says what each of the two records is.

## Capabilities

### New Capabilities

- `remote-access-journal`: who did what on the remote server, bounded, without secrets, separate from the audit of
  credentials.

### Modified Capabilities

(none)

## Impact

- `morpheus-api` (remote server, a journal writer), `morpheus-cli` (where the journal's location is resolved),
  `SECURITY.md`, `docs/user/TEAM_REMOTE_SERVER.md`, `.claude/rules/security.md`.
- No route is added by this change: the journal is a file the operator reads. A surface to read it is a separate
  decision, with its manifest line.
- After `leave-a-trace-when-a-failure-is-swallowed`, whose decision on a file under the logs directory this change
  follows.
