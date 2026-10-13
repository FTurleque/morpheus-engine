# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change. Lot A (tasks 1 to 5) does not wait for sprint 11; lot B (task 6) does.

## 1. Measure before changing

- [ ] 1.1 Re-count on the lot's base: source files and packages of `morpheus-api`, public and package-private
      top-level types, the types of `api` another module imports. Proof: the table; on 2026-10-13 at `8b26d3d7`,
      81 files in one package, 13 public and 68 package-private, 6 types used by `morpheus-cli`.
- [ ] 1.2 Inventory every place that pins an `api` class by where it lives: literal source paths in
      `morpheus-architecture-tests`, exact-package and fully-qualified references, `scripts/validate-*` in both
      spellings (`com/morpheus/api/` and `com\morpheus\api\`), the regular expressions of `.claude/hooks/`,
      `docs/`, `.claude/rules/`, `.claude/skills/`. Proof: the list by file; on 2026-10-13, 117 literal paths and 43
      lines in the gate module, six validator pairs.
- [ ] 1.3 Inventory every list of production class names in the gate module and say for each: role, debt counter
      under which ADR, or table of facts. Proof: the table. Expected at least `COMPOSITION_ROOTS`, the three
      `ROUTERS_*`, the four maps of `HttpPathReadingArchitectureTest`.
- [ ] 1.4 Measure the candidate layout without moving a file: a throwaway ArchUnit rule that assigns each class to a
      candidate package by a predicate and reports the cycles (`archunit.cycles.maxNumberToDetect` raised for the
      run only, as ADR-0109 did). Count separately the readings of a compile-time constant across candidates, which
      ArchUnit does not see. Proof: the cycles, the constant readings, and the extractions each would need. If no
      layering exists short of dismantling `MorpheusHttpServer`, stop and report: see `design.md`, risks.
- [ ] 1.5 Take the executed-test count of `morpheus-architecture-tests` from a validation summary of the base
      (`live-numbers`). Proof: the figure and the run it comes from.

## 2. Make the gates independent of the layout

- [ ] 2.1 The source locator of the gate module, with its own tests: found, absent, ambiguous. Intent, in one
      sentence: *a gate names the class it reads, not the folder it is in* - textual by nature, the gate reads a
      `.java` file.
- [ ] 2.2 Route every literal path of 1.2 through it. No class of production moves. Proof: the gate module green,
      and a class of `api` moved by hand to a scratch sub-package fails only what names its package; revert.

## 3. The entry rule, before any visibility is widened

- [ ] 3.1 The ArchUnit rule (*no class outside the adapter depends on its non-entry packages* - a dependency, so a
      rule) and the scan of imports beside it (*no source outside the adapter names those packages* - a mention, and
      the only form that sees an inlined constant). Both, per ADR-0103 section 3.
- [ ] 3.2 Break each once and revert: a dependency from `morpheus-cli` on a planted public class of a sub-package
      fails the rule; a read of a planted public constant passes the rule and fails the scan. Proof: the two named
      failures, recorded in the ADR.

## 4. Move the transport roles, one role per pull request

- [ ] 4.1 The remote server and its identity model. Tests of `morpheus-api` move with their subjects;
      `validate-m26.ps1` and `validate-m26.sh` change together (`.ps1` stays ASCII). Proof: `clean verify` on both
      platforms; `scripts\validate.cmd m26 -Version <current>` and its `.sh`.
- [ ] 4.2 The transport plumbing and the local server, with the extractions 1.4 found necessary. The textual pins
      of `.claude/rules/security.md` follow a constant to its new owner in the same pull request.
- [ ] 4.3 The routers, into their group packages. `HttpRoutesTransportBoundaryArchitectureTest` and
      `HttpRoutesFamilyArchitectureTest` select by package; the text scans beside the rules stay.
- [ ] 4.4 For each pull request: a line per moved file in the table of old and new paths of
      `docs/project/README.md`; dated records are not rewritten (`rules/documentation.md`).

## 5. The rules the packages make possible

- [ ] 5.1 The cycle rule over the packages of `com.morpheus.api`, with a guard that fails when the adapter has fewer
      than two packages. Break it once: a planted back-edge. Revert.
- [ ] 5.2 The classification rule: every `*HttpRoutes` resides in exactly one group package. Break it once: a router
      in the parent package. Revert.
- [ ] 5.3 The remote ban by package. Break it once with a `RemoteIdentity*` class in a router - the case the prefix
      missed. Revert.
- [ ] 5.4 The gate over the remaining lists (1.3): each is declared a debt counter or a table of facts.
- [ ] 5.5 Re-take the count of 1.5. Proof: not lower, pull request by pull request. If it is lower, the split of the
      tests is wrong, not the ratchet.

## 6. Lot B - services and composition (after sprint 11's ADR and `own-the-sqlite-connection-policy-in-one-place`)

- [ ] 6.1 Re-measure the composition roots and say whether each remaining one composes and does nothing else.
- [ ] 6.2 If they do: the composition package, the package rule in both directions, and the amendment of ADR-0109
      that replaces "add a name" by "add a class to the package". If they do not: the list stays, and the ADR says
      what is still missing.
- [ ] 6.3 The package of the `*ApiService` classes. Break the rule that separates it from the routers once.

## 7. Documentation and decision records

- [ ] 7.1 The ADR (next free number, re-read `docs/architecture/adr/0*.md`; line in its `README.md`), with the
      measures of 1.1 to 1.4 and the breakages of 3.2 and 5.x.
- [ ] 7.2 Dated amendments: ADR-0109 (the row "not applicable"), ADR-0103 (placement is classification).
- [ ] 7.3 `.claude/rules/architecture.md` - re-read the classes before rewriting the paragraph on router groups, as
      the paragraph itself asks; `.claude/rules/security.md`; `.claude/CLAUDE.md`;
      `.claude/skills/enforcement-choice/SKILL.md`; `docs/architecture/arc42/05-vue-blocs.md`.

## 8. Verification

- [ ] 8.1 `./mvnw clean verify` and `./mvnw dependency:analyze` on Linux and Windows; changed-line coverage gate.
- [ ] 8.2 `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml` byte-identical to the base.
      Proof: `git diff --stat`.
- [ ] 8.3 Pull request description: the finding, the measures, what each rule replaced, and the justification of
      every change under `morpheus-architecture-tests/` (`rules/governance.md`).
