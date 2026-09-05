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

### Response lifetime and abandoned clients

Concurrency permits, the proxied-response memory budget and the request-body deadline are all taken before a
response is written and released after it. An authenticated client that stops reading therefore holds every one
of them for as long as it keeps the socket full, without sending anything malformed; bounding the response size
does not help, because the cost is time rather than memory.

Responses are written under two budgets: a stall budget rearmed by each block that reaches the client, and a
total budget for the whole response. `jdk.httpserver` exposes no write timeout, and its only response deadline is
the undocumented `sun.net.httpserver.maxRspTime` system property, which MORPHEUS deliberately does not rely on.
The deadline is enforced instead by interrupting the blocked writer: a thread blocked on a
`java.nio.channels.InterruptibleChannel` closes that channel and receives `ClosedByInterruptException`, which is
specified behaviour rather than an implementation detail.

**Residual limitation.** An abandoned response is aborted, not completed: the client sees a truncated body on a
closed connection and receives no error envelope, because the connection that would carry it is the resource
being reclaimed. The event is counted separately from a request-body timeout in `GET /api/v1/server/status`
(`responseWriteTimeouts`), since only a client that stopped receiving holds a slot for as long as it stays
connected.

## External code trust boundary

Provider plugins and configured MCP peers execute in child processes/classloaders with integrity checks, bounded resources, minimized inherited environment, and lifecycle cleanup. These controls are **not an operating-system sandbox**. Approved plugins and MCP peers execute with the filesystem and network permissions of the MORPHEUS operating-system account and must therefore be treated as trusted code.

For deployments that require execution of untrusted third-party code, isolate MORPHEUS or the external process with operating-system/container controls and a dedicated least-privilege account.

### Process-tree termination

The two external-code boundaries give different guarantees, because only one of them has a MORPHEUS-controlled root process.

**Provider plugin probes are guaranteed.** The probe runs under `ProviderPluginProbeWorker`, which is MORPHEUS code. It terminates its own process subtree before it exits, and it does so while that subtree is still enumerable. A plugin therefore cannot leave a process behind by spawning one and returning immediately. The parent additionally terminates every descendant it observed, which covers the worker being killed on timeout. The residual gap is a worker killed by `SIGKILL`/`TerminateProcess` or lost to a JVM crash in the same instant it spawned a process; that path falls back to parent-side observation and is best-effort.

**MCP peer descendants are best-effort.** A configured MCP peer is not MORPHEUS code, so nothing inside it can be required to clean up. MORPHEUS observes the peer's process tree continuously, retains every `ProcessHandle` it sees, and force-terminates all of them on shutdown — including descendants that outlived the peer. The peer process itself is always terminated. What is **not** guaranteed is a descendant that is spawned and orphaned inside a single observation interval: once the peer exits the operating system re-parents that process, and no portable Java API can still attribute it to the peer. Closing this window requires an OS containment object created at spawn time (a Windows Job Object, a POSIX process group, or a cgroup); Java 21 exposes none of these, and MORPHEUS does not currently take a native dependency to obtain them.

Neither guarantee is a sandbox. Operators who need a hard bound on what an external process can leave running must run MORPHEUS under an OS-level container or job/cgroup that owns the whole tree.

## Supply chain

Repository CI includes dependency convergence checks, OWASP Dependency-Check, CodeQL, CycloneDX SBOM generation, exact-head qualification, and Linux/Windows release provenance. GitHub Actions used by release workflows are pinned to immutable commit SHAs.

A release should not be considered provenance-qualified until the actual tagged release workflow has completed successfully and its expected checksums and attestation bundles have been published.
