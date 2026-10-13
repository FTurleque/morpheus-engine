# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List the `@SuppressWarnings` of `src/main` with their key and whether a justification is present. Proof: the
      table; on 2026-10-13, 20 sites.
- [ ] 1.2 List the unchecked casts of a `Map` at the MCP and HTTP boundaries and where each value comes from. Proof: the
      list.
- [ ] 1.3 List the 33 sites of the tests, for the record.

## 2. Reproduce before fixing

- [ ] 2.1 The textual test. Proof: red on `HEAD`, naming the sites of 1.1 without a justification.
- [ ] 2.2 A test per boundary cast: a value of the wrong shape. Proof: a class cast failure on `HEAD`.

## 3. The change

- [ ] 3.1 The justifications. Proof: 2.1 passes.
- [ ] 3.2 The validations and their named refusals. Proof: 2.2 passes; the public surfaces unchanged.
- [ ] 3.3 Break once, then revert: a bare `@SuppressWarnings`. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 Pull request description: the finding, the table of 1.1, what a malformed call now answers.
