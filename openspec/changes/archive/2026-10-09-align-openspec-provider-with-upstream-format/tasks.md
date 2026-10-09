# Tasks

## 1. Reproduce before fixing

- [x] 1.1 Add a fixture generated with the OpenSpec CLI (`openspec init` + one change from the `spec-driven` templates), record the CLI version in its README, and a test reading it; verify it fails today with "OpenSpec proposal has no Proposal title"
- [x] 1.2 Add a test with a requirement duplicated under `## ADDED Requirements`; verify `read()` throws `IllegalArgumentException("duplicate requirement delta identity…")` today
- [x] 1.3 Add probe and reader tests with a UTF-8 BOM at the start of `config.yaml` and of `spec.md`; verify today's INVALID "does not declare a schema" and "has no title"
- [x] 1.4 Add current-specification tests for a `### Requirement:` inside a code fence and for `## Notes` after the last requirement; verify today's phantom requirement and appended notes

## 2. Dialect support

- [x] 2.1 Confirm decision 1 of design.md with the maintainer (both dialects, or M0 only with a probe diagnostic) and record it in the ADR-0028 amendment
- [x] 2.2 Implement the chosen proposal reading; verify 1.1 passes and every existing M0-fixture test still passes

## 3. Failure containment

- [x] 3.1 Detect duplicated requirements and scenarios in the delta and current readers with a file-attributed INVALID_SOURCE; verify 1.2 reports FAILED with a workspace-relative source and no exception
- [x] 3.2 Make one malformed change report its own proposal path without hiding the outcome of valid changes; verify with a two-change fixture

## 4. Encoding and structure

- [x] 4.1 Strip a single leading BOM at decoding and accept a quoted `schema:` scalar; verify 1.3 and the markdown and synthetic BOM cases
- [x] 4.2 Align current-specification section and fence handling with the delta reader; verify 1.4

## 5. Integration

- [x] 5.1 Run `./mvnw test -pl morpheus-provider-openspec,morpheus-provider-markdown,morpheus-provider-synthetic` and `-pl morpheus-architecture-tests -Dtest=ProviderAntiLockInTest`; record results
  - 2026-10-08: openspec 84 (5 skipped, pre-existing), markdown 10, synthetic 31, application 363, `ProviderAntiLockInTest` 3 - 0 failure
- [x] 5.2 Read this repository's own `openspec/` with the built CLI (`morpheus` sync on the repository) and verify no category is FAILED
  - 2026-10-08, built `morpheus-cli-1.2.1-all.jar`: no category FAILED (6 changes, 12 deltas, 57 tasks READ); strict `sync` published, exit 0
