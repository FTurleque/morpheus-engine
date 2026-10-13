# Design

## Context

- A scan walks each source root with `Files.walkFileTree`, without following links. `preVisitDirectory` skips ignored
  directories and symbolic links, validates the directory against the workspace boundary, then applies the depth and
  directory budgets. `visitFile` skips links, validates the file, applies the budgets, fingerprints the file, validates
  it again, re-reads its attributes and compares identities.
- A boundary failure adds a `WORKSPACE_BOUNDARY_ESCAPED` failure, exhausts the shared budget and terminates the walk.
- `WorkspacePathBoundary` is a static utility with one method. It returns silently when the workspace or a segment does
  not exist: a path that is not there cannot escape.
- The scanner already has a test seam that runs code between the fingerprint and the re-validation: the optional
  verification computer (`LocalSourceInventorySecurityTest` uses it to mutate a file mid-scan).
- On Linux a real-path resolution walks every component in the kernel; on Windows it opens the path once. "One
  resolution" is one call on both, not the same amount of work.

## Goals / Non-Goals

**Goals**: boundary work per scan proportional to the entries visited; no refusal lost; the mid-scan replacement
guarantee stated and tested; a deterministic proof of the cost.

**Non-Goals**:

- The watcher. Its checks are on single paths as events arrive; a watcher that remembered validated directories across
  events would be caching exactly what an event says may have changed.
- Remembering anything between two scans, or between two source roots of one scan beyond the workspace root itself.
- Changing the scan budgets, the fingerprinting or the identity re-check.
- A sandbox. This boundary is a check, as `SECURITY.md` says of the others; nothing here claims atomicity against a
  process racing the scan.

## Decisions

1. **The validated state belongs to one scan.** The boundary becomes an object created at the start of a scan and
   dropped at its end; it holds the physical workspace root, resolved once, and the directories this scan has validated
   and entered. The static single-path method stays for the watcher and for the source-root check.
   *Rejected*: a process-wide cache keyed by path - it would outlive the file-system state it describes.

2. **Before reading: validate the entry's own segment against a validated parent.** When the walker hands over a
   directory or a file, its parent has been entered by this scan, so its ancestors are in the validated set; the check
   asks the four questions of today for the last segment only. A candidate whose parent is not in the set - the source
   root itself, or anything the walker did not descend to - takes the full walk. The saving is the audit's:
   from `d` segment checks to one, for every directory and for the first check of every file.

3. **After fingerprinting: the question is "still?", so the set is not consulted.** Two ways to ask it:
   - *(a) the full segment walk, as today.* Cost stays `d` per file for this call; the scan goes from `2d + ...` to
     `d + 1` segment checks per file. Certain, and already tested by the existing suite.
   - *(b) one resolution of the whole path.* If no segment is a link, the real path of the file equals the physical
     workspace root followed by the path the walker produced; any link in the chain, wherever it points, makes the two
     differ. One call per file instead of `d`.
     **Unverified premise**: that the equality holds for every legitimate tree on both platforms - case-insensitive
     volumes, 8.3 short names, substituted drives, a workspace reached through a junction or a mount above the root.
     A false refusal would terminate a scan that passes today.
   Recommendation: (b) if task 1.3 proves the equality on Windows and Linux for the cases listed, otherwise (a). The
   requirements are written so that both satisfy them; only the constant differs.
   *Rejected*: consulting the validated set here too. It is the literal form of the audit's remedy and it turns the
   re-validation into a no-op for every ancestor.

4. **Line 39 stays, or goes with the loop it belongs to.** Under (a) the loop is kept and so is its second link test.
   Under (b) the loop no longer runs after the fingerprint and the equality holds the same property. In neither case is
   the condition removed as dead. It cannot be reached by a deterministic test without a hook between two adjacent
   lines, and no such hook is added for it: the replacement tests of task 2.2 hold the guarantee at the grain a test
   can stage, and a comment on the line says what it is for.

5. **The proof of cost is a count, not a duration.** The per-scan boundary reports how many physical resolutions and
   link tests it made; a test in the same package scans two trees that differ only in depth and asserts the bound. A
   wall-clock budget on a deep tree would measure the disk and the platform. `M19PerformanceGate` already holds a time
   budget for the inventory scan and is read before and after.
   The counter is a package-private accessor on an object the scanner already owns, not an injected file-system
   abstraction: `rules/code-style.md` refuses an abstraction with one implementation.

6. **Enforcement is behavioural.** Every property here is about what a scan does on a real directory tree: unit tests
   in `morpheus-application`, with links where the platform supports them and an explicit assumption where it does not
   (the existing tests already do this). No ArchUnit rule and no textual assertion: there is no dependency to forbid
   and no string whose presence means the property holds.

## Risks / Trade-offs

- **A wider window for an ancestor replaced between two entries.** Today an ancestor swapped for a link is noticed at
  the first check of the next entry; after this change, at the re-validation of the next *file* fingerprinted beneath
  it. No file enters the inventory unvalidated in either case; a directory entered beneath the swapped ancestor may be
  listed before the scan stops. Listing is not reading, and the scan result is a failure either way. The trade is
  stated in the requirement so that it is a decision and not an accident.
- **Windows without the right to create links.** The tests that need a link are skipped there by assumption, as today.
  The count test needs none and runs everywhere.
- **Deep trees are rare.** The saving is proportional to depth; on a workspace four levels deep it is small. The change
  is justified by the worst case the policy allows, and by removing work that buys nothing.

## Open Questions

1. **May the re-validation after fingerprinting trust the validated ancestors?** That is the maximum saving and the
   audit's literal remedy. Recommendation: no. If the maintainer decides yes, the third requirement is rewritten to say
   that only the file's own segment is re-validated, and the decision needs an ADR and a paragraph in `SECURITY.md`
   beside the other documented best-effort limits, because a guarantee is being narrowed.
2. **Should the watcher's pair of checks at `LocalSourceWatcher.java:143-144` share the directory's validation?** It
   validates a directory, then a child of it, walking the same segments twice per event. Recommendation: leave it; the
   cost is per event, not per file of a tree, and the watcher is where staleness matters most.
