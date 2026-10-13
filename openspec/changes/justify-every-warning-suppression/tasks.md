# Tasks

Implemented on 2026-10-13.

## 1. Measure before changing

- [x] 1.1 List the `@SuppressWarnings` of `src/main` with their key and whether a justification is present. Proof: the
      table; on 2026-10-13, 20 sites.
      *Read one by one. Sixteen already carried a written reason, on the line or just above (ten `java:S1181`, four
      `java:S106`, two `java:S107`). **Four did not, all `unchecked`**: `MorpheusPolicyMcpTools.castMap`,
      `SyntheticConstraintSemanticsReader.object`, `SyntheticSpecificationContentReader.object` and
      `SyntheticJsonParser.parseObject`. The audit's "about five justified of 47" was no longer the state of the
      sources.*
- [x] 1.2 List the unchecked casts of a `Map` at the MCP and HTTP boundaries and where each value comes from. Proof: the
      list.
      ***None is at a boundary.** `castMap` was applied to `createSchema().get("properties")` - a schema MORPHEUS
      builds itself two lines above, never a value received. The three others are in the synthetic provider and follow
      an `instanceof Map` check that already refuses by name (`... must be an object`); their values come from
      `SyntheticJsonParser`, which builds every object with string keys.*
- [x] 1.3 List the 33 sites of the tests, for the record.
      *Counted, not gated: 33 under `src/test`, most of them `unchecked` casts of parsed JSON in assertions.*

## 2. Reproduce before fixing

- [x] 2.1 The textual test. Proof: red on `HEAD`, naming the sites of 1.1 without a justification.
      *`WarningSuppressionJustificationTest`, red on the commit that adds it, naming exactly the four.*
- [ ] 2.2 A test per boundary cast: a value of the wrong shape. Proof: a class cast failure on `HEAD`.
      *Not applicable as written: 1.2 found no cast at a boundary, so there is no class cast failure to reproduce.*

## 3. The change

- [x] 3.1 The justifications. Proof: 2.1 passes.
      *Three, in the synthetic provider. A reason is read on the annotation's line or on the nearest line above that
      is not another annotation; a comment that only repeats the rule key is refused.*
- [x] 3.2 The validations and their named refusals. Proof: 2.2 passes; the public surfaces unchanged.
      *The one cast that hid nothing to validate is removed instead: `updateSchema` starts from the properties
      `createSchema` is built from, and `castMap` is gone. The second requirement of the specification is met by the
      absence of a boundary cast, not by a new validation.*
- [x] 3.3 Break once, then revert: a bare `@SuppressWarnings`. Proof: the named failure.
      *The trailing reason removed from one `java:S106` of `MorpheusMcpServer` fails the test naming the file and the
      line.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *`morpheus-mcp` and `morpheus-provider-synthetic` in full and `dependency:analyze` locally; the reactor is the
      required checks of the pull request.*
- [x] 4.2 Pull request description: the finding, the table of 1.1, what a malformed call now answers.
      *A malformed call answers what it answered before: nothing at a boundary changed.*
