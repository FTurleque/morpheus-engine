# Proposal

## Why

AUD-SEC-07 (sprint 3, Low) reports that nothing stops a credential file from being committed. Re-read on `develop` at
`0f3b0ad0` on 2026-10-12, the finding **holds**:

- `.gitignore` has no entry for `*.p12`, `*.jks`, `*.pfx`, `*.pem`, `*.key` or `remote-auth*`.
- `SECURITY.md` says "Never commit generated tokens, keystore passwords, or authentication files containing operational
  credential hashes".
- The remote server writes its identity file as `<config>/remote-auth.txt` by default
  (`RemoteApiLaunchOptions.java:135`), and `--auth-file` and `--tls-keystore` accept any path, the working tree
  included.

No such file is tracked today (`git ls-files` finds none). The rule is a sentence with no guard: an operator who starts
the server from the repository with `--tls-keystore ./server.p12` can commit a TLS private key without a warning.

## What Changes

- `.gitignore` ignores the identity file and the usual key-material extensions.
- The repository hardening gate asserts those lines are present and that no tracked file matches them.

## Capabilities

### New Capabilities

- `repository-credential-hygiene`: the repository ignores the files that hold credentials or key material, and the
  build refuses one that is tracked.

### Modified Capabilities

(none)

## Impact

- `.gitignore`, `D2RepositoryHardeningArchitectureTest` (or a sibling), `SECURITY.md` (one clause pointing at the guard).
- No product code. A contributor who needs a test key commits it under a name the patterns do not match, or the test
  generates it - none exists today.
