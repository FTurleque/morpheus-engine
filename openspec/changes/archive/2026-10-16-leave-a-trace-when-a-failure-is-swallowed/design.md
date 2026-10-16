# Design

## Context

- `OperationalRecorder` couples a sink and the metrics: `begin` increments `<operation>.started` and emits; `finish`
  increments an outcome counter and a duration. The counters move even under the no-op sink.
- `OperationalEventCode` is a closed enumeration; `SensitiveValueRedactor` runs before any event is written.
- `StructuredOperationalEventSink` writes one JSON object per line to a stream it is given; today that stream is
  standard error.
- In MCP STDIO mode standard output is the protocol and must carry nothing else; standard error is free.
- `LocalWritePermissionHardener` exists for files the product creates under the user's profile.
- The core packages `application.reasoning` and `application.policy` are forbidden transport, persistence and execution
  by textual rules; counting a suppressed failure there must not bring any of them in.

## Goals / Non-Goals

**Goals**: no deliberately swallowed failure without a counter; the embedded libraries audible on demand; the logs
directory either written or gone; nothing written by default that was not written before.

**Non-Goals**: a logging framework as a dependency of the domain or the application; a network exporter; turning
suppressions into propagated failures (each was a decision; this change makes them visible, it does not re-litigate
them); the access journal of the remote server (`record-who-acted-on-the-remote-server`).

## Decisions

1. **One event code and one counter family for a suppressed failure.** `FAILURE_SUPPRESSED`, with the attributes
   `family` (a closed list written beside the code: `adapter-discovery`, `sqlite-cleanup`, `mcp-peer`, ...) and
   `failureType` (the simple name of the failure). The counter is `failure.suppressed.<family>`. The message of the
   failure is not an attribute: it may name a server location, and the type and the family are what a diagnosis starts
   from.

2. **Every catch site is classified, and the classification is held by a test.** For each `catch` that does not
   rethrow: either it records, or it is listed with its reason in a closed list (a `close()` on an already failed path
   that adds its failure as suppressed to the primary one is not a swallowed failure). The textual test reads the
   sources, so a new silent `catch` fails the build. *Rejected*: recording in every catch without reading them - a
   counter that moves on normal operation is noise, and noise is how a counter stops being read.

3. **The counters are served where the metrics already are**, local and remote, with no new route.

4. **Third-party logs: a provider of MORPHEUS's own, in `morpheus-cli`.** It implements the SLF4J service provider,
   forwards warning and error records to the operational sink as `THIRD_PARTY_LOG` events (logger name, level,
   redacted message, failure type), and drops everything when the mode is off - which is what `slf4j-nop` does today.
   It replaces `slf4j-nop`; no second binding is shipped. *Rejected*: selecting between two bindings by an environment
   variable - SLF4J binds one provider per class path, so the choice would have to be made by the launcher's class
   path. *Rejected*: Logback or Log4j - a framework and its configuration surface, for a need that is one sink.
   **This is the decision the maintainer is asked to confirm**: it adds `slf4j-api` as a compile dependency of
   `morpheus-cli`.

5. **The three `System.Logger` users join the same channel.** Their records go to the JDK's default handler today. The
   lot routes them through the recorder, or installs a `System.LoggerFinder` beside the SLF4J provider; it chooses
   after reading what each of the three logs, and says which.

6. **Logs directory: a `file` mode, recommended.** `MORPHEUS_OPERATIONAL_LOGS=file` writes JSON-lines to
   `<logs>/morpheus-operational.jsonl`, rotated by size, a fixed number of generations, created with the permissions
   the write hardener gives a private file. The directory is published in three user pages and by the `paths` command;
   an operator who was told there is a logs directory expects logs in it. *The alternative, also acceptable*: remove
   `logs` from the layout, from `MORPHEUS_LOGS_DIR` and from the three pages, and state that the product writes no
   file. **The maintainer chooses.** Whichever is chosen, the sink needs a directory the application module does not
   know: the wiring moves to `MorpheusMain`, which is what `inject-the-operational-runtime` formalises.

7. **An ADR** records decisions 4 and 6: they add a dependency edge and a file the product writes.

## Risks / Trade-offs

- A counter incremented on a path that fails often by design (an optional adapter absent on most installations) reads as
  an alarm. Decision 2 exists for that; each family says in its documentation what a non-zero value means.
- A third-party message may carry a path or a token. It goes through the redactor like any attribute, and the redactor
  is extended only with a failing test first.
- A file under the user's profile grows. The bound is in bytes and generations, both stated, and the rotation is tested
  at the bound.
- M19 budgets: an increment per suppressed failure is not on a hot path, but the gates run in the nightly lane and are
  replayed.
