# Design

## Context

- `morpheus-api` and `morpheus-mcp` are siblings: neither may depend on the other, both depend on
  `morpheus-application`.
- `com.morpheus.application.query.compact` already holds views of the same domain types shared by the three adapters
  (`CompactQueryViewService`, `CompactQueryTypes`) and the serializer both adapters use; `application.query.dsl` names
  the same fields again for query rows. The application layer already knows these field names.
- `morpheus-architecture-tests` has both adapters on its classpath and already starts a real local server in
  `PublicHttpRouteConvergenceTest`.
- A private method is a member ArchUnit imports; a lambda that builds the same map inline is not a method with a
  domain parameter and is invisible to a members rule.
- The CLI was not found to project these eight types through a map of its own (no CLI source carries their field
  names); this is a measurement of 2026-10-13, to be repeated.

## Goals / Non-Goals

**Goals**: one projection per shared domain type; a test that compares the transports on content; a rule that refuses a
new private projection; unchanged public payloads.

**Non-Goals**: redesigning a payload; removing the envelope each transport puts around a shape (pagination, MCP
content wrapper, HTTP status); converging answers that exist on one transport only; replacing maps by records (an open
question, below); splitting `MorpheusMcpToolService` by capability - that is AUD-ARC-09, sprint 12.

## Decisions

1. **The projections live in `morpheus-application`, in a package of their own that depends on the domain and on the
   payload builder only.** *Rejected*: `application.query.compact`, as the audit proposes - it sits under `application.query`, a
   slice of the package cycle AUD-ARC-03 measures, and the compact views are a different, bounded representation; putting the full
   shapes beside them invites one to be mistaken for the other. *Rejected*: `morpheus-domain` - the domain model must
   not know its JSON spelling. *Rejected*: a copy per adapter kept in step by a test - the per-module convention is
   for test support, and a parity test over nineteen methods is the gate without the fix.

2. **What is shared is the shape, not the envelope.** A projection answers one domain value. Lists, pages, absence
   notes (ADR-0151, ADR-0124) and transport wrappers stay where they are. The convergence test compares shapes after
   removing each transport's envelope, and names the envelope it removed.

3. **The convergence test is behavioural and runs through the real surfaces.** For one published fixture
   (`experiments/m0/fixtures/openspec-basic` through the memory store), it asks the HTTP route and the MCP tool that
   show the same domain value and compares the two shapes key by key. It covers every domain type the rule of decision
   4 names; a type shown on one transport only is listed as such, with the transport, so the list is the inventory.
   *Rejected*: comparing the two adapters' methods by reflection or by text - after this change there is one method,
   and the question that remains is whether a transport alters the shape on its way out.

4. **The refusal is an ArchUnit members rule; the audit's "gate on payloads" is decision 3.** Intent in one sentence:
   *no method of a transport adapter takes a shared domain type and returns its payload.* The rule is over
   `com.morpheus.api..`, `com.morpheus.mcp..` and `com.morpheus.cli..`, on parameter and return types, so a renamed
   copy is seen and sprint 12 can move classes freely. The shared domain types are a named list that only grows, each
   entry with the transports that show it. A textual assertion would have to match method names, which is exactly what
   a copy changes. What the rule does not see is said in its comment: an inline construction in a lambda, which only
   the convergence test catches - the two are complementary, neither is kept "in doubt" (`.claude/rules/architecture.md`;
   `.claude/skills/enforcement-choice`).

5. **The versioned requirement record moves with the requirement**, though both its copies are in `morpheus-api`: it
   embeds the requirement shape, and leaving it behind keeps a reason to re-declare `requirement(...)` locally.

## Risks / Trade-offs

- The convergence test may find that the transports already differ somewhere the audit and this reading did not look -
  a shape built inline, an envelope that rewrites a field. Each difference found is either a defect fixed in this
  change or a named, justified difference in the test; none is silently equalised.
- The application layer now owns public field names more visibly. It already did (compact views, query schema); the
  ADR states it once.
- Adapters keep their private projections of types they alone show. That is deliberate: the rule is about shared
  types, and its list is the place where "shared" is decided.

## Open Questions

- **Maps or records for the shared shapes?** The serializer writes records through their components, and a record
  would make the shape a compiled type the OpenAPI schema could be compared with. Recommendation: maps in this change,
  byte-identical output proven first; records as a follow-up the ADR may schedule, because an absent optional value
  needs a decision per field.
- **Should the shapes be compared with the OpenAPI schemas?** Recommendation: not here. `PublishedEnumsMatchJavaEnumsTest`
  shows the pattern exists; a schema comparison belongs with the record decision.
- **The CLI.** If the repeat of the measurement finds a CLI projection of one of the eight types, it joins the same
  change; the rule already covers `com.morpheus.cli..`.
