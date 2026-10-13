# Design

## Context

- `SourceScanPolicy` is a public record of `morpheus-application` with several constructors taking the
  `followSymbolicLinks` flag; five Java files construct one or read the flag.
- The gateways stage a verified copy through `ExternalJarIntegrity.stageVerifiedCopy` when a pin is present; the copy is
  what is launched and deleted afterwards (`staged.orElseThrow()` is already used at the two deletion sites).
- `morpheus-provider-sdk` is a public API for third-party plugins: a method of it that MORPHEUS does not call is not
  dead. `morpheus-provider-testkit` and `morpheus-provider-reference` are tooling.
- Public method names are not constants: `javac` does not inline a call. But a name-based scan cannot tell two methods
  of the same name apart, and a bytecode rule ("this method has no caller") is exact.

## Goals / Non-Goals

**Goals**: remove what cannot run; make the fail-closed launch an invariant the compiler holds; refuse the next
unreferenced public method.

**Non-Goals**: unused private or package-private members (the compiler and the IDE report those); unused classes;
the sixteen methods' callers in a future feature - a surface comes back with its consumer and its manifest line.

## Decisions

1. **Delete the six `snapshot*` methods rather than give them a consumer.** They have no CLI command, MCP tool, HTTP
   route or test. A surface that is wanted is delivered with its manifest line (`public-capability`); a method kept
   "because it may be useful" is the definition of what the rule forbids. *Rejected*: wiring them to a route in this
   change - that is a feature, with a contract, not a clean-up. The maintainer can overrule for that family (task 1.1).

2. **Remove the symbolic-link flag from the record, do not keep it "always false".** A parameter that accepts one value
   is a branch waiting to be re-enabled without a test. The scanner keeps refusing links, unconditionally. *Rejected*:
   failing the scan explicitly when the flag is true - the constructor already does, and the branches stay.

3. **`staged.orElseThrow()` in both gateways.** The launch then cannot name the unverified JAR: weakening `enabled()`
   later yields an exception at the launch site, not an execution. The existing test of the unpinned case keeps passing
   unmodified.

4. **The gate: a bytecode rule, by enforcement-choice.** Intention in one sentence: "a public method of a product
   module is called by some class of the repository, tests included, or overrides something". That is a dependency
   question, so ArchUnit answers it exactly: for each public method declared in `com.morpheus.domain..`,
   `..application..`, `..api..`, `..mcp..`, `..cli..`, `..store..`, `..provider.(openspec|markdown|synthetic)..` and
   `..integration..`, require at least one access from another method, or an override of a supertype method, or an entry
   in a named list with a reason (record accessors and methods reached only by reflection-free frameworks do not exist
   here; `main` methods and JUnit entry points are the expected entries). `com.morpheus.sdk..` is excluded as a
   published API. The rule must be broken once: add an uncalled public method and watch it fail. *Rejected*: the
   name-count scan the audit used - it misses a dead method that shares its name with a live one.

   If ArchUnit's view of test classes is incomplete (the imported set is the classpath of the architecture module),
   the rule is restricted to the modules whose tests are importable, and the restriction is written down with the
   measured count - not silently.

5. **No ADR.** Application of an existing rule.

## Risks / Trade-offs

- Removing a constructor parameter of a public record breaks any out-of-tree caller. There is none: the record is not
  part of the plugin SDK.
- The gate will report record accessors and accessors used only by serialisation, if any. They are measured first
  (task 1.2) and either excused by a category with a reason or the rule is scoped to non-record classes; the choice and
  its count go in the pull request.
- Deleting six query methods removes code a future route might have used. It returns with that route.
