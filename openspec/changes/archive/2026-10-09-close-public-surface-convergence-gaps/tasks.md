# Tasks

## 1. Measure

- [x] 1.1 Write the bidirectional route comparison (servers' registered routes, manifest `http` column, OpenAPI paths, parameter names normalised) as a failing gate in `morpheus-architecture-tests`; verify it fails today on `GET /api/v1/provider-plugins/discover` and list every other difference it reports
  - 2026-10-08, `PublicHttpRouteConvergenceTest` (6 tests, 3 red): 3 served routes undocumented in OpenAPI (`GET /provider-plugins/discover`, `GET /policy-activations`, `POST /policy-overrides/remove`, all three in the manifest); 19 served routes without a manifest line; no documented or declared route unserved; the local server dispatches all 81 policy routes (proved by adding a phantom route, which fails the binding test)
- [x] 1.2 Present the measured differences to the maintainer and record the decision on manifest exhaustiveness (capabilities only, or every route) in the gate's exclusion list with one reason per entry
  - 2026-10-08, maintainer decision: the manifest lists capabilities; the 15 served business routes get a line, the 4 transport operability probes (`/api/v1`, `health`, `readiness`, `metrics`) are reasoned exclusions in the gate (`NOT_IN_MANIFEST`)

## 2. Converge

- [x] 2.1 Document `GET /api/v1/provider-plugins/discover` (parameters, responses, remote-only, ADMIN or READ role as served) in the provider-plugin OpenAPI document; verify the gate no longer reports it and the OpenAPI bound rules of `governance.md` still pass
  - 2026-10-08: `discover` is not remote-only — served locally with a required `directory`, and remotely with a server-configured directory a client may not supply; documented in `morpheus-v1.yaml` and `morpheus-v1-remote-m26.yaml` (READ). The gate no longer reports it
- [x] 2.2 Resolve every remaining difference by documentation, manifest line or reasoned exclusion; verify the gate passes
  - 2026-10-08: `GET /policy-activations` and `POST /policy-overrides/remove` documented in `morpheus-v1-policy-m25.yaml`; 15 manifest lines added; `PublicHttpRouteConvergenceTest` and the neighbouring manifest/OpenAPI gates green (72 tests)
  - 2026-10-08: a witness probing every route two segments deeper (`anUnroutedPathUnderEveryRouteIsRecognisedAsUnrouted`) proves the unrouted classifier still recognises each router's refusal, and found one router answering a path it does not declare: `GET /api/v1/policy-overrides/<anything>` was served as the list locally while the remote policy refused it. `MorpheusPolicyHttpRoutes.handleOverrides` now serves its own path only (404 `unknown policy-overrides route`)

## 3. Rules

- [x] 3.1 Update `.claude/rules/governance.md` so it describes the comparison the gates perform; verify `RepositoryDocumentationCoherenceTest` passes
