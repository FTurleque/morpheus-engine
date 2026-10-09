# Tasks

## 1. Reproduce before fixing

- [x] 1.1 Add a golden test in `morpheus-store-sqlite` pinning the canonical (LF) SHA-256 of V001–V020 as literals; verify it fails on the current Windows checkout (raw CRLF digest) and passes once 2.1 lands
- [x] 1.2 Add a test that creates a database, rewrites its `schema_migrations.checksum` to the digest of the other line-ending convention, and reopens it; verify it fails today with "migration history mismatch for version 1"
- [x] 1.3 Add a test that restores a backup whose ledger carries a digest the runtime refuses; verify today the live database is discarded and the restored one does not open

## 2. Canonical checksum

- [x] 2.1 Compute the migration checksum on CRLF→LF normalised text and accept the CRLF-text digest as the only alternative; verify 1.1 and 1.2 pass and a one-byte content change is still refused
- [x] 2.2 Keep recorded checksums untouched when accepted; verify by reading `schema_migrations` after reopening in 1.2
- [x] 2.3 Update `R2UpgradeCompatibilityTest` so its expectation no longer derives from the resources it checks; verify it fails if a migration's content changes

## 3. Restore verification

- [x] 3.1 Make `verify` compare the backup ledger (version, name, accepted digest) with the runtime's migration manifest and refuse with a named reason; verify 1.3 now leaves the live database intact and opening
- [x] 3.2 Add the cross-platform restore scenario (backup ledger with the other convention) and verify it restores and opens

## 4. Checkout policy and documentation

- [x] 4.1 Add `*.sql text eol=lf` to `.gitattributes` and renormalise; verify `git ls-files --eol morpheus-store-sqlite/src/main/resources` reports `w/lf` on Windows
- [x] 4.2 Amend ADR-0021 with the normalisation and acceptance rule, and document restore refusal reasons for operators; verify `AdrIndexCoherenceTest` and `RepositoryDocumentationCoherenceTest` pass

## 5. Cross-platform evidence

- [x] 5.1 Run `./mvnw test -pl morpheus-store-sqlite` on Windows and on Linux (CI or WSL) and record both runs
- [x] 5.2 Open a database created by the Windows portable build with the Linux portable build of the same commit (and the reverse); record the result in the audit document

Note on 5.2 (agreed with the maintainer on 2026-10-08): run with the CLI jar (`--db`) built on each platform instead of the
jpackage images, which would overwrite `dist/`; the checksum logic lives in the jar. The released-style Windows build
from `dist/` (CRLF ledger) was added as the source of the decisive case.
