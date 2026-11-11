# Proposal

## Why

AUD-SEC-06 (sprint 3, Low) reports that the security headers of a response depend on which code path wrote it. Re-read
on `develop` at `0f3b0ad0` on 2026-10-12, the finding **holds**:

- `MorpheusHttpResponseWriter.java:31-35` sets `Content-Type`, `Cache-Control: no-store` and
  `X-Content-Type-Options: nosniff` on every local success and error.
- `LoopbackRequestProtectedHttpServer.java:36-40`, which answers a refused origin on the **same** local server, also
  sets `X-Frame-Options: DENY` and `Referrer-Policy: no-referrer`.
- `MorpheusRemoteResponseWriter.java:32-39` sets those plus `Content-Security-Policy` and `X-Request-Id`, and no
  `Strict-Transport-Security`, although the remote facade serves HTTPS only.

The real exposure is small: a JSON API, `nosniff` everywhere, a local server guarded by its Host and Origin policy. The
defect is the asymmetry: a refusal is better protected than a success on one server, and the HTTPS-only facade does not
say it is HTTPS-only. An asymmetry of this kind ends up being read as a rule.

## What Changes

- Every response of the local server carries one set of security headers, whether it is a success, an error or an
  origin refusal.
- Every response of the remote facade carries that set plus `Strict-Transport-Security`.
- The set is declared once and a test reads the headers of real responses on both servers.

## Capabilities

### New Capabilities

- `http-response-headers`: which security headers every HTTP response of MORPHEUS carries, per server, and how that is
  held for every way a response can be written.

### Modified Capabilities

(none)

## Impact

- `morpheus-api`: `MorpheusHttpResponseWriter`, `LoopbackRequestProtectedHttpServer`, `MorpheusRemoteResponseWriter`.
- `.claude/rules/security.md` (the remote table names `X-Frame-Options` and `Content-Security-Policy`; it gains HSTS)
  and the textual security gate that asserts those strings.
- No route, manifest line or OpenAPI operation moves. No CORS header is added (the ban stands).
