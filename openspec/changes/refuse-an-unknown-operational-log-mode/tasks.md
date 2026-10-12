# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List every place the variable is read, documented or tested. Proof: the list; on 2026-10-13 it was one
      constant, one developer page and no user page.
- [ ] 1.2 Read how `LaunchEnvironmentDisclosureTest` discovers the variables the product reads, and why this one was
      not found. Proof: the lines.

## 2. Reproduce before fixing

- [ ] 2.1 Unit tests of the parser for the four scenarios of the first requirement. Proof: `jsno`, `true` and `1` are
      accepted on `HEAD`.
- [ ] 2.2 A launcher test: an unknown mode makes any command exit with the usage code and one named line on standard
      error. Proof: red on `HEAD`.
- [ ] 2.3 The documentation gate, widened as 1.2 decided. Proof: red on `HEAD`, naming the variable.

## 3. The change

- [ ] 3.1 The parser, its closed list, and its use by the static initializer and by `MorpheusMain`. Proof: 2.1 and 2.2
      pass.
- [ ] 3.2 The user page that lists environment variables, and `docs/developer/OPERABILITY.md`. Proof: 2.3 passes.
- [ ] 3.3 Break once, then revert: restore the `default` branch. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 Release note in the terms of an operator. Pull request description: the finding and what a mute deployment
      will now see.
