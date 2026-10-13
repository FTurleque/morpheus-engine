# Tasks

## 1. Measure before changing

- [ ] 1.1 Count the `<module>` entries of the root POM. Proof: the list; on 2026-10-13 at `8b26d3d7`, eighteen, the last
      being `morpheus-coverage-report`. Do not write the count anywhere but in the pull request.
- [ ] 1.2 For every page of the scanned set (design, decision 3), count how many declared modules it names. Proof: the
      table, and the pages above the half; on 2026-10-13 the four pages of the finding are expected.
- [ ] 1.3 List every statement of a module, project or POM total in the scanned set, with whether it speaks of the
      present or reports a dated measurement. Proof: the table; on 2026-10-13 it holds at least
      `BUILD_AND_TEST.md:17` and `:39`, `developer/README.md:32`, `04-strategie-solution.md:27`, `05-vue-blocs.md:81`,
      `SPOTBUGS.md:64`, and the dated tables of `CODE_AUDIT.md`.
- [ ] 1.4 Read how `RepositoryDocumentationCoherenceTest` tells an active surface from a historical one, and what its
      `ADR_OR_MODULE_TOTAL` pattern matches. Proof: the lines; the new rule reuses what exists.

## 2. Reproduce before fixing

- [ ] 2.1 Write the list rule. Proof: red on `HEAD`, naming the four pages and `morpheus-coverage-report`.
- [ ] 2.2 Write the total rule with the recognition of dated measurements drawn from 1.3. Proof: red on `HEAD`, naming
      the present-tense statements of 1.3 and none of the dated ones.

## 3. The change

- [ ] 3.1 Correct the pages: add the missing module to each list and to the arc42 table with what it is for; replace
      each total by a sentence without a number or by a pointer to the root POM. Reword a dated measurement only to
      give it its date when the rule cannot recognise it.
- [ ] 3.2 Enforcement: textual assertion, intent "a module total, or an incomplete module list, must not appear on a
      current page" (ADR-0103; skill `enforcement-choice`). Suggested home: `RepositoryDocumentationCoherenceTest` or a
      sibling.
- [ ] 3.3 Break each rule once, then revert: remove a module from one list; add a module to a fabricated POM; write
      "18 modules" on a developer page; name a non-existent module in a list; empty the scanned set. Proof: the five
      named failures.

## 4. Verification and destinations

- [ ] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green;
      `RepositoryDocumentationCoherenceTest` refuses no link.
- [ ] 4.2 No rule file needs a new sentence: `.claude/CLAUDE.md` already says to count the `<module>` entries. Check
      that no file under `.claude/` describes the pages as unchecked.
- [ ] 4.3 Pull request description: the finding, the tables of 1.2 and 1.3, the justification of the test changed under
      `morpheus-architecture-tests/`.
