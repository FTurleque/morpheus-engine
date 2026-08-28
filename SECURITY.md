# Security Policy

MORPHEUS treats its local API, remote HTTPS facade, provider plugins, MCP processes, filesystem boundaries, SQLite state, and release supply chain as security-sensitive surfaces.

## Supported code

Security fixes are developed on `develop`, qualified by the repository CI gates, and promoted through `main` and published releases. Users should run the latest published release for production use. Development branches other than `develop` are not supported deployment targets.

## Reporting a vulnerability

Do not publish exploit details, credentials, tokens, private paths, or other sensitive reproduction material in a public issue.

Prefer GitHub private vulnerability reporting from the repository **Security** tab when that facility is available. If private reporting is unavailable, open a minimal public issue stating that you need a secure reporting channel, without including exploit details or secrets.

A useful report includes the affected version or commit, impacted component, expected security boundary, reproducible preconditions, and the observed impact. Redact all credentials and private data.

## Remote server security

The remote API must be exposed only through `MorpheusRemoteHttpServer` with TLS and Bearer authentication. Authorization is fail-closed through an explicit `(HTTP method, route) -> minimum role` registry. Unknown remote routes are denied rather than inheriting authority from their HTTP verb.

Remote identity tokens contain 256 bits of random material and only their SHA-256 hashes are persisted. Identity records may optionally carry an ISO-8601 expiration instant. Expired credentials are rejected during authentication. Legacy three-field records remain supported and are intentionally non-expiring; operators should rotate them to expiring credentials when automatic lifetime bounds are required.

Credential material printed by `server identity create` or `server identity rotate` must be captured once into an appropriate secret store. Never commit generated tokens, keystore passwords, or authentication files containing operational credential hashes.

## External code trust boundary

Provider plugins and configured MCP peers execute in child processes/classloaders with integrity checks, bounded resources, minimized inherited environment, and lifecycle cleanup. These controls are **not an operating-system sandbox**. Approved plugins and MCP peers execute with the filesystem and network permissions of the MORPHEUS operating-system account and must therefore be treated as trusted code.

For deployments that require execution of untrusted third-party code, isolate MORPHEUS or the external process with operating-system/container controls and a dedicated least-privilege account.

## Supply chain

Repository CI includes dependency convergence checks, OWASP Dependency-Check, CodeQL, CycloneDX SBOM generation, exact-head qualification, and Linux/Windows release provenance. GitHub Actions used by release workflows are pinned to immutable commit SHAs.

A release should not be considered provenance-qualified until the actual tagged release workflow has completed successfully and its expected checksums and attestation bundles have been published.
