# Design

## Context

- The product reads its environment through an injected `Map<String, String>` (`System.getenv()` is passed in from
  `MorpheusMain` and `MorpheusCli`), with several helper shapes: `envPath(environment, "NAME")`,
  `presentSetting(environment.get("NAME"))`, a constant (`LocalOperationalRuntime.LOG_MODE_ENV`), and the MINOS / NEXUS
  settings classes. There is no single read site.
- Variable names are compile-time string constants: `javac` inlines them, so a bytecode rule cannot see which class
  mentions which name.
- The existing documentation homes: `docs/user/TEAM_REMOTE_SERVER.md` (remote server), `docs/user/INSTALLATION.md`
  (layout), `docs/user/INTEGRATIONS.md` (MINOS / NEXUS), `docs/developer/OPERABILITY.md` (operational logs).

## Goals / Non-Goals

**Goals**: document the one missing variable with its precedence; make an undocumented variable a build failure.

**Non-Goals**: documenting every JVM property (`morpheus.*`) — the property of this one setting is documented with it,
a general rule for properties is a separate question; changing any precedence; variables of the operating system the
product also reads (`LOCALAPPDATA`, `XDG_*`), which are not its own.

## Decisions

1. **Textual assertion, by enforcement-choice.** Intention in one sentence: "a `MORPHEUS_*` name the product mentions
   appears in the operator documentation". The target is a `.md` file and the names are inlined constants: text is the
   only hold on both sides (ADR-0103, "restent textuels par nature"). No ArchUnit twin is possible.

2. **The population is every `"MORPHEUS_[A-Z0-9_]+"` literal under `*/src/main/java`, minus a named list.** Anchoring on
   read sites was tried on paper and rejected: four helper shapes today, and a fifth would silently escape the rule. A
   literal scan over-approximates — it also catches refusal codes — and the over-approximation is handled the way the
   repository handles it elsewhere: a list of excused names, each with a reason, where an entry that no longer matches
   a literal, or has no reason, fails. Two entries today: `MORPHEUS_VERSION_TOO_NEW` and `MORPHEUS_VERSION_TOO_OLD`
   ("refusal code of the update check, not an environment variable").

3. **"Documented" means named in `docs/user/**` or `docs/developer/**`.** Not `docs/audit`, `docs/adr`, `openspec/` or
   release notes: an operator hardening a deployment reads the guides. Whole-word match, so that
   `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` is not satisfied by a longer or shorter name.

4. **The population is asserted non-empty and the rule is broken once.** `archRule.failOnEmptyShould` does not protect a
   hand-written scan; the test asserts it found at least the variables it excuses plus one. Task 3.3 removes a
   documented name from a guide and watches the failure, then reverts.

5. **The guide states the precedence as the code has it**, and the test of `RemoteApiLaunchOptions` that already covers
   option-over-environment is the proof of the sentence; if no such test exists for this setting, one is added before
   the documentation claims it (task 2.2).

6. **No ADR.** Documentation and a coherence test.

## Risks / Trade-offs

- The rule proves a name is mentioned, not that it is explained. A table row with an empty description would pass.
  Review remains the control for the content.
- A variable whose name is built by concatenation would escape a literal scan. None is today (the MINOS and NEXUS names
  are full literals — measured); the test's comment says so, so that a future concatenation is recognised as a hole.
- A test fixture name under `src/main/java` matching the pattern would have to be excused. Acceptable: the excuse list
  is the place where that is said out loud.
