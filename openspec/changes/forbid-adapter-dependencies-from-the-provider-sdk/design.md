# Design

## Context

- `AdapterSiblingArchitectureTest` imports production classes only and names fourteen package patterns in `NAMED`;
  `everyPackageTheseRulesNameResolvesToImportedClasses` refuses a pattern that matches no imported class. That check
  covers a pattern that is vacant. It does not cover a subject that is announced and never used.
- `com.morpheus.sdk..` resolves to the classes of `morpheus-provider-sdk`. `morpheus-provider-testkit`
  (`com.morpheus.sdk.provider.testkit`) is not on the classpath of the architecture tests, by a declared and reasoned
  absence.
- `ProviderPluginPlatformContractTest#domainAndApplicationDoNotDependOnSdkOrReferencePlugin` holds the opposite
  direction in the POMs; nothing holds this one.

## Goals / Non-Goals

**Goals**: the SDK's outward dependencies are refused by a rule; the list of subjects of the sibling rules cannot
announce a subject that no rule has.

**Non-Goals**: importing `morpheus-provider-testkit` into the architecture tests (its absence is decided and tested);
restricting which `application` packages the SDK may use (a narrower contract than "inward only" is a product decision
about the plugin API, not a repair of a false sentence); a rule on what plugins themselves depend on.

## Decisions

1. **An ArchUnit rule, not a textual assertion.** Intent in one sentence: *the provider SDK has no dependency on an
   adapter*. "No dependency" is the bytecode proposition (`.claude/rules/architecture.md`). No textual twin: the forbidden targets are
   packages, not names, and none of the SDK's possible targets is reached through an inlined constant alone - the lot
   checks this claim rather than trusts it (task 1.3).

2. **Targets: `STORES`, `PROVIDERS`, `INTEGRATIONS`, `MCP`, `API`, `CLI`.** These are the constants the class already
   declares. `PROVIDERS` includes the reference plugin: the SDK must not know an implementation of itself.
   *Rejected*: an allowlist ("the SDK depends only on `domain`, `application` and the JDK") - stricter, but it turns
   every JDK or third-party type into a named exception, and ADR-0129 does not forbid the SDK a library.

3. **The subjects are a constant, and a test compares it to the rules.** The Javadoc sentence was false because nothing
   compared it to the class. The rules of the class are held as values, the class declares the subjects it claims, and
   one test fails when a declared subject is the subject of none of them. The Javadoc then points to the constant
   instead of repeating it. *Rejected*: correcting the sentence only - it is the correction the next added or removed
   rule makes false again. *Rejected*: reading the test's own source as text to find `.that().resideIn` - it would
   accept a subject named in a comment.

4. **The rule page names the package as the code spells it, once.** `.claude/rules/architecture.md` gains a `JAMAIS`
   entry for `com.morpheus.sdk..` citing the rule by name, and its three mentions of `provider.sdk` become
   `sdk.provider`. No test compares these lines to the class; the entry cites the test method so that a reader goes to
   the source.

5. **No ADR.** The decision exists (ADR-0129: the SDK depends on MORPHEUS's provider-neutral contracts; ADR-0028).

## Risks / Trade-offs

- The rule passes on the day it is written, as every rule of this class does. Its proof is the break of task 3.2, not
  its green.
- Holding rules as values changes the shape of a test class nine rules long. The count of `@Test` methods must not
  fall (`architectureTestsMinimum` is a ratchet): each rule keeps its own method.

## Open Questions

- Should the testkit be judged too? It would need to be on the classpath, which `dependency:analyze` refuses today.
  **Recommendation**: no; state in the Javadoc that the rule sees `morpheus-provider-sdk` only, and leave the testkit's
  boundary to its POM.
