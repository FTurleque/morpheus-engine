---
name: condensing-adr-decision-sections
description: How to shorten an ADR's Decision / Do's and Don'ts without losing a rule, and the cross-references a renumbering breaks
metadata:
  type: feedback
---

When an ADR's `Decision` or `Do's and Don'ts` must shrink to the briefing budget, keep every rule in the briefed
sections and move reasons, diagrams, tables and examples to the end of `Context`; then check mechanically that
nothing left the file.

**Why:** a condensed list silently drops literals (refusal messages, security flags such as
`-DnvdApiKeyEnvironmentVariable=NVD_API_KEY`, invariant formulas), and renumbering a Decision breaks numbered
references elsewhere: "ADR-0106, §4" in Javadoc, "décision 9" inside the same ADR, "ADR-0089 §5" in another ADR,
"ADR-0018 §15" in arc42 and the risk register. No test reads those references.

**How to apply:**
- Compare every code span and `ADR-NNNN` of the old briefed sections (`git show HEAD:<file>`) with the whole new
  file; restore any literal that vanished.
- After renumbering, grep the repo (docs, `.claude`, `openspec/specs`, `src/`) for `ADR-NNNN §`, `ADR-NNNN, point`,
  `décision N`, `§N` and fix each pointer.
- Delegated edits: forbid `sed -i` (it strips CRLF from the working tree); a subagent stopped mid-edit can leave text
  removed from `Decision` and not yet added to `Context`, so resume it rather than restart, and diff before trusting.
- Never `git add -N .` to inspect: it marks the maintainer's untracked audit files; undo with `git reset`.
