# Design

## Context

- `RepositoryDocumentationCoherenceTest` already reads the `<modules>` block of the root POM (`MODULE_BLOCK`, `:31`) and
  already refuses a perishable total on the AI governance surfaces. `.claude/CLAUDE.md` says: count the `<module>`
  entries, never quote a total from memory.
- `rules/documentation.md` separates current pages from dated records: plans, validations, audits, release notes keep
  the figures of their date. Inside a current page, a dated measurement is a record too — `docs/developer/CODE_AUDIT.md`
  reports "16 modules" in tables that state when and how they were measured.
- `AggregateCoverageGateTest` derives the coverage population from the root POM and names it in its evidence: the
  repository's own answer to "which modules" is a derivation, not a number.

## Goals / Non-Goals

**Goals**: a page that shows the reactor shows all of it; no current page carries a module total that the next module
makes false.

**Non-Goals**: generating the pages; the module descriptions and the families of the arc42 table, which are prose;
dated records and dated measurements; the other perishable numbers (ADR totals, schema version, ratchets), which have
their own rules or belong to sprint 10.

## Decisions

1. **A list is checked against the POM; a total is forbidden.** The audit offers either. Both are needed: a complete
   list with a stale total is still wrong, and a page without a total can still omit a module. *Rejected*: allowing the
   total when it equals the POM's count — it is the copy `rules/meta.md` calls perishable, and it makes every new module
   edit pages that say nothing about it.

2. **A page lists the reactor when it names more than half of the declared modules.** The rule finds its pages instead
   of holding their names: among the current developer and architecture pages, a page that names more than half of the
   modules is describing the reactor and must name them all. A page that mentions three modules in passing is not
   concerned. The rule also refuses a `morpheus-*` name that is presented as a module in such a list and that the POM
   does not declare. *Rejected*: a fixed list of four pages — the fifth page would be born unchecked.

3. **The scanned set is the current developer and architecture documentation.** `docs/developer/**`,
   `docs/architecture/**` outside `adr/`, and the entry pages `README.md`, `CONTRIBUTING.md`, `docs/README.md`. The ADRs
   are decisions of their date; `docs/project/**` is steering and records; `docs/user/**` does not speak of modules.

4. **A dated measurement inside a current page is recognised by its own date.** The total is refused in a sentence or
   a table row that states the present; a table or paragraph that carries the date or the commit of its measurement is
   a record. The lot lists the occurrences first (task 1.3) and writes the recognition from the shapes that exist,
   rather than from a rule imagined here. If a shape cannot be told apart by text, the occurrence is reworded to carry
   its date — never exempted by a file name.

5. **Textual assertion, in the documentation coherence family.** Intent: "a module total, or an incomplete module
   list, must not appear on a current page". The targets are `.md` and `.xml`: text is the only hold (ADR-0103).

6. **No ADR.**

## Risks / Trade-offs

- The "more than half" threshold is a heuristic. It is proven on the four pages known today and on a page that names a
  few modules in passing; the rule fails, rather than passes, when it finds no page that lists the reactor.
- The arc42 table groups modules by family. Adding `morpheus-coverage-report` to it is a line of architecture
  documentation, written by someone who says what the module is for — the rule only says it is missing.
- Refusing the total will flag sentences that were true. That is the point of the rule, and the same trade the AI
  governance surfaces already accepted.

## Open Questions

1. **Should `docs/developer/SPOTBUGS.md` keep "16 modules carry production classes"?** It is a derived count the gate
   already computes. Recommendation: replace it by the rule ("every module with classes under `src/main/java`") and
   leave the table that names the two modules without production classes.
