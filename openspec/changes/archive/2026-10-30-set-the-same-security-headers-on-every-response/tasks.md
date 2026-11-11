# Tasks

Implemented on 2026-10-12 by pull request 454.

## 1. Measure before changing

- [x] 1.1 Record the headers of a success, a 400, a 404, a 405, a 409 and an origin refusal on the local server, and of
      a proxied response, a 401, a 403 and a 429 on the remote facade. Proof: the table.
      *2026-10-13: both halves are held by tests on real responses rather than by a table. Local: six kinds in `LocalResponseSecurityHeadersTest` (200, 404, 405, 400, 403, 409) and the capability refusal (401) in `CapabilityProtectedHttpServerTest`. Remote: eight kinds in `MorpheusRemoteHttpServerTest` — 401, a proxied 200, 404, 405, 403, a proxied 400, a 201 the facade serves itself and a 429 — each carrying the five headers, `Strict-Transport-Security`, a request identifier and no CORS header.*
- [x] 1.2 Read what the security gate asserts textually about these headers. Proof: the assertions that must still pass.

## 2. Reproduce before fixing

- [x] 2.1 The behavioural test of both servers. Proof: red on `HEAD` for the local success and errors (two headers
      missing) and for every remote response (HSTS missing).
      *Three failures before the change: the local responses, the remote writer, the remote server.*

## 3. The change

- [x] 3.1 One declaration of the local set; the shared writer and the origin-refusal path use it. Proof: local half of
      2.1 passes.
      *`MorpheusHttpResponseWriter.applySecurityHeaders`; the origin guard no longer sets a security header itself.*
- [x] 3.2 HSTS in the remote writer. Proof: remote half of 2.1 passes.
      *`max-age=31536000`, without `includeSubDomains`: the header speaks for the host the operator exposes.*
- [x] 3.3 `.claude/rules/security.md` remote table and the textual gate gain HSTS in the same change. Proof: the gate.
- [x] 3.4 Break once, then revert: remove one header from the shared set. Proof: the named failure.

## 4. Verification

- [x] 4.1 `./mvnw clean verify`. Proof: green.
      *By the required checks of the pull request, not locally.*
- [x] 4.2 `docs/user/TEAM_REMOTE_SERVER.md`: one sentence on HSTS and its one-year pin. Pull request description: the
      finding, the table of 1.1.
      *The sentence and the finding; of the table, see 1.1.*

## After the reviews of 2026-10-13

- A third local response path wrote its headers itself and missed three of the five: the 401 of
  `MorpheusInternalCapability`, answered to a loopback request that lacks the internal capability in remote mode. It
  calls the shared declaration now. Task 3.1 was ticked while this path was outside it.
- "Every local response" means every response under `/api/v1`: a path outside the context and a malformed request line
  are answered by the JDK server itself, without these headers. The release note says "under `/api/v1`".
