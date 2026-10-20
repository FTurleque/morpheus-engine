# Proposal

## Why

AUD-QUA-04 (sprint 8) reports that the root POM declares 18 modules while four current pages announce 17. Re-read on
`develop` at `8b26d3d7` on 2026-10-13, the finding **holds as written, and reaches a little further**:

- `pom.xml` declares 18 `<module>` entries; the eighteenth is `morpheus-coverage-report`.
- Four pages state "17 modules … 18 projets Maven parent inclus" and list the modules without
  `morpheus-coverage-report`: `docs/developer/BUILD_AND_TEST.md:17-37`, `docs/developer/README.md:32-52`,
  `docs/architecture/arc42/04-strategie-solution.md:27`, `docs/architecture/arc42/05-vue-blocs.md:81-93` (its table of
  families has no row for that module).
- `docs/developer/BUILD_AND_TEST.md:39` adds "Les 18 POMs actifs portent la même version" — the reactor has one more.
- `docs/developer/SPOTBUGS.md:64` states the right total today ("18 modules (19 projets avec le POM parent)", and 16
  that carry production classes). It is right by luck of its date: it is the same perishable statement, one module
  later.
- The detector exists and does not look there. `RepositoryDocumentationCoherenceTest` forbids a module or ADR total
  (`ADR_OR_MODULE_TOTAL`, `:40-41`, whose own comment quotes "17 modules" as the example) on the AI governance surfaces
  only (`GOVERNANCE_PAGES`, `GOVERNANCE_DIRECTORIES`, `:60-61`).

The two entry pages of a developer give a wrong count and an incomplete list. A reader who derives from them the
population of the coverage gates — which is defined from the modules of the root POM — leaves out the module that
carries the canonical gate.

## What Changes

- A current page of the developer and architecture documentation that lists the reactor's modules lists every module
  the root POM declares, and names none it does not declare; the build fails otherwise.
- Those pages no longer state how many modules or Maven projects the reactor has; the build refuses the total, as it
  already does on the AI governance surfaces.
- The four pages and the SpotBugs guide are corrected.

## Capabilities

### New Capabilities

- `reactor-module-documentation`: what the current documentation may say about the modules of the reactor, and how it
  is kept equal to the root POM.

### Modified Capabilities

(none)

## Impact

- `docs/developer/BUILD_AND_TEST.md`, `docs/developer/README.md`, `docs/developer/SPOTBUGS.md`,
  `docs/architecture/arc42/04-strategie-solution.md`, `docs/architecture/arc42/05-vue-blocs.md`; any other page the rule
  names when it first runs.
- `RepositoryDocumentationCoherenceTest` or a sibling in the documentation coherence family — a governance file,
  justified in the pull request.
- No ADR, no contract, no ratchet, no product code. Dated records are not rewritten.
- Close to sprint 10 ("Dire la vérité dans les règles"): if a change of that sprint widens the same detector to the
  same pages, the two are merged rather than stacked.
