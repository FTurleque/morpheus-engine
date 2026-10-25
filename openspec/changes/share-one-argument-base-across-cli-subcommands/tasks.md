# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

**Where the targets are.** `give-each-cli-command-and-mcp-tool-a-capability-class` runs first. The capability classes
of the CLI are in `com.morpheus.cli.command`, beside the shared types this change speaks of (`GlobalArgs`,
`SimpleOptions`, `OptionValue`, `OptionOccurrence`). The parser and the error line of `MorpheusCli` are there too, once
each: `CommandOptions` (the private parser `MorpheusCli.CommandOptions` of the counts below) and `CliCommandRun` (the
error line, the exit-code mapping and `safeMessage` of `MorpheusCli`), which every command extracted from
`MorpheusCli` uses. The rules of this change are written on `com.morpheus.cli..`, so the move does not change them.

## 1. Measure before changing

- [ ] 1.0 Search `scripts/`, `.github/workflows/` and `distribution/` for a reader of the CLI's error line. Proof:
      the list; it decides what 4.1 runs.
- [ ] 1.1 Re-count, in `morpheus-cli/src/main/java`, the declarations of the three members by signature, the sites of
      the error literal, and the types that parse option tokens. Proof: the table; on 2026-10-13 at `8b26d3d7`: eleven,
      twelve (three of them delegating), six, 21 sites in 11 files, six private parsers beside `SimpleOptions`.
- [ ] 1.2 **The unverified premise.** For each option parser (the six private ones, `SimpleOptions`, and the three
      parsers of the raw vector the discovery test does not see), record what it answers to: an undeclared option, a
      duplicate, a missing value, a value starting with `--`, a blank value, a positional token, a repeated flag.
      Proof: the table, each cell an exit code and a message, obtained by running the command. The audit asserts the
      parsers diverged; this reading only established that their text does.
- [ ] 1.3 List the methods of `com.morpheus.cli` whose signature matches one of the three members and whose purpose is
      another. Proof: the list, expected short; it becomes the named exceptions of 2.1.
- [ ] 1.4 Read both coverage scales, the changed-line thresholds and the presence ratchets from the last validation
      summary. Proof: the figures and their source.

## 2. Reproduce before fixing

- [ ] 2.1 The ArchUnit members rule on the three signatures. Intent: *no CLI type other than the owner declares one of
      these three conversions.* Proof: red on `HEAD`, naming the declarations of 1.1; the rule retains classes.
- [ ] 2.2 The textual rule on the error literal. Intent: *the literal appears in one source file.* Proof: red on
      `HEAD`, naming eleven files.
- [ ] 2.3 The closed list of option-token parsers, added to the discovery of
      `CliOptionParsingRefusesUnknownOptionsTest` as a separate test method. Proof: red on `HEAD`, naming the six.
- [ ] 2.4 One adapter test per cell of 1.2 that no existing test covers. Proof: green on `HEAD` - they pin today's
      behaviour - and they are the tests that must stay green, or change knowingly, in lot two.

## 3. The change

### Lot one - the three members and the error line

- [ ] 3.1 The writer of the error line, the failure text and the command reader, beside `GlobalArgs`. Proof: unit tests,
      including a failure with a null and with a blank message.
- [ ] 3.2 Replace the copies, the three delegating wrappers included. Proof: 2.1 and 2.2 pass; the CLI suites pass with
      no expected line edited.

### Lot two - the parsers

- [ ] 3.3 The shared declarative parser, refusing in the order of the design. Proof: unit tests over the kinds of 1.2.
- [ ] 3.4 Replace the private parsers, one sub-command per commit. Proof: that sub-command's rows of 2.4 green before
      and after, or changed in the same commit with the line of the release notes; 2.3 lists one type fewer.
- [ ] 3.5 Record the declared differences that remain, if any, in the tested list. Proof: the list and the test that
      reads it.
- [ ] 3.6 Break each rule once, then revert: a renamed failure-to-text method; a second spelling of the literal; a
      nested parser in one sub-command. Proof: the three named failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning. If 1.0 finds a validator
      or a workflow that reads the error line, that validator is run on both platforms with the current version read
      from `ProductMetadata`.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows. Proof: the figures next to 1.4; tests are
      written if a ratio falls, no ratchet is lowered.
- [ ] 4.3 Documentation: `docs/developer/` page of the CLI adapter, one paragraph on the shared base; a release note
      under `docs/user/release-notes/` only if 3.4 aligned a refusal, in user terms. Proof: the diff and
      `RepositoryDocumentationCoherenceTest`.
- [ ] 4.4 Pull request description, per lot: the finding, the tables of 1.1 and 1.2, every refusal that changed, the
      justification of the architecture tests.
