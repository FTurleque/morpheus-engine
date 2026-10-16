# Tasks

Implemented on 2026-10-13.

## 1. Measure before changing

- [x] 1.1 List every place the variable is read, documented or tested. Proof: the list; on 2026-10-13 it was one
      constant, one developer page and no user page.
      *Read once, in `LocalOperationalRuntime.createSink`; documented in `docs/developer/OPERABILITY.md` only; tested
      nowhere.*
- [x] 1.2 Read how `LaunchEnvironmentDisclosureTest` discovers the variables the product reads, and why this one was
      not found. Proof: the lines.
      ***The proposal was wrong on this point.** The gate did find the variable: it scans the string literals of the
      main sources, and the name is one. It was satisfied because it accepted `docs/developer` as operator
      documentation. Of the names it finds, this was the only one absent from `docs/user`. The gate now reads
      `docs/user` only.*

## 2. Reproduce before fixing

- [x] 2.1 Unit tests of the parser for the four scenarios of the first requirement. Proof: `jsno`, `true` and `1` are
      accepted on `HEAD`.
      *`OperationalLogModeTest`. The parser is new, so it has no state on `HEAD`; what was red is 2.2.*
- [x] 2.2 A launcher test: an unknown mode makes any command exit with the usage code and one named line on standard
      error. Proof: red on `HEAD`.
      *`OperationalLogModeLaunchTest`, a real process: on the commit that adds the test, `MORPHEUS_OPERATIONAL_LOGS=jsno
      morpheus version` prints the version and exits 0.*
- [x] 2.3 The documentation gate, widened as 1.2 decided. Proof: red on `HEAD`, naming the variable.
      *Narrowed rather than widened, per 1.2: restricted to `docs/user`, it fails naming `MORPHEUS_OPERATIONAL_LOGS`
      and nothing else.*

## 3. The change

- [x] 3.1 The parser, its closed list, and its use by the static initializer and by `MorpheusMain`. Proof: 2.1 and 2.2
      pass.
      *`OperationalLogMode` holds the list and the refusal; `MorpheusMain.main` consults it before anything else, and
      `LocalOperationalRuntime` selects its sink from it. The variable's name is a constant of the enumeration, so the
      launcher reads it without initializing the runtime.*
- [x] 3.2 The user page that lists environment variables, and `docs/developer/OPERABILITY.md`. Proof: 2.3 passes.
      *`docs/user/INSTALLATION.md` gains a section; `docs/user/MCP_CLIENTS.md` a diagnostic sentence, since an
      assistant client shows a start-up failure poorly.*
- [x] 3.3 Break once, then revert: restore the `default` branch. Proof: the named failure.
      *With the refusal replaced by `return OFF`: three failures in `OperationalLogModeTest` and two in
      `OperationalLogModeLaunchTest`.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *`morpheus-application` and `morpheus-cli` in full and `dependency:analyze` locally; the reactor and the
      changed-line gate are the required checks of the pull request.*
  - 2026-10-16: the pull request's required checks ran `clean verify` on Linux and Windows and the changed-line gate, green before merge; `develop` at `557fed02`, which carries every lot, is green on push (run 38825491416). (PR 465).
- [x] 4.2 Release note in the terms of an operator. Pull request description: the finding and what a mute deployment
      will now see.
