# Design

## Context

- `URI.getPath()` returns the decoded path; `URI.getRawPath()` the path as sent. `URLDecoder.decode` is a form decoder:
  it decodes percent escapes **and** turns `+` into a space, which is wrong for a path.
- Splitting a decoded path on `/` treats an encoded `%2F` as a separator; splitting the raw path and decoding each
  segment does not. The two orders disagree exactly on the inputs an attacker would choose.
- `MorpheusHttpRouteTable` is the single list of routes and methods; the local `Allow` header and the remote role
  policy are both computed from it, by path.
- `HttpRoutesFamilyArchitectureTest` holds, for every `*HttpRoutes`, a ban on `MorpheusHttpPathParser` (rule and text).

## Goals / Non-Goals

**Goals**: one decoding, one function, the same segments for authorization and routing.

**Non-Goals**: query-string decoding (`MorpheusHttpQuery` decodes form parameters, where `+` is a space by definition);
normalising `.` and `..` (no route has a free-text segment; a dot segment matches no route and is a 404); changing what
the proxy forwards (it keeps the raw path, so that the internal hop decodes it itself, once).

## Decisions

1. **Split raw, decode once per segment, with a path decoder.** The function takes the raw path, splits on `/`, refuses
   an empty segment, and decodes percent escapes in each segment as UTF-8 without touching `+`. A malformed escape is a
   404 "invalid API path", as an empty segment is today. *Rejected*: dropping `URLDecoder` and keeping `getPath()` - it
   decodes once but still splits after decoding, so `%2F` creates a segment on one side only.

2. **The remote role policy uses the same function on the same raw path.** The authorization and the internal hop then
   read the same bytes through the same code. This is the requirement the audit asks a test for.

3. **Where the function lives, given the family ban.** The ban on `MorpheusHttpPathParser` in routers exists so that a
   router does not re-parse what the server already parsed. Two routers register their own context and receive a full
   path: they need segments. The implementation chooses between (a) giving them the segments through the shared
   decoder they already must depend on, and (b) narrowing the ban to "no second implementation" by amending ADR-0103's
   record of it. (a) is preferred: no rule moves. Whichever is chosen is written in the pull request with the test that
   proves the ban still bites.

4. **Enforcement.** Textual and structural: no `URLDecoder` in `com.morpheus.api` outside `MorpheusHttpQuery` and
   `MorpheusRemoteProxyTargetResolver` (query parameters), named with their reason; and the equality test of decision 2.

5. **No ADR**, unless decision 3 ends on (b), which amends ADR-0103.

## Risks / Trade-offs

- A client that double-encoded a segment by mistake and relied on the second decoding gets a 404 or a parse refusal.
  Identifiers are UUIDs and tokens without reserved characters; the release note states the change.
- `+` in a path segment now stays `+`. Same remark.
