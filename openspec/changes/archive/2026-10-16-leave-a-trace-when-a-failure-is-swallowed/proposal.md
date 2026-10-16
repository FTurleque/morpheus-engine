# Proposal

## Why

AUD-TRV-04 (sprint 4, the only High of the sprint) reports that the product is mute. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding
**holds, with two corrections**:

- **No application log.** No file of `src/main` imports `org.slf4j` (0 of the product's sources). *Correction*: three
  classes do log, through `System.Logger` - `BoundedStdioClientTransport`, `BoundedStdioServerTransportProvider` and
  `SqliteTransactionRunner`. That channel goes to the JDK's default handler and is governed by nothing MORPHEUS
  documents.
- **Failures swallowed without a trace.** The audit counted 21 deliberate suppressions. A scan for `catch` clauses whose
  variable is named `ignored`, `suppressed`, `expected` and the like finds 38 across eight modules today (12 in
  `morpheus-mcp`, 8 in `morpheus-application`, 6 in `morpheus-store-sqlite`). The two numbers do not use the same
  criterion; the lot reads each site. None of them increments a counter or emits an event. The mechanism exists:
  `OperationalRecorder` increments its counters whatever the sink is.
- **The third-party stack is thrown away.** The only binding shipped is `org.slf4j:slf4j-nop`
  (`morpheus-cli/pom.xml:74-76`): what the MCP SDK and Reactor would report - a handshake that timed out, a broken
  stream, a JSON-RPC frame that did not parse - goes nowhere.
- **A logs directory nobody writes to.** `CliLayout` resolves a logs directory (`MORPHEUS_LOGS_DIR`, or `logs` under the
  data or state root), the `paths` command prints it, and three user pages name it (`INSTALLATION.md`, `QUICKSTART.md`,
  `UPGRADE_1_1.md`). Nothing in the product writes there.

A reasoning adapter that fails to load disappears from the list and the user reads "unknown reasoning adapter" without
learning it was present and broken. After an incident, nothing survives the process. `.claude/rules/code-style.md` says
"never a silent degradation".

## What Changes

- Every place that deliberately swallows a failure either records it - a named counter, and an event when a sink is
  active - or is listed with the reason it is not a failure. The counters are exposed by the metrics the product
  already serves.
- The third-party stack can be heard: when the operational log mode is active, what the MCP SDK and its dependencies log
  at warning level and above reaches the same sink, redacted like the rest.
- The logs directory tells the truth: either a file mode writes bounded, rotated JSON-lines there, or the directory
  leaves the layout and the documentation. The maintainer decides; this specification recommends the file mode.

## Capabilities

### New Capabilities

- `suppressed-failure-trace`: a failure that is deliberately not propagated is counted under a named family, and
  reported when a sink is active.
- `third-party-log-capture`: the logs of the libraries MORPHEUS embeds reach the operational sink when one is active.
- `logs-directory-truth`: a directory the product announces as its logs directory has a writer, or is not announced.

### Modified Capabilities

(none)

## Impact

- `morpheus-application` (operability package, the catch sites), `morpheus-store-sqlite`, `morpheus-mcp`,
  `morpheus-mcp-transport`, `morpheus-api`, the two integration modules, `morpheus-provider-sdk`; `morpheus-cli` (the
  binding, the layout, the wiring).
- `docs/developer/OPERABILITY.md`, the three user pages that name the logs directory, `SECURITY.md` (what a log may
  contain).
- The metrics view gains counters; whether that is a contract change of `GET /api/v1/metrics` is read in the OpenAPI
  file before deciding.
- After `refuse-an-unknown-operational-log-mode`; before `record-who-acted-on-the-remote-server` and
  `inject-the-operational-runtime`.
