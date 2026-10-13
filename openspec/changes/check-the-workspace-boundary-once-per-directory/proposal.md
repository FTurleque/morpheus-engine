# Proposal

## Why

AUD-PRF-08 (sprint 7, Medium) reports that the physical workspace-boundary check of a source scan walks every segment
of a path, resolving each, and is called twice per file. It is filed under "the SQL of detail" but touches no SQL: it
is `morpheus-application`, and shares nothing with the other findings of the sprint. Re-read on `develop` at `8b26d3d7`
on 2026-10-13, the finding **holds on its cost and is wrong on one point of its remedy**.

- `WorkspacePathBoundary.requireContained` (`:14-43`) resolves the workspace root (`:27`), then for each segment of the
  candidate below it (`:30-42`) asks whether it exists, whether it is a symbolic link, resolves its real path, and asks
  again whether it is a symbolic link. Four file-system queries per segment, one of which resolves the whole chain
  again.
- `LocalSourceInventoryScanner` calls it once per source root (`:155`), once per directory entered (`:174`), and twice
  per file: before reading (`:206`) and after fingerprinting (`:260`). The audit's line numbers (178, 212, 265) have
  shifted; the four call sites are the same.
- The walker descends: when a file is visited, every directory above it has been validated by `preVisitDirectory`
  during the same scan. For a file `d` segments below the workspace the two calls redo `2 x d` segment checks whose
  answers the scan already holds. With the defaults of `SourceScanPolicy` (`:22-24`: depth 128, 50 000 directories,
  50 000 files) the product `files x depth` is what the scan pays; the cost is reasoned from the code, as in the audit,
  not measured.
- **The second `Files.isSymbolicLink(current)` of line 39 is not dead code.** The audit calls it unreachable because
  line 35 has already thrown for a link. That holds only if the file system does not change between the two lines; the
  class exists because it can. The second test is what refuses a segment swapped for a link that resolves *inside* the
  workspace between the first test and the resolution. No test covers it, which is a gap, not a proof of deadness.
- **The second call per file is not a duplicate.** `:260` runs after the fingerprint, before the identity re-check of
  `:261-262`: it is the re-validation that catches a segment replaced while the file was being read. Memoising it away,
  as a literal reading of the audit's remedy would, removes a check the scan was given on purpose
  (`1869c1b7`, "enforce physical workspace boundaries").
- `LocalSourceWatcher` uses the same method on single paths (`:66`, `:143-144`, `:168`, `:176`, `:198`); it does not
  scan and is not what the finding measures.

So the cost is real and the saving is available, but only for the answers the scan legitimately already holds: the
ancestors it has entered. What must not be cached is the question the second call asks - "is this still true now?".

## What Changes

- During one scan, a directory the scan has validated and entered is not validated again for each entry beneath it:
  the boundary work of a scan grows with the number of entries visited, not with entries times depth.
- Everything refused today is still refused, with the same failure code and the same effect on the scan.
- The re-validation after fingerprinting keeps its meaning: a segment replaced by a link while the scan runs is still
  refused before the file enters the inventory. That property, which no test pins today, gets one before the code moves.
- The scan's validated state lives and dies with the scan: nothing is remembered between two scans, and the watcher's
  single-path checks are unchanged.

## Capabilities

### New Capabilities

- `workspace-boundary-scan-cost`: what the source scan refuses at the workspace boundary, what it may remember within
  one scan, and how much boundary work a scan may do per entry.

### Modified Capabilities

(none)

## Impact

- `morpheus-application`: `WorkspacePathBoundary`, `LocalSourceInventoryScanner`, their tests
  (`IntermediateSymlinkBoundaryTest`, `LocalSourceInventorySecurityTest`). No port, no adapter.
- No CLI command, MCP tool or HTTP route changes; `contracts/public-surfaces.tsv` and the OpenAPI files are untouched.
  No ratchet. An ADR and a line of `SECURITY.md` only if the maintainer chooses the weaker guarantee of open question 1.
- Independent of sprint 6 and of the two other changes of sprint 7: no shared file. It can be implemented in any order.
- The M19 inventory-scan budget (`M19PerformanceGate`) is the existing time proof; this change adds a deterministic
  count, not a second time budget.
