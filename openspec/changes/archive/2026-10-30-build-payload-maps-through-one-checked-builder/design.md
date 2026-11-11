# Design

## Context

- The adapters build their answers as `Map<String, Object>` and serialise them with `CanonicalJsonSerializer`
  (`com.morpheus.application.query.compact`), which sorts keys, refuses a non-string key by name
  (`canonical JSON maps require String keys`) and writes an absent value as JSON `null`.
- `MorpheusMcpToolCatalog.props` builds JSON Schema property maps for the tool catalogue, not answers; its body is the
  same function.
- `application.query.{dsl,saved,export}`, `application.policy` and `application.reasoning` carry isolation rules of
  their own; `application.query.compact` does not, but it sits under `application.query`, one of the
  slices of the package cycle AUD-ARC-03 measures.
- `javac` does not inline a method call: a class that uses the shared builder depends on it in bytecode, and a private
  method is a member ArchUnit imports.

## Goals / Non-Goals

**Goals**: one builder; three named refusals; the absent-value policy explicit at the call; byte-identical public
answers; a rule that refuses the eleventh copy under any name.

**Non-Goals**: replacing payload maps by typed records (see Open Questions); changing the canonical serializer; the
other uses of `Map<String, Object>` in the adapters; the projections themselves, which are AUD-ARC-10.

## Decisions

1. **The builder lives in a leaf package of `morpheus-application`.** A package that depends on no other MORPHEUS
   package, held by a rule, so that it cannot join the package cycle sprint 11 has to shrink. *Rejected*: next to
   `CanonicalJsonSerializer` in `query.compact` - convenient, and under a slice AUD-ARC-03 counts in its cycle. *Rejected*:
   one copy per adapter (the per-module convention) - that convention is for test support; this is the serialisation
   boundary of two public adapters, and a rule added to one copy would not protect the other. *Rejected*:
   `morpheus-domain` - a payload map is not part of the model.

2. **Three refusals, each named.** An odd number of arguments, a key that is not a `String` (the refusal names the
   position and the type received), and a key given twice (the refusal names the key). The third is new behaviour: no
   call site is expected to rely on overwriting, and the first task proves it.

3. **Two entry points, by what an absent value means.** One admits an absent value and one refuses it by name; the
   call reads as what it promises. *Rejected*: one permissive builder for all - it removes the check the MCP copies
   carry today. *Rejected*: one strict builder - the HTTP history answer legitimately carries an absent side of a
   difference, and turning it into an empty string changes a public payload.

4. **The result is unmodifiable and iterates in the order given.** Not observable through the canonical serializer,
   but two of today's copies return `Map.copyOf`, whose iteration order changes from one JVM start to the next; a
   builder that feeds a product selling determinism should not offer that to a future caller that iterates.

5. **Enforcement: an ArchUnit rule on the signature, and a textual twin.** Intent in one sentence: *no type of the
   product other than the owner declares a method that turns an argument array into a map.* A members rule over
   `com.morpheus..` (a method whose only parameter is `Object[]` and whose return type is `Map`) sees a renamed copy -
   `props` is the proof one exists - and is not fooled by a comment. The audit proposed a dependency rule that
   *requires* the builder; a private copy depends on nothing but the JDK, so no dependency rule can see it, and
   requiring a dependency from every class that builds a map is not expressible. The textual twin scans every
   `src/main/java` for the cast `(String) entries[`-shaped key cast in a method taking `Object...`, because
   `morpheus-provider-testkit` is outside the imported classpath (`.claude/rules/architecture.md`; `.claude/skills/enforcement-choice`,
   "in doubt, keep both"). The leaf property of decision 1 is a plain ArchUnit dependency rule.

## Risks / Trade-offs

- The duplicate-key refusal can surface a latent defect in a call site. That is the point; it is found in task 1.3,
  before the builder exists, not in production.
- Some sixty call sites change their receiver. The adapter tests compare whole payloads and run unmodified.
- The signature rule would also refuse a legitimate `Object[]`-to-`Map` method written for another purpose. None exists
  today (measured); one that appears is either the builder under another name or an exception listed with its reason.

## Open Questions

- **Maps or records?** `CanonicalJsonSerializer` already serialises records through their components, and a typed
  record per payload would make most of these maps unnecessary. It is a larger decision - it meets the OpenAPI
  schemas, the tri-state rule for absent values and AUD-ARC-10 - and is left to the maintainer. Recommendation: keep
  maps in this change, and let the ADR of `project-shared-domain-types-to-one-public-shape` say whether the shared
  shapes become records.
- **The package name.** Recommendation: a leaf package shared with the pure helpers of
  `derive-digests-and-stable-keys-in-one-place`, named for what it is rather than `util`; the sprint 11 ADR lists it.
