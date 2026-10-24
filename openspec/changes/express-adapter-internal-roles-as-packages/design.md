# Design

## Context

- The flat package is not only an absence of structure: with 68 package-private types out of 81, it is the boundary
  between `morpheus-api` and the modules that depend on it, and the compiler holds it. `morpheus-cli` uses six types
  of `api`.
- `.claude/rules/architecture.md` ("Règle ArchUnit ou assertion textuelle") refuses a new router until someone has classified it,
  by name and never by suffix. Its reason is the classification, not the list.
- ADR-0109 calls `COMPOSITION_ROOTS` a counter: the alternative it rejected - "bring all the wiring back into
  `morpheus-cli`" - "remains possible: the named list is its counter". Two tests keep it exact in both directions.
- `MorpheusHttpServer` is a hub. It declares four public compile-time constants (`API_PREFIX`, `DEFAULT_HOST`,
  `DEFAULT_PORT`, `MAX_REQUEST_BODY_BYTES`) and five public nested records (three envelopes, two requests). A scan of
  names over the sources on 2026-10-13 - mentions, comments included, so an upper bound and not a measure - finds it
  named by six routers, one service, two classes of the transport plumbing and two of the remote server. A layering
  of packages that puts the server above the routers has those edges pointing upwards.
- `javac` inlines a compile-time constant: a class that reads `MorpheusHttpServer.API_PREFIX` carries no dependency
  on it (`.claude/rules/architecture.md`). A rule on packages does not see that reading; a scan of the source does.
- `HttpRoutesTransportBoundaryArchitectureTest` has changed its groups twice in a month (DT-16, DT-17): a group was
  deleted, another renamed. With a list that is a one-line edit.
- `.claude/rules/security.md` pins text inside named classes (`MorpheusHttpServer` keeps
  `MAX_REQUEST_BODY_BYTES = 65_536`, `MorpheusHttpRequestDecoder` keeps the two strict features). The pins are on the
  class, not on its package.

## Goals / Non-Goals

**Goals**: a role inside the HTTP adapter is a package and its rules name the package; the adapter's packages form no
cycle; what other modules may use of the adapter stays the set it is today; moving a class stops being a change to
dozens of gates.

**Non-Goals**: changing a route, a payload, a refusal or a header; splitting a class by capability (that is
`give-each-cli-command-and-mcp-tool-a-capability-class` for `cli` and `mcp`; `api` is already split);
`morpheus-store-sqlite`; JPMS; migrating the textual assertions on targets without a family (`.claude/rules/architecture.md`); removing a composition
root (that is `own-the-sqlite-connection-policy-in-one-place`).

## Decisions

1. **A package carries a role; a list carries a measured state.** A role is what a class is for the life of the
   class - a router, a part of the remote server, a piece of transport plumbing. A measured state is what a class
   does today and is expected to stop doing - reaching a concrete store. The first is selected by package. The second
   stays a named list, exact in both directions, under the ADR that says it only shrinks. A third kind stays too: a
   table of facts where each entry carries its reason (`RAW_PATH_READERS`, the exemptions of a discovery rule). The
   new ADR states the three kinds; a gate names which kind each remaining list is. *Rejected*: replacing every list
   by a package - see decision 5.

2. **The layout is measured before a file moves.** A throwaway rule assigns every class of `api` to a candidate
   package by a predicate on its name and asks ArchUnit for the cycles between the candidates. The candidates are the
   roles the existing gates already speak of: routers, application-facing services, transport plumbing (decoder,
   writer, parser, query, budget, route table, guards), the local server and its protections, the remote server and
   its identity model, and an entry package. The lot keeps the assignment that needs the fewest extractions, and
   records the measure in the ADR. *Rejected*: writing the layout in this specification - the name scan above is not
   a dependency graph, and an inlined constant is in neither.

3. **What closes a cycle is extracted, not excused.** The envelopes and the request records nested in
   `MorpheusHttpServer`, and `API_PREFIX`, are the expected cases. Each extraction keeps the type's simple name and
   its JSON shape; the textual pins of `rules/security.md` follow the constant to its new owner in the same change.
   An `ignoreDependency` is accepted only by the pattern of ADR-0109, decision 3 - the exact pair of types, with its
   reason - and only if the ADR says why extraction was refused.

4. **The cross-module boundary becomes a rule before visibility is widened.** The entry package holds the types
   another module may use; today's six are the starting set, re-measured. `noClasses().that().resideOutsideOf(the
   adapter).should().dependOnClassesThat().resideIn(the adapter's other packages)`, and beside it a scan of the
   sources of the dependent modules for an import of those packages: a public constant of a newly public class is
   inlined, the rule would not see it, and an import must spell the package (`.claude/rules/architecture.md`: both forms, since
   each sees what the other misses). Written and broken first, while it is still vacuously true, then the classes
   move.

5. **The router groups become packages; `COMPOSITION_ROOTS` does not, yet.**
   - *Router groups.* Placement in one of the group packages is the classification `.claude/rules/architecture.md` asks for, provided
     no router can sit outside all of them: a rule requires every class named `*HttpRoutes` to reside in exactly one
     group package, and the bijection with the sources (`theRulesSeeEveryRouterTheSourcesDeclare`) stays. "Doubly
     classified" and "classified but vanished" become impossible instead of refused. The cost is real and accepted:
     regrouping, which happened twice, becomes a move of files instead of an edit of a list - which decision 6 makes
     cheap.
   - *Family selections.* `haveSimpleNameEndingWith("HttpRoutes")` and `haveSimpleNameStartingWith("MorpheusRemote")`
     become package selections. The second one widens on purpose: `RemoteIdentityAudit`, `RemoteIdentityCodec`,
     `RemoteIdentityCredentialService` and `RemoteIdentityFileStore` belong to the remote model and escape the prefix
     today. The text scans beside them stay, for the inlined constants.
   - *Composition roots.* The list stays, by decision 1. It becomes a package rule on the day every root is a class
     whose role is to compose - which is what `own-the-sqlite-connection-policy-in-one-place` moves towards ("one
     runtime per adapter") and what sprint 11 may change again. The replacement is written now so that nobody
     improvises it: classes of the composition package may reach a concrete store or provider, no other class of the
     adapter may, and every class of that package does reach one - the two directions of today, with "a name is added by a
     consolidated ADR that replaces ADR-0109" becoming "a class is added to the package by one", which the ADR must say.

6. **A gate finds a production source by its type.** One locator in the gate module resolves the source file of a
   class from its simple name within a module and fails when the name is absent or ambiguous; the 141 literal paths
   to an adapter source go through it. It is done first, alone, with no class moved, and it is worth keeping even if
   the split is abandoned. It shares its repository anchor with `locate-the-repository-root-once-per-test-module`.
   *Rejected*: rewriting the 141 literals at each move - that is the maintenance proportional to the number of
   classes the finding is about, moved from the lists to the paths.

7. **One role per pull request, the remote server first.** Its classes depend on the server and the plumbing and
   only `morpheus-cli` depends on them: the smallest move that exercises the entry rule, the validators
   (`validate-m26` checks the three remote classes inside the JAR by path) and the test relocation. Routers last,
   since their group packages replace two test classes.

8. **The count of executed tests is taken before and after each pull request.** A rule that replaces a list also
   retires the test that proved the list (`everyRouterIsClassifiedIntoExactlyOneTransportGroup`, its fixture test).
   Each retired test is replaced by the test of what the package rule can still get wrong - a router in the parent
   package, a group package that a rule no longer names - never by nothing. The quantity is the sum of Surefire
   executions of the module, read in a validation summary, never a count of annotations (`enforcement-choice`).

## What can start before sprint 11 decides, and what cannot

The audit orders AUD-ARC-06 after AUD-ARC-01. Read against
`decide-what-technology-the-application-core-may-touch`, the dependency is narrower than the whole finding:

- **Does not depend on the decision**: decision 6 (the locator), decision 4 (the entry rule), the move of the remote
  server, the transport plumbing, the local server and the routers, the cycle rule, the family selections. None of
  these classes reaches the application's file or HTTP access, and no option of that ADR moves a class of `api`.
- **Depends on it**: where the `*ApiService` classes and the runtimes go, and decision 5's replacement of
  `COMPOSITION_ROOTS`. Under the options that move technology access out of the application, something has to wire
  the new implementations and the composition roots are where it happens; `ingest-a-project-through-the-provider-port`
  changes the one root that reaches a provider (`MorpheusProjectSyncApiService`). Naming a `service` and a
  `composition` package before that is naming them twice.
- **Depends on sprint 5, not on sprint 11**: any change to the list itself.

So the change is two lots: transport roles now, service and composition after the ADR of sprint 11 and after
`own-the-sqlite-connection-policy-in-one-place`. Until the second lot the services stay in the package they are in,
which the cycle rule treats as one slice.

## Risks / Trade-offs

- The split widens visibility of up to 68 types, and decision 4's rule is weaker than the compiler: it runs in the
  gate module, on the imported classpath, and misses an inlined constant without its text half.
  `UnreferencedPublicMethodArchitectureTest` will judge the methods that become public; they have callers, and the
  lot checks it rather than assumes it.
- A package rename costs more than a list edit. It is paid once per regrouping, against a list entry per class.
- The gain is not fewer lines of gate. Two lists of the finding out of three kinds remain, one of them the largest.
  What is gained: a cycle rule that could not be written, a remote ban four classes wider, and moves that no longer
  touch the gates.
- If the measure of decision 2 shows that no layering exists without extracting most of `MorpheusHttpServer`, the
  honest outcome is a smaller split (the remote server and the routers only) and an ADR that says why.

## Open Questions

- **Router groups as packages, or as lists inside one `routes` package?** Recommendation: packages, because the exit
  criterion of the sprint is about them and placement satisfies `.claude/rules/architecture.md`. The alternative is defensible - a capability
  ("reads a body") is not quite a role - and costs nothing if chosen: decisions 1 to 4 and 6 stand either way.
- **Is the split worth its cost at all without the composition list?** It is the question this proposal cannot
  answer for the maintainer. Recommendation: do decision 6 and decision 4 unconditionally, then decide on the measure
  of decision 2.
- **One new ADR, or consolidated ADRs only?** Recommendation: one ADR for the three kinds of selection and the entry rule;
  a consolidated ADR replacing ADR-0109 that points to it, and `.claude/rules/architecture.md` saying that placement is classification.
- **Does the gate module's test declared in `com.morpheus.api` follow `MorpheusHttpRouteTable`, or does the table join the
  entry package?** Recommendation: the test follows; the table is not something another module should build on.
