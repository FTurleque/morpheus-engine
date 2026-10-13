# Design

## Context

- `LocalOperationalRuntime` holds three static finals: the metrics, the sink and the recorder. The sink is chosen by
  `createSink()` from `System.getenv`.
- A static initializer that throws surfaces as `ExceptionInInitializerError` at the first use of the class, from
  wherever that use happens to be: the refusal would have no stable place and no stable wording.
- The process is wired in `MorpheusMain`; a refused setting there becomes a message on standard error and a usage exit
  code, as the launch options already do.

## Goals / Non-Goals

**Goals**: an unknown mode is refused before the process does anything; the refusal names the variable and the accepted
values; an absent or blank variable keeps meaning "off".

**Non-Goals**: adding a mode (`leave-a-trace-when-a-failure-is-swallowed` does); removing the static runtime
(`inject-the-operational-runtime` does); changing where the events are written.

## Decisions

1. **The mode is parsed by a named function that returns a mode or throws a named refusal**, and the static initializer
   uses it. `MorpheusMain` calls the same function first, before any other class of the product is touched, so the
   refusal is rendered by the launcher and not by a class initializer. *Rejected*: validating in the initializer alone -
   the error would be an `ExceptionInInitializerError` thrown from the first caller.

2. **Trimming and case folding are kept; nothing else is tolerated.** `JSONL ` is `jsonl`. `true`, `1` and `yes` are
   refused: they are not modes, and guessing which mode they mean is the fallback this change removes.

3. **The refusal lists the accepted values**, read from the same enumeration the parser uses, so the message cannot
   drift from the code.

4. **The variable is documented for the user**, and the lot finds out why the sprint 2 gate on environment variables did
   not see it (it is read through a constant, `LOG_MODE_ENV`) and widens that gate.

5. **No ADR.**

## Risks / Trade-offs

- A deployment that carried an unrecognised value and ran mute will now refuse to start. That is the point, and the
  release note says so.
- The MCP server is started by an assistant client, which shows a start-up failure poorly. The refusal goes to standard
  error and the exit code is the usage code; the integration guide gets one sentence.
