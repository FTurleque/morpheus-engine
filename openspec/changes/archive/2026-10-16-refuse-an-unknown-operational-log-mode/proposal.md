# Proposal

## Why

AUD-DEP-10 (sprint 4) reports that `MORPHEUS_OPERATIONAL_LOGS` falls back to no logging for any value it does not
recognise. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding **holds**:

- `LocalOperationalRuntime.java:32-41`: `off` and `none` give the no-op sink, `json`, `jsonl` and `structured` give the
  JSON-lines sink on standard error, and `default -> OperationalEventSink.noop()`.
- The sink is built in a static initializer (`:13`), once per process: nothing replays the decision and nothing reports
  it.

`MORPHEUS_OPERATIONAL_LOGS=jsno`, `=true` or `=1` therefore turns off the only observability channel of the product
without a word. The operator believes structured logs are on and runs a mute server. This is the silent degradation
`.claude/rules/code-style.md` forbids, on the variable every incident investigation depends on.

Found while re-reading, and not in the audit: the variable is documented in `docs/developer/OPERABILITY.md` only. No page
of `docs/user/` names it, although sprint 2 added a gate meant to refuse an undocumented variable the product reads.

## What Changes

- A value of the variable that is not one of the accepted modes makes the process refuse to start, with a named refusal
  that lists the accepted values.
- The accepted values and the default are stated in the user documentation.
- The gate on documented environment variables sees this variable.

## Capabilities

### New Capabilities

- `operational-log-mode`: the operational log mode is either one of a closed list of values or a refusal; it is never
  silently replaced.

### Modified Capabilities

(none)

## Impact

- `morpheus-application` (`LocalOperationalRuntime`), `morpheus-cli` (`MorpheusMain`: where a refusal becomes an exit
  code), `docs/user/` (the page that lists environment variables), `docs/developer/OPERABILITY.md`.
- No public surface moves: no manifest line, no OpenAPI operation.
- First of sprint 4: `leave-a-trace-when-a-failure-is-swallowed` adds a mode to the same list and builds on this
  refusal.
