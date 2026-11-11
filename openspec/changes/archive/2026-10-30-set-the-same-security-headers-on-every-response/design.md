# Design

## Context

- Three writers produce responses: the shared local writer, the origin-refusal path of the loopback guard, the remote
  writer. The remote proxy relays the upstream body and sets its own headers through the remote writer.
- `.claude/rules/security.md` requires `X-Frame-Options` and `Content-Security-Policy` on the remote surface and forbids
  `Access-Control-Allow-Origin` anywhere. The security gate asserts these strings in the sources.
- The local server binds loopback only and speaks plain HTTP by design; the remote facade is `HttpsServer` only.

## Goals / Non-Goals

**Goals**: one header set per server, on every response; HSTS on the HTTPS-only facade.

**Non-Goals**: CORS (forbidden); HSTS on the local server (it is plain HTTP on loopback, where the header is ignored and
would be a false statement); `includeSubDomains` or `preload` (MORPHEUS does not own the operator's domain).

## Decisions

1. **The set**: `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
   `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`. The remote
   facade adds `Strict-Transport-Security: max-age=31536000` and keeps `X-Request-Id`.

2. **Declared once per server.** A single place holds the local set and the origin-refusal path uses it, so that the
   two cannot drift again. *Rejected*: copying the five lines into the second writer.

3. **`max-age` of one year, no `includeSubDomains`.** The header applies to the host the operator exposes; widening it
   to subdomains is a decision about a domain MORPHEUS does not control. *Rejected*: a short max-age "to be safe" - the
   facade has no plain-HTTP mode to fall back to.

4. **Proof on real responses.** A test starts each server and reads the headers of a success, an application error, a
   404, a 405 and, locally, an origin refusal. The textual gate keeps asserting the strings; the behavioural test is
   what sees a path that forgot the writer.

5. **No ADR.** Defence in depth inside the existing remote hardening decision.

## Risks / Trade-offs

- A browser-based tool embedding a local MORPHEUS response in a frame stops working. None is known or supported.
- HSTS pins HTTPS for a year for a host name; an operator who later serves plain HTTP on that name for something else
  is affected. Stated in the remote server guide.
