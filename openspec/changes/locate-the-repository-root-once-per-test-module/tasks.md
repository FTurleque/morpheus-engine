# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-count the locators under `src/test`, by module, by name and by normalised body, and list the anchor and
      the failure type of each body. Proof: the table. On 2026-10-13 at `8b26d3d7`: 88 declarations, 15 bodies, eight
      anchors; the audit said 73 and seven. Trust neither number: the count moved by fifteen in four days.
- [ ] 1.2 List every test method that climbs through parents from the working directory without being one of 1.1 (the
      fixture locators of `morpheus-cli`, `morpheus-provider-synthetic` and `morpheus-api`, and the two architecture
      test files that climb for another reason). Proof: the list, each entry classified locator / other purpose.
- [ ] 1.3 List the test methods that declare `throws IOException` only because of the locator. Proof: the list.
- [ ] 1.4 Run one suite of each anchor group from the module directory and from the repository root. Proof: which
      groups already fail from one of the two - the seventeen bounded copies are the candidates.

## 2. Reproduce before fixing

- [ ] 2.1 The governance test, textual, shape-based (design, decision 4). Intent in one sentence: *this declaration must
      not appear outside the owner*. Proof: red on `HEAD`, naming the files of 1.1 and the locators of 1.2.
- [ ] 2.2 A test of the owner: found from the module directory, from the root, from a nested directory; a directory
      with no repository above it fails naming the start and the anchor. Proof: red until 3.1.

## 3. The change

- [ ] 3.1 The owner in `morpheus-architecture-tests`, with the anchor and its reason in the class comment. Proof: 2.2
      passes.
- [ ] 3.2 Replace the copies, one commit per package. Proof: 2.1 lists fewer files after each commit and none at the
      end; every suite passes unmodified apart from the call.
- [ ] 3.3 The owner of `morpheus-coverage-report`, and the step test between the two owners. Proof: the step test red
      when one anchor is edited alone.
- [ ] 3.4 If decision 5 is accepted: one owner per remaining module for the fixture climb. Proof: 2.1 green with no
      named exception for a locator.
- [ ] 3.5 Break the rule once, then revert: paste a locator under a new name into one suite. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`, then
      `./mvnw clean verify`. Proof: green; the presence ratchets read from a validation summary, not from a count of
      annotations (`.claude/skills/live-numbers`).
- [ ] 4.2 Both platforms: the required checks of the pull request run the suites on Linux and Windows. Proof: green on
      both; a path anchor is exactly what differs between them.
- [ ] 4.3 `.claude/rules/testing.md`: one line under "TOUJOURS" naming the owner. Proof: the diff.
- [ ] 4.4 Pull request description: the finding, the tables of 1.1 and 1.2, the anchor chosen and why. A test under
      `morpheus-architecture-tests/` is a governance file: the description justifies it.
