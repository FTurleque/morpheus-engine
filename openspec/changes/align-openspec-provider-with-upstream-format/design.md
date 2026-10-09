# Design

## Context

`OpenSpecChangeMetadataReader` requires `^#\s+Proposal:\s*(.+)` and a `## Intent` section; the upstream
`spec-driven` template (OpenSpec CLI 1.14.1, observed in this repository on 2026-10-08) writes `# Proposal`,
`## Why`, `## What Changes`, `## Capabilities`, `## Impact`. The change, constraint, design-decision, task and
delta categories are all derived from the change read, so one proposal the reader rejects fails five categories.
`NormalizedProjectContent` refuses duplicated identities with an `IllegalArgumentException` constructed outside any
`try` in `OpenSpecSpecificationContentReader.read`. `SafeWorkspaceFileResolver` decodes strict UTF-8 and keeps
U+FEFF; the schema regex and title regexes then miss line 1. The M0 fixtures (`experiments/m0/fixtures/openspec-*`)
were written by hand in the M0 dialect and are the only workspaces the tests read.

## Goals / Non-Goals

**Goals:**
- Read upstream `spec-driven` changes produced by the CLI version named in the documentation.
- Keep reading every M0-dialect fixture unchanged (no regression on existing users).
- Make every reader failure an attributed FAILED, never an escaping exception.

**Non-Goals:**
- Supporting OpenSpec schemas other than `spec-driven`.
- Reading `.openspec.yaml` or `skip_specs` (separate change if wanted).

## Decisions

1. **Read both dialects (recommended, to be confirmed by the maintainer).** The proposal's intent is taken from
   `## Intent` when present, otherwise from `## Why`; the title from `# Proposal: <title>`, otherwise from the
   change directory name. Alternative: keep the M0 dialect only and make the probe report a dialect diagnostic —
   rejected as the default because the provider would then not read the tool it is named after; it remains the
   fallback if the maintainer prefers not to widen the contract.
2. **Pin a CLI-generated fixture.** A fixture produced by `openspec new change` + the CLI templates is committed with
   the CLI version recorded beside it, so a future template change is noticed by a failing test rather than by a
   user.
3. **Detect duplicates in the readers**, where the file is known, rather than catching the `IllegalArgumentException`
   from `NormalizedProjectContent`: catching would lose the file attribution.
4. **Strip one leading BOM at decoding** (`SafeWorkspaceFileResolver`), so every provider gains it, rather than in
   each regex.
5. **Reuse the delta reader's fence and section handling** for current specifications instead of a second
   implementation.

## Risks / Trade-offs

- [Widening the accepted dialect changes what MORPHEUS publishes for some workspaces] → only workspaces that are
  FAILED today change; M0 fixtures are pinned by their existing tests.
- [Upstream templates evolve] → the CLI-generated fixture names its CLI version; ADR-0028 records the supported
  version range.
- [BOM stripping in a shared resolver also affects markdown and synthetic providers] → both currently fail or
  silently skip on a BOM (audit PRV-AUD-2), so the change only turns failures into reads.

## Open Questions

- Which OpenSpec CLI version range is promised in the user documentation (1.14.x only, or "current")? Does not change
  the tasks; it changes one sentence of the ADR amendment.
