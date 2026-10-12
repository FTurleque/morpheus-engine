# Design

## Context

- The rule keys in use are Sonar's (`java:S1181`, `java:S106`, `java:S107`) and the compiler's (`unchecked`).
- Most `java:S1181` sites are deliberate: a failure of any kind must release a resource or roll back
  (`SqliteTransactionRunner` is asserted to catch `Error`).
- A cast of `Object` to `Map<String, Object>` is unchecked by nature; what can be checked is that the value is a `Map`
  and that its keys are strings.

## Goals / Non-Goals

**Goals**: the reason of a suppression is written where the suppression is; a boundary validates what it receives.

**Non-Goals**: removing suppressions that are right; a Sonar quality gate; the test sources (counted and reported, not
gated - a test's suppression hides nothing from a caller).

## Decisions

1. **A comment on the annotation's line or on the line above, and the test reads it.** The test requires a comment that
   is not empty and not the rule key repeated. It cannot judge that the reason is good; it makes the reason exist, where
   a reviewer reads it. Textual by nature: no rule on the bytecode sees a comment.

2. **`src/main` only.** The 33 sites of the tests are listed in the pull request; gating them is a separate decision.

3. **The boundary casts become a validation with a named refusal.** `instanceof Map`, keys checked to be strings, a
   refusal that names the field. The `unchecked` suppression that remains - the cast after the check - carries that
   reason. *Rejected*: leaving the cast with a comment - at a boundary the value comes from outside, so the comment
   would assert what nothing verified.

4. **No ADR.**

## Risks / Trade-offs

- A refusal where a `ClassCastException` used to surface later is a change of error for a malformed call; the tests of
  the MCP tools and the HTTP routes say what a caller sees.
- A comment requirement produces comments. The review of the lot reads the twenty.
