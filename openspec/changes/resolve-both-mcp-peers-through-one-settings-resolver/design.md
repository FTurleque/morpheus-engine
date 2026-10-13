# Design

## Context

- Each settings type is a public record with public constants (property names, environment names, the default and the
  maximum time bound) and a static `resolve(environment, properties)`. `ProductionIntegrityContractTest` reads the two
  maximum bounds; the runtime and the gateway of each integration read the record.
- A resolution never throws for a bad configuration: it returns a record whose `configurationError` joins the errors,
  and whose state is disabled, configured or invalid. Third-party code runs only when the JAR is pinned.
- The record's compact constructor holds the invariants that must not be skipped (absolute launcher, bounded time).
- The resolution uses the file system (is the JAR a file, the real path of the launcher, the runtime's `bin/java`) and
  `ExternalJarIntegrity` (pin verification and normalisation).
- `morpheus-mcp-transport` ships the package `com.morpheus.integration.mcp`, which holds `McpPeerLaunch`; AUD-ARC-07
  (sprint 10) questions that package name. It depends on no MORPHEUS type by rule.
- `document-every-environment-setting-the-product-reads` documents the environment settings the product reads; the
  ten names of the two peers (five properties and five variables each, by pattern) must not change under it.

## Goals / Non-Goals

**Goals**: one implementation of the resolution; the two peers provably alike on the same inputs; the launcher
invariants held where they are implemented; no change to a setting name or to what a status shows.

**Non-Goals**: merging the two public settings types into one (callers and tests name them); the gateways and the
runtimes, which really differ; the trivial twins (exception, factory) - six and twelve lines that share nothing worth
a type; a generic "peer" abstraction beyond what the two existing peers need.

## Decisions

1. **The resolver lives in `morpheus-application`, in the package of `ExternalJarIntegrity`.** It is the same concern -
   deciding whether external code may be started and from which verified file - and the two integrations already
   depend on it. *Rejected*: `morpheus-mcp-transport`. It is the natural neighbour of `McpPeerLaunch`, but it would
   need the pin verification passed in as a function to respect its rule, which hides the one dependency a reader of
   this code must see; and its package is under review. *Rejected*: a copy per integration held equal by a parity
   test - that is today's state with a better test; the per-module convention is for test support, and this is a trust
   path. *Rejected*: a new module - one class.
   **Consequence for sprint 11**: the resolver is a new class of the application that reads the file system. Under
   `decide-what-technology-the-application-core-may-touch` it must enter the named list knowingly, with the status the
   ADR gives `ExternalJarIntegrity`, and it follows that class if
   `move-marked-technology-access-behind-application-ports` moves it. The two changes are sequenced: this one is
   implemented after the sprint 11 ADR is accepted, or the ADR is written with this class in its inventory.

2. **The peer is a parameter with three parts: its name, its property prefix, its environment prefix.** The ten
   setting names derive from them by one pattern, and each integration keeps its public constants - computed from the
   same pattern or written out and compared by the parity test, whichever keeps `javac`-inlined constants readable for
   the textual gates that mention them. Nothing else is per peer.

3. **The two public settings types remain, as thin records over the shared result.** They keep their components,
   their constants, `state()` and `enabled()`. The invariants of the compact constructor are those of the shared
   result; a record cannot be built around a result that broke them.

4. **The messages take the peer's name and nothing else.** The one difference today - "runner JAR" on the NEXUS side
   for the file check only, while its two other JAR messages say "NEXUS JAR" - is not a property of the peer.
   Recommendation: one wording, `<PEER> JAR is not a file`; it is the single visible change and is listed as such.

5. **Enforcement.**
   - *An integration resolves nothing itself*: an ArchUnit dependency rule. Intent: *the settings types of the
     integrations do not depend on the file system API nor on the pin verification; only the resolver does.* This is a
     dependency, the classes are in the imported classpath, and none of the targets is reached through an inlinable
     constant.
   - *The launcher literals*: textual, as today. `PeerLauncherArchitectureTest` forbids a bare `java` literal; a
     literal handed to a launch is not a dependency (its own comment says so). The assertions are pointed at the
     resolver, and extended to the two settings files as an absence - the rule is at least as strict as before.
   - *Parity*: a behavioural test in `morpheus-architecture-tests`, on the pattern of the persistence parity tests:
     the same table of inputs resolved for both peers, the results equal once the peer's name is abstracted.
   Each is broken once (ADR-0103; `.claude/skills/enforcement-choice`).

6. **The behaviour tests stay per peer and are not thinned.** One parameterised suite over the two peers, in the
   module of the resolver, replaces the four twins only if the execution count read from Surefire does not fall;
   otherwise the twins stay and call the shared resolver through their own settings type. Test *support* that each
   integration needs (its fixture MCP server) stays copied per module: that is the convention, and it is not this
   finding.

## Risks / Trade-offs

- A refactor of the code that decides whether third-party code runs. The four existing suites pass unmodified before
  any of them is reorganised, and `MinosUnpinnedExecutionTest` with them.
- Sequencing with sprint 11 delays this change. The alternative - landing it first - adds an unlisted file-system
  class to the application the week an ADR inventories them; the order costs less than the correction.
- A third peer would now cost three names. That is the benefit, and also the temptation: the parameter stays at three
  parts until a third peer shows what else differs.

## Open Questions

- **Home of the resolver** (decision 1). Recommendation: `morpheus-application`, beside `ExternalJarIntegrity`, after
  the sprint 11 ADR. If that ADR decides the application core is pure, both move together to wherever external-code
  trust goes.
- **The NEXUS message** (decision 4). Recommendation: align. If refused, the peer parameter gains a fourth part for one
  noun, and the parity test lists the exception.
- **The trivial twins.** Recommendation: leave them; say so in the pull request so the next audit does not re-open them.
