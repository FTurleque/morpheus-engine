# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 Re-list every method of `src/main/java` that takes `Object...` and returns a map, with its body's behaviour
      on an odd count, an absent value and the returned map. Proof: the table; on 2026-10-13 at `8b26d3d7`, ten
      declarations, four bodies, three behaviours.
      Done: on `2fcf7415`, four declarations, four bodies, three behaviours: `api` `OrderedFields.map` (the seven HTTP copies already merged), `mcp` `McpContentPayloads.map`, `MorpheusCompositionMcpTools.map`, `MorpheusMcpToolCatalog.props`; 64 calls.
- [x] 1.2 List the call sites that pass a value which can be absent. Proof: the list; it decides which entry point each
      call takes. `MorpheusHistoryApiService` is the known one.
      Done: one site, the difference map of `MorpheusHistoryApiService.compareVersions`; every other value is a `toString()`, a `name()`, an `orElse` of a present value, a constant or a component a compact constructor refuses `null` for.
- [x] 1.3 Search the call sites for a key given twice and for a key that is not a string literal or a string constant.
      Proof: the list, expected empty; a hit is a defect reported before the builder exists.
      Done: empty over the 64 calls.
- [x] 1.4 Read both coverage scales and the presence ratchets from the last validation summary. Proof: the figures and
      their source (`.claude/skills/live-numbers`), to compare in 4.2.
      Done: ratchets read from `config/m21-quality-ratchets.properties`; no validation summary of the base exists locally, the before/after comparison is the CI's (4.2).

## 2. Reproduce before fixing

- [x] 2.1 The ArchUnit members rule on the signature and its textual twin. Intent: *no type other than the owner
      declares a method that turns an argument array into a map.* Proof: red on `HEAD`, naming the ten methods; the
      rule retains classes (`archRule.failOnEmptyShould` stays active).
      Done: red on `2fcf7415`: the members rule names the four methods, the twin and the leaf rule fail.
- [x] 2.2 Tests of the builder: the three refusals with an assertion on each message, the two absent-value policies,
      the order of iteration, the refusal of modification. Proof: red until 3.1.
      Done: `PayloadMapTest`, red (no owner) until 3.1.
- [x] 2.3 A test that a key of the wrong type answers a class cast failure through one of the existing copies. Proof:
      it documents the behaviour replaced; removed with the copy.
      Done: `OrderedFieldsKeyCastTest`, removed with `OrderedFields`.

## 3. The change

- [x] 3.1 The builder, in a leaf package of `morpheus-application`. Proof: 2.2 passes.
      Done: `com.morpheus.application.foundation.PayloadMap`.
- [x] 3.2 Replace the ten copies, one module per commit. Proof: 2.1 passes; the adapter suites pass with no expected
      payload edited.
      Done: one commit for `morpheus-api`, one for `morpheus-mcp`; both suites green, no expected payload edited.
- [x] 3.3 The leaf rule: the owner's package depends on no other MORPHEUS package. Proof: red when a type of
      `application.query` is referenced from it, then reverted.
      Done: red on a reference to `application.query.PageRequest` from the owner, reverted.
- [x] 3.4 Break the members rule once, then revert: a private `Object...` builder under a new name in `morpheus-mcp`.
      Proof: the named failure, from the rule and from the twin.
      Done: `MorpheusSyncStatusMcpTools.fieldsOf(Object...)` named by the rule and by the twin, reverted.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning.
- [ ] 4.2 Both coverage scales and the changed-line gate, on Linux and Windows (the required checks). Proof: the figures
      next to those of 1.4; if a ratio falls, tests are written - no ratchet is lowered.
- [x] 4.3 `.claude/rules/code-style.md`: the builder named where the rule on explicit failures is stated. Proof: the
      diff.
      Done: `.claude/rules/code-style.md`, beside the rule on explicit failures.
- [ ] 4.4 Pull request description: the finding, the table of 1.1, the result of 1.3, the one internal failure whose
      type changes, and the justification of the new architecture test.
