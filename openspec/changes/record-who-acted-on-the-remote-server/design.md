# Design

## Context

- `serve` sets the response headers and the request identifier first, authenticates under its own concurrency limit,
  authorizes once, then serves the status and the backup itself or proxies.
- The route template comes from `MorpheusHttpRouteTable`: `projects/{projectId}/sync`, never the identifiers.
- `RemoteIdentityAudit` writes through `FileChannel` and `FileLock`, bounded to a fixed number of records.
- The remote disclosure boundary forbids server locations in responses; a journal is a local file and is not a response.
- The token is never stored: identities hold a SHA-256 fingerprint.

## Goals / Non-Goals

**Goals**: attribution of every authorized request; no secret and no personal content in a record; a bound; a failure
policy that is decided, not accidental.

**Non-Goals**: recording request or response bodies; recording path parameters or query strings; a route to read the
journal; shipping the journal elsewhere; the local server (single user, loopback, no principal).

## Decisions

1. **One record per authorized request, written when the response is decided.** Fields: timestamp (UTC), request
   identifier, principal, role held, role required, method, route template, status. Nothing else. *Rejected*: the raw
   path - it carries identifiers, and a hostile caller chooses its content.

2. **A 403 is recorded; a 401 is not.** A refused authorization has a principal and is exactly what an investigation
   looks for. A failed authentication has no principal, and a journal an unauthenticated caller can fill is a journal
   an unauthenticated caller can rotate; it stays the counter it is.

3. **JSON-lines, bounded by size and generations, private.** Same writer discipline as the operational file sink
   (rotation, permissions). The location is beside the identity file by default and is stated by the server status
   locally; it is never in a remote response.

4. **Failure policy - the maintainer decides.** When a record cannot be written: *recommended*, a WRITE or ADMIN
   request is refused with `503 ACCESS_JOURNAL_UNAVAILABLE` before it is forwarded - attribution of a mutation is the
   reason the journal exists - and a READ is served while a counter of lost records moves. *The alternative*: serve
   everything and count. The first can turn a full disk into refused writes; the second lets a mutation go
   unattributed.

5. **A mutation is recorded before it is forwarded and completed after.** A record written only after the response
   would be missing for exactly the request that crashed the server. Two lines for a WRITE or ADMIN request (accepted,
   then outcome), one for a READ; the lot measures the cost and may reduce it to one with the reason.

6. **The principal's name is written as stored, the token and its fingerprint never.** The redactor runs on every
   field; a test gives it a principal name shaped like a token.

7. **An ADR**: a new file the server writes, with a failure policy that can refuse requests.

## Risks / Trade-offs

- Disk: bounded by decision 3. Throughput: one append per request under a lock; measured against the remote load
  profile the repository already has.
- Rotation loses the oldest records. The bound is stated in the operator guide with the sentence that a journal to keep
  must be collected.
- Decision 4, first option, makes the journal part of the availability of writes. That is why it is the maintainer's.
