# Design

## Context

- `morpheus-cli` is one package; most of its types are package-private. `MorpheusCli` (the historical monolith) and
  fourteen `Morpheus*Cli` classes each handle a family of commands.
- `GlobalArgs.parse` reads `--json`, `--data-dir`, `--config-dir` and `--db` and hands the rest on; `GlobalArgs.command`
  returns the first token that is not one of them.
- `SimpleOptions` accepts any `--key value` pair and refuses the unknown ones in `rejectUnknown`, which its caller must
  call; the six private `Options` refuse an unknown option in the `default` arm of their own `switch`.
- `CliOptionParsingRefusesUnknownOptionsTest` *discovers* the parser families and the terminal parsers of the module by
  shape and blanks comments and string literals before scanning. Its comment lists what it does not see.
- The error line `MORPHEUS error [<code>]: <message>` is what a caller's script sees; the CLI suites assert it.
- The message of a failure is the failure's own message, or its type name when it has none.

## Goals / Non-Goals

**Goals**: one writer of the error line; one derivation of a failure's text; one reader of the command token; one
option parser to which a sub-command declares what it accepts; the same refusal for the same malformed input; rules
that refuse the next private copy.

**Non-Goals**: changing an option, a command, an exit code or the error format; filtering what a failure message may
disclose (the HTTP boundary has its own change; the CLI is a local surface); splitting `MorpheusCli` by capability
(AUD-ARC-09, sprint 12); the three launch parsers (`ApiLaunchOptions`, `McpLaunchOptions`, `RemoteApiLaunchOptions`),
which read a different grammar and are named exceptions.

## Decisions

1. **The shared base stays in `com.morpheus.cli`, beside `GlobalArgs`.** No other module needs it. *Rejected*: an
   abstract parent class for sub-commands - the sub-commands share behaviour, not identity, and the repository wires
   by composition. *Rejected*: moving the failure text to `morpheus-application`, where a method of the same name
   exists - the CLI's rule ("the message, or the type name") is a presentation choice of this adapter.

2. **One declarative parser instead of six.** A sub-command states its value options and its flags; the parser refuses
   the unknown option, the duplicate, the missing value and the blank value (through `OptionValue`), in that order,
   and nothing else is written per sub-command. It is either `SimpleOptions` extended with declared flags or its
   successor - one type at the end, not two. Declaring the allowed set at construction removes the optional
   `rejectUnknown` call that CLI-3 was about: a parser that cannot be built without its allowed set cannot forget to
   check it. *Rejected*: keeping the six and holding them equal by a parity test - six bodies with six fingerprints is
   what a parity test would have to tolerate.

3. **The behaviour of each sub-command is pinned before the parsers are replaced.** Task 1.2 builds the table: for each
   parser, what it answers to an unknown option, a duplicate, a missing value, a value starting with `--`, a blank
   value, a positional token, a repeated flag. Where all agree, the shared parser does that. Where they differ, the
   difference is a decision recorded in the table - align (visible, release note) or keep as a declared property of
   that sub-command - never an accident of which parser was pasted.

4. **Enforcement.**
   - *The three members*: an ArchUnit members rule on **signatures** in `com.morpheus.cli..`. Intent: *no CLI type
     other than the owner declares a method that turns a failure into text, a raw argument vector into a command
     token, or a code and a message into an error line.* A signature survives a rename, which a name does not; the
     module is in the imported classpath. Exceptions are listed by name with their reason.
   - *The error format*: textual. Intent: *the literal `MORPHEUS error [` appears in one source file.* A string
     literal is a compile-time constant; no rule on bytecode says which class spelled it (`.claude/rules/architecture.md`).
   - *The parsers*: textual, by extending the discovery of `CliOptionParsingRefusesUnknownOptionsTest`. Intent: *a
     type that parses a list of option tokens is the shared parser or a named exception.* The existing test already
     finds these types by shape; this adds that their list is closed.
   Splitting by intention adds test methods; none is merged into an existing one
   (`.claude/skills/enforcement-choice`).

5. **Delivered in two lots.** First the three members and the error line - mechanical, no behaviour at stake. Then the
   parsers, sub-command by sub-command, each with its row of the table green before and after.

## Risks / Trade-offs

- The parser lot touches every sub-command's argument handling. The adapter suites each have unknown-option and
  missing-value cases; the table of decision 3 adds the cases they lack before anything moves.
- A signature rule on `(Throwable) -> String` may meet a method with that shape and another purpose. It is listed,
  with its reason - the list is short or the rule is wrong, and task 2.1 shows which.
- Aligning a divergent refusal changes what a script sees on a malformed call. Only malformed calls are concerned, and
  each alignment is listed.

## Open Questions

- **Align or keep the divergences of the table?** Recommendation: align on the strictest refusal in every case, as
  CLI-3 and the blank-value decision did; a sub-command that keeps a difference states it in its declaration.
- **`safeMessage` in `morpheus-application` and `morpheus-provider-sdk`** (one declaration each, measured). They are
  not CLI code and not in scope. Recommendation: leave them; the SDK may not depend on the application, and two sites
  in two modules that cannot share is the per-module case, not a foundation helper.
