# Design

## Context

- A dependency version in a module POM wins over `<dependencyManagement>`, silently. That is why the literal `1.0.4`
  hides nothing today and would hide a difference the day the Reactor line moves.
- The Enforcer runs `dependencyConvergence` (`pom.xml:190`), and `dependency:analyze` runs with `failOnWarning`.
- `morpheus-cli/pom.xml` configures `dependency:analyze` with a `usedDependencies` entry for the runtime-only
  `slf4j-nop`; that configuration names the artifact, not its version.
- The update bot reads versions where they are written. Property-backed versions in the root POM are what it already
  updates.

## Goals / Non-Goals

**Goals**: one place decides a version; the rule page is true; a new exception cannot enter unnoticed.

**Non-Goals**: changing any version; moving `sqlite-jdbc` to another line; the scope of a dependency (it stays in the
module, where it is a fact about the module); the versions inside the root POM's own profiles.

## Decisions

1. **`reactive-streams`: delete the literal, add nothing.** The imported Reactor BOM manages it. *Rejected*: a root
   entry with a `reactive-streams.version` property (the audit's first option) - it would place a second authority
   beside the BOM, and the two could disagree after a Reactor update; the root comment on the Reactor line already says
   the transport follows the MCP SDK's train. The module keeps declaring the dependency. No source of the module names
   `org.reactivestreams` (searched on 2026-10-13), so the declaration is presumably there because `dependency:analyze`
   sees the reference in the bytecode of the Reactor calls and would report it "used undeclared" - unverified here,
   since this specification runs no build: task 4.2 settles it, and if the analyser reports the dependency unused
   instead, it is removed from the module rather than kept without a reason.

2. **`sqlite-jdbc`, `slf4j-nop`, `archunit`: one managed entry each in the root POM, using the property that exists.**
   `slf4j-nop` sits next to `slf4j-api` under the same property, which makes the alignment the comment asks for a
   property of one block. The `runtime` and `test` scopes stay in the modules.

3. **Plugins too, in the same lot.** `maven-jar-plugin` is already in the root `<pluginManagement>` and
   `morpheus-cli` restates its version; `maven-shade-plugin` is versioned in the module alone. The rule page speaks of
   dependencies, but the reason is the same and the cost is two lines. *Rejected*: leaving plugins out - the test would
   need a carve-out whose only justification is that the audit did not look there.

4. **The gate is textual, over the POM files.** Intent in one sentence: *no module POM states a version*. A `.xml` file
   is outside any bytecode rule (`.claude/rules/architecture.md`: text is the only grip). The test reads the `<module>` list of the root POM,
   as `ImportedClasspathCompletenessTest` does, so a new module is judged from the day it is declared; it accepts
   `<version>${project.version}</version>` and the `<version>` of the `<parent>` block, and nothing else. It names the
   file and the line of what it refuses. No exception list: after this change there is none to name.

5. **The parent version stays a literal.** Each module's `<parent><version>` is the product version, which
   `ProductReleaseContractTest` already pins; it is not a dependency version and the gate says so in its message.

6. **No ADR.**

## Risks / Trade-offs

- A managed entry applies to every module, including transitive resolutions. For `sqlite-jdbc`, `slf4j-nop` and
  `archunit` no other module resolves them today; the before/after tree is the check.
- Reading XML as text can be fooled by a comment containing `<version>`. The test ignores comments, and the break of
  task 3.2 includes one.

## Open Questions

- **Should the root POM's managed entries be the only place a third-party coordinate appears with a version, profiles
  included?** The `d2-security`, SpotBugs and PIT profiles version their plugins by property in place.
  **Recommendation**: leave the profiles; they are in the root POM, which is the single place the rule names.
