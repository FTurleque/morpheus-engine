# Tasks

Implementation is a separate lot, started after this specification is reviewed. It shares no file with the other
changes of sprints 6, 7 and 13. Nothing below is done by the pull request that introduces this change.

## 1. Measure before changing

- [x] 1.1 Count the file-system queries one scan makes for the boundary on a tree of `f` files at depth `d`, for two
      values of `d`, by instrumenting a scratch copy of `WorkspacePathBoundary`. Proof: the four numbers and the
      formula they fit; the audit's reasoning (`2 x d` segment checks per file, four queries per segment) is confirmed
      or corrected. Nothing was measured on 2026-10-13: the cost in the proposal is read from the code.
- [x] 1.2 Read `M19PerformanceGate`'s inventory-scan metric on the unchanged code, on Windows and on Linux, and the
      depth of its fixture. Proof: the two figures and the depth; if the fixture is shallow, say that M19 cannot show
      the saving and that the count test is the proof.
- [x] 1.3 Decision 3, option (b): on Windows and on Linux, compare the real path of a scanned file with the physical
      workspace root followed by the walker's relative path, for an ordinary tree, a root given in another letter
      case, an 8.3 short name, a substituted drive, a junction or mount above the workspace root, and a link at each
      level below it. Proof: the table of cases, equal or different, per platform. One legitimate case that differs
      rejects (b) and the lot implements (a).
      Result, measured on 2026-10-21 with `Path.toRealPath`, equal unless said:
      Windows - ordinary tree, root in another letter case, root given as an 8.3 short name, root on a `subst` drive,
      symbolic link above the root and junction above the root: **equal**; a junction below the root, to a place inside
      the workspace or outside it: **different** (the junction is not a symbolic link for Java; by reading the check, which
      this measurement did not run, it accepts the first and refuses the second, and the walker does not descend into
      either);
      Linux (WSL) - ordinary tree and a symbolic link above the root: equal; a link at a file: different.
      A legitimate case differs, so (b) is rejected and (a) stays, as implemented.
- [x] 1.4 List the call sites of `WorkspacePathBoundary` in the scanner and the watcher. Proof: the list with line
      numbers (ten on 2026-10-13: four in the scanner, six in the watcher), re-measured.

## 2. Reproduce before fixing

- [x] 2.1 The count test: same files, depth `d` and `2d`; a directory with many files. Proof: red on `HEAD`, with the
      measured growth.
- [x] 2.2 The mid-scan replacement tests, using the verification-computer seam to replace an ancestor directory, then
      the file, by a link between the fingerprint and the re-validation. Proof: green on `HEAD` - they pin a guarantee no test holds
      today, and they must stay green. Skipped by assumption where the platform cannot create a link, as the existing
      link tests are.
- [x] 2.3 The two-scans test and the watcher test of the last requirement. Proof: green on `HEAD`.
- [x] 2.4 An inventory-equality test on a fixture of `experiments/m0/fixtures/`: entries and fingerprints before and
      after. Proof: the inventory captured on `HEAD` is the expected value.

## 3. The change

- [x] 3.1 The per-scan boundary: physical root resolved once, validated directories recorded as they are entered, the
      own-segment check for an entry whose parent is validated, the full walk otherwise. Proof: 2.1 passes for the
      checks made before reading; 2.2, 2.3, 2.4 unchanged and green.
- [x] 3.2 The re-validation after fingerprinting, by option (a) or (b) according to 1.3. Proof: 2.2 green; the constant
      of the second requirement is the one the count test asserts.
- [x] 3.3 Line 39: kept with the loop under (a), with a comment saying which replacement it refuses; gone with the loop
      under (b). Proof: the diff; it is not removed as unreachable.
- [x] 3.4 Break each property once, then revert: answer the re-validation from the validated set (2.2 fails on the
      replaced ancestor); keep the validated set in a static field (the two-scans test fails); validate every ancestor
      for each file again (2.1 fails).

## 4. Verification

- [x] 4.1 `./mvnw clean verify` on Windows and on Linux - the link tests only run fully where links can be created.
      Proof: green on both, with the number of tests skipped by assumption on each.
- [x] 4.2 `IntermediateSymlinkBoundaryTest`, `LocalSourceInventorySecurityTest`, `SourceInventoryScanDisclosureTest`,
      `LocalSourceWatcherBudgetTest`. Proof: green unmodified.
- [x] 4.3 M19: `scripts\validate.cmd m19` and its `.sh` twin. Proof: the inventory-scan figure before (1.2) and after.
- [x] 4.4 Run the filter of `.claude/skills/adr-author`. Proof: its answer in the pull request. No ADR if the three
      first requirements hold as written; an ADR and a paragraph of `SECURITY.md` if open question 1 was answered yes.
- [x] 4.5 Pull request description: the finding, the two points where this change departs from its remedy (line 39,
      the second call per file), the counts of 1.1 before and after, the table of 1.3.
