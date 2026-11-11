# Design

## Context

- Two scales, two gates, two caps each (ADR-0104). A cap is "the lowest reproducible measurement of both platforms";
  a ratchet is placed under it, at a distance the comments express in lines and branches.
- Each gate writes a summary whose first line names its scale: `m21-per-module-coverage-summary.txt` and
  `m21-aggregate-coverage-summary.txt`. A summary carries the covered and missed counts, the ratios, the cap, the
  ratchet, and — for the aggregate scale — the population. `ci.yml` uploads both in `m21-integrity-<OS>` for 14 days.
- A qualification, as practised since 15 September: two complete runs per platform at the same commit (the `push` run
  of `develop` and the `pull_request` run of the promotion), the cap taken on the lowest of the four.
- The summaries are a few lines of text each. A qualification is eight of them: two scales, two platforms, two runs.

## Goals / Non-Goals

**Goals**: a cap can be checked against what was measured for as long as it is in force; a cap that no evidence backs
cannot be written; a developer knows from the guide which kind of raise they are attempting.

**Non-Goals**: changing a cap, a ratchet, the population or the scales; enforcing the distance a ratchet keeps from its
cap (open question 2); keeping every promotion's artifacts, as the audit suggests — only a qualification in force is
evidence of anything; longer artifact retention as the answer (it postpones the expiry and still leaves the constant
typed by hand).

## Decisions

1. **The evidence is the summaries themselves, kept as files.** They are what the comments already cite, they are
   small, and they name their scale, their counts and, on the aggregate scale, their population. Each kept file is stored
   with the platform, the run it came from and the commit. *Rejected*: the JaCoCo XML reports — megabytes, and the gate
   that produced the summary from them is already tested. *Rejected*: a retention of 90 days — still an expiry.
   *Rejected*: the audit's archive at every promotion — it keeps what no cap rests on.

2. **A cap equals the lowest ratio of its scale in the kept evidence, and a test says so.** The constant stays in the
   gate, with its comment; the test fails when the constant differs from the lowest measurement, to the precision the
   summaries carry. *Rejected*: deriving the cap from the files and deleting the constant — the cap would move by
   committing a file, with no line of code to review; the constant and the file must agree, which makes a change of
   either visible.

3. **A qualification is complete or refused.** The evidence of a scale must hold both platforms, at least two runs
   each, all at one commit and — on the aggregate scale, which names it — one population. The lowest must not come from
   a set that lacks a platform. This is the rule `rules/testing.md` states in prose.

4. **Only the qualification in force is required.** When a cap is requalified, the new set replaces the old one in the
   same change; Git keeps the previous set. The comments keep their history, as dated records do.

5. **The guide explains the two kinds of raise and points to the summary.** It does not say "the margin is nil": that
   is a perishable statement of the kind `rules/meta.md` forbids. It says that a ratchet rises by decision while it
   stays under its cap and keeps the distance the rule asks, that a cap rises only by requalification, and that the
   summary of each gate shows the measurement, the cap and the ratchet side by side.

6. **ADR-0104 is amended.**

## Risks / Trade-offs

- The evidence of 15 and 22 September cannot be recovered. Only the qualification in force is kept, so nothing is
  lost that the rule needs — provided the 9 October artifacts are downloaded in time. If they are not, the first task
  becomes a requalification, on the current head, with no change of value expected.
- A summary copied by hand could be edited. The comparison binds the constant to the file, not the file to the truth;
  the file names its run, which stays checkable while the run exists, and review reads a file that never changes after
  it is added.
- Eight small files per qualification in a governance directory is more surface to keep coherent. The completeness
  rule is what keeps it so.

## Open Questions

1. **Where do the files live?** `config/` beside the ratchets they justify, or `docs/project/validation/` with the
   proofs of milestones. Recommendation: `config/`, in a directory named for the qualification. A gate reads them, which
   makes them an input of the build rather than a page; and the tree of `docs/` is frozen with one kind of file per
   directory.
2. **Should the distance between a ratchet and its cap be enforced?** The kept evidence holds the counts needed to
   compute the spread between platforms, so a test could require a ratchet to keep three times that spread. The
   comments use the widest spread ever observed, not the current one, and that reference is no longer in any artifact.
   Recommendation: not in this change. State the convention in the guide; enforce it only if a ratchet is ever raised
   into the last thousandths.
