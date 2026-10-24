# Tasks

## 1. Baseline

- [x] 1.1 On the base commit, record: the executed-test counts (declared, generated, architecture) and both coverage
      scales from a full `./mvnw clean verify`, and the per-module line and branch totals of `morpheus-store-sqlite`.
      Proof: the figures and the build they come from.
- [x] 1.2 Re-run the search of every pin of the package and of its file paths, both spellings, outside dated
      records. Proof: the list by file.

## 2. Move the classes

- [x] 2.1 `git mv` the 14 adapter classes and the 13 runtime classes of `design.md`; package lines and imports only.
- [x] 2.2 Widen the visibilities the compiler requires for the main sources, no more. Proof: the table of `design.md`
      matches the diff of modifiers.
- [x] 2.3 Move each test to the package of the class it exercises; shared test support goes where its users are, and
      `META-INF/services` follows `NoSqliteScopeLeftBehindExtension`. Record every widening a test forces in
      `design.md`, with the test.
- [x] 2.4 Update the import lines of `morpheus-api`, `morpheus-cli`, `morpheus-mcp` (main and test). No other edit
      in those modules. Proof: `git diff` of those modules is import lines only.

## 3. Follow the pins

- [x] 3.1 Architecture tests listed in `design.md`: paths and package selections follow the files; no assertion
      removed, none weakened.
- [x] 3.2 Validators `validate-m23` to `m26` and `validate-r2`, `.ps1` and `.sh` together, `.ps1` kept ASCII;
      `scripts/tests/`; `.claude/skills/live-numbers/numbers.{ps1,sh}`; `distribution/build-portable.ps1`.
- [x] 3.3 Active ADRs, their `.rules.ts`, `docs/` pages outside dated records, `.claude/agents/`, `.claude/commands/`.

## 4. The rules

- [x] 4.1 Rule 1 and its textual companion, the closed exception list with its comment pointing to AUD-ARC-01.
- [x] 4.2 Rule 2 (adapter contents) and the check that no main class stays in the parent package.
- [x] 4.3 No other store, no provider: held by `AdapterSiblingArchitectureTest` (`storesDoNotDependOnEachOther`,
      `storesDoNotDependOnProvidersTransportsOrIntegrations`), which selects the new sub-packages; no rule is added.
- [x] 4.4 Break each rule once and revert, the existing rules of 4.3 included: the failures of `design.md`, decision 6.
      Proof: the failure messages.

## 5. Verification

- [x] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze` green; counts and coverage of 1.1 not lower, per
      scale. Proof: the figures before and after.
- [x] 5.2 Changed-line coverage gate, as `ci.yml` runs it.
- [x] 5.3 The validators of jalon touched by 3.2, on Windows and on Linux, with the current version.
- [x] 5.4 `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml` byte-identical to the base.
- [x] 5.5 Pull request description: the measure, the tables of `design.md`, the breakages, the justification of
      every change under `morpheus-architecture-tests/`.
