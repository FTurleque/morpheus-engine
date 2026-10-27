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

- [x] 1.0 Search `scripts/`, `.github/workflows/` and `distribution/` for a reader of the CLI's error line. Proof:
      the list; it decides what 4.1 runs.
- [x] 1.1 Re-count, in `morpheus-cli/src/main/java`, the declarations of the three members by signature, the sites of
      the error literal, and the types that parse option tokens. Proof: the table; on 2026-10-13 at `8b26d3d7`: eleven,
      twelve (three of them delegating), six, 21 sites in 11 files, six private parsers beside `SimpleOptions`.
- [x] 1.2 **The unverified premise.** For each option parser (the six private ones, `SimpleOptions`, and the three
      parsers of the raw vector the discovery test does not see), record what it answers to: an undeclared option, a
      duplicate, a missing value, a value starting with `--`, a blank value, a positional token, a repeated flag.
      Proof: the table, each cell an exit code and a message, obtained by running the command. The audit asserts the
      parsers diverged; this reading only established that their text does.
- [x] 1.3 List the methods of `com.morpheus.cli` whose signature matches one of the three members and whose purpose is
      another. Proof: the list, expected short; it becomes the named exceptions of 2.1.
- [ ] 1.4 Read both coverage scales, the changed-line thresholds and the presence ratchets from the last validation
      summary. Proof: the figures and their source.

## 2. Reproduce before fixing

- [x] 2.1 The ArchUnit members rule on the three signatures. Intent: *no CLI type other than the owner declares one of
      these three conversions.* Proof: red on `HEAD`, naming the declarations of 1.1; the rule retains classes.
- [x] 2.2 The textual rule on the error literal. Intent: *the literal appears in one source file.* Proof: red on
      `HEAD`, naming eleven files.
- [x] 2.3 The closed list of option-token parsers, added to the discovery of
      `CliOptionParsingRefusesUnknownOptionsTest` as a separate test method. Proof: red on `HEAD`, naming the six.
- [x] 2.4 One adapter test per cell of 1.2 that no existing test covers. Proof: green on `HEAD` - they pin today's
      behaviour - and they are the tests that must stay green, or change knowingly, in lot two.

## 3. The change

### Lot one - the three members and the error line

- [x] 3.1 The writer of the error line, the failure text and the command reader, beside `GlobalArgs`. Proof: unit tests,
      including a failure with a null and with a blank message.
- [x] 3.2 Replace the copies, the three delegating wrappers included. Proof: 2.1 and 2.2 pass; the CLI suites pass with
      no expected line edited.

### Lot two - the parsers

- [x] 3.3 The shared declarative parser, refusing in the order of the design. Proof: unit tests over the kinds of 1.2.
- [x] 3.4 Replace the private parsers, one sub-command per commit. Proof: that sub-command's rows of 2.4 green before
      and after, or changed in the same commit with the line of the release notes; 2.3 lists one type fewer.
- [x] 3.5 Record the declared differences that remain, if any, in the tested list. Proof: the list and the test that
      reads it.
- [x] 3.6 Break each rule once, then revert: a renamed failure-to-text method; a second spelling of the literal; a
      nested parser in one sub-command. Proof: the three named failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning. If 1.0 finds a validator
      or a workflow that reads the error line, that validator is run on both platforms with the current version read
      from `ProductMetadata`.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows. Proof: the figures next to 1.4; tests are
      written if a ratio falls, no ratchet is lowered.
- [x] 4.3 Documentation: `docs/developer/` page of the CLI adapter, one paragraph on the shared base; a release note
      under `docs/user/release-notes/` only if 3.4 aligned a refusal, in user terms. Proof: the diff and
      `RepositoryDocumentationCoherenceTest`.
- [ ] 4.4 Pull request description, per lot: the finding, the tables of 1.1 and 1.2, every refusal that changed, the
      justification of the architecture tests.

## Proofs of the checked tasks (2026-10-27, from `2fcf7415`)

- 1.0: no reader of the error line in `scripts/`, `.github/workflows/` or `distribution/`; 4.1 runs no extra validator.
- 1.1: by signature, 16 failure-to-text methods (12 `Throwable`, 4 `RuntimeException`, `MorpheusMain` included), 13
  command readers beside `GlobalArgs` (3 delegating) plus `commandTokens` of the constraints command, 7 error-line
  writers (6 `error` plus `CliCommandRun.printError`); the literal at 21 sites in 11 files; 7 nested parsers beside
  `SimpleOptions` and `CommandOptions`, and 4 readers of the raw vector (product, provider-plugins, reason, server).
- 1.2: `CliOptionRefusalTableTest`, 110 cells over 18 sub-commands, measured by running each call through
  `MorpheusMain.run`; the parsers diverged in wording (6 spellings of a positional refusal, 6 of an unknown option),
  `paths` accepted any option with exit 0 and `augmented-context` accepted `--explain` twice.
- 1.3: one, `CliCommandPaths.commandWords(String[])`, the command path checked against the declared paths.
- 2.1, 2.2: `CliArgumentBaseArchitectureTest`, red on `HEAD` with 16, 14 and 7 declarations and 11 files.
- 2.3: `theOptionTokensOfEverySubCommandAreParsedByTheSharedParserOrANamedException`, red on `HEAD` naming the 7
  nested parsers and `SimpleOptions`.
- 2.4: `CliOptionRefusalTableTest`, green on `HEAD` (commit `test(cli): pin what every option parser answers`).
- 3.1, 3.2: `CliErrorLine`, `GlobalArgs.commandTokens`, `CliErrorLineTest`; the CLI suites passed with no expected line
  edited.
- 3.3: `CommandOptions.parse(tokens, Declared)`, `CommandOptionsTest`.
- 3.4: one commit per sub-command family; each changed cell is in the same commit as its release-notes line.
- 3.5: `CliOptionRefusalTableTest.DECLARED_DIFFERENCES`, run by `everyDeclaredDifferenceIsWhatTheSubCommandAnswers`.
- 3.6: `noCliTypeButTheOwnerTurnsAFailureIntoText`, `theErrorLineFormatIsSpelledInOneSourceFile` and
  `theOptionTokensOfEverySubCommandAreParsedByTheSharedParserOrANamedException` failed on the planted copies; the
  third first passed a parser declared on `java.util.List<String>`, and its pattern was widened.
- 4.3: `docs/developer/ARCHITECTURE.md` §10, `docs/user/release-notes/RELEASE_NOTES_1.2.2.md`;
  `RepositoryDocumentationCoherenceTest` green.
