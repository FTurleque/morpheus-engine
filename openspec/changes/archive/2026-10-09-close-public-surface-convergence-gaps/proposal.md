# Proposal

## Why

The governance rule says the code, `contracts/public-surfaces.tsv` and `docs/openapi/*.yaml` change together, and
`.claude/rules/governance.md` claims they are compared character by character. At `3ec3ea46` the audit found
`GET /api/v1/provider-plugins/discover` served (`MorpheusProviderPluginHttpRoutes`) and declared in the manifest but
absent from every OpenAPI document, and found that only the `mcp` column of the manifest is compared with what is
served in both directions; the HTTP column is checked for non-emptiness and per-family substrings
(audit `docs/audits/AUDIT_OUTILLE_2026-10-08.md`, finding API-AUD-1).

## What Changes

- The `discover` route is documented in the OpenAPI document of the provider-plugin surface.
- A gate compares, in both directions, the set of HTTP routes (method + normalised path) the servers register, the
  `http` column of the manifest and the OpenAPI documents; a route present in one and absent from another fails the
  build unless an explicit sentinel or exclusion says why.
- The manifest lists capabilities (maintainer decision, 8 October 2026): the 15 served business routes without a line
  get one, and the four transport operability probes (API root, health, readiness, metrics) are reasoned exclusions
  of the gate.
- `.claude/rules/governance.md` describes the comparison the gates actually perform.

## Capabilities

### New Capabilities

- `public-surface-convergence`: the guarantee that every public HTTP route is served, declared and documented
  consistently, or explicitly excluded with a reason.

### Modified Capabilities

(none — `openspec/specs/` holds no capability yet)

## Impact

- `docs/openapi/morpheus-v1.yaml` and `morpheus-v1-remote-m26.yaml` (`discover`, local and remote),
  `morpheus-v1-policy-m25.yaml` (`policy-activations`, `policy-overrides/remove`, found by the gate).
- `contracts/public-surfaces.tsv`: 15 lines added.
- `morpheus-api`: a package-private read accessor on `MorpheusRemoteRoutePolicy`.
- `morpheus-architecture-tests`: new convergence gate; `morpheus-api` route registration read by it.
- `.claude/rules/governance.md`.
