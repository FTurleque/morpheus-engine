# Design

## Context

- A record's canonical constructor is where its invariants are validated (`rules/code-style.md`, anatomy of
  `DomainIdentity`). Every secondary constructor here delegates to it, so no invariant is bypassed; what is lost is the
  caller's knowledge of what it asked for.
- The scan that produced the proposal's table reads sources: it finds `new X(` and counts top-level arguments. It
  cannot tell two overloads of the same arity apart and does not see a constructor reference (`X::new`). The compiler
  is the authority: removing a constructor and compiling the reactor names every caller.
- `UnreferencedPublicMethodArchitectureTest` excludes the SDK, the testkit and the reference plugin, and does not judge
  constructors.
- Test support is copied per module rather than shared through a new module or a new dependency edge - the convention
  the repository already follows.

## Goals / Non-Goals

**Goals**: one way to construct a record; a default a caller obtains is one it named; the rule page true.

**Non-Goals**: non-record classes; private or package-private secondary constructors used by a factory of the same
type; changing any default value; the `check_product_update` MCP stub (a public surface - see Open Questions).

## Decisions

1. **The enforced property is structural: a record declares no public constructor besides its canonical one.**
   Intent in one sentence: *a record offers one public way to be constructed*. Neither of the two shapes of `.claude/rules/architecture.md`
   exactly: it is not a dependency, and a textual scan for "Compatibility constructor" would forbid a comment, not a
   constructor. The test reads the imported production classes and compares each record's public constructors to its
   components. *Rejected*: the textual ban on the comment. *Rejected*: extending the unreferenced-method gate to
   constructors - eight of the seventeen are referenced, by tests, and that is the larger half of the problem.

2. **Unused and test-only constructors are removed, not converted.** A factory nobody in production calls is the same
   dead surface under another name. Tests call the canonical constructor; where the argument list makes a test
   unreadable (`SnapshotBusinessContent` has seventeen call sites in tests), the module's tests get a fixture method of
   their own. No shared test-support module is added.

3. **Constructors with production callers become named factories on the record.** The name states the assumption:
   a `Constraint` whose semantics the provider does not know, a `NormalizedProjectContent` without change artefacts or
   without acceptance criteria. The exact names are chosen in the lot from what each caller means; three call shapes of
   `NormalizedProjectContent` need not become three factories if two mean the same thing. This is line 13 of the rule
   page applied, and it keeps `UNKNOWN` visible at the call site, which the tri-state rule asks for.

4. **The exception list starts empty and is checked both ways.** If the lot cannot convert one constructor, it is
   named with its reason; a name that no longer has a second constructor fails the test.

5. **The rule page says what the build enforces and what the word "shim" does not cover.** Line 13 stops being a
   preference for records; line 61 keeps its interdiction and gains two bounds: a `default` method that gives a port a
   correct fallback is not a shim (the two of `VersionedRequirementStore` are re-documented as that, their Javadoc no
   longer speaking of compatibility), and a public name retained by a decision recorded in
   `contracts/public-surfaces.tsv` is a contract, not a shim. The page cites the test by name.

6. **No ADR.** The rule exists; the change makes the code obey it.

## Risks / Trade-offs

- A broad mechanical edit of test sources (the scan counts about eighty test call sites over the eight test-only
  constructors and the four that become factories). The risk is a test that silently changes meaning; a fixture that
  reproduces exactly the removed default avoids it, and the tests' assertions are not touched.
- Plugin authors who construct `NormalizedProjectContent` or `Constraint` through a removed constructor stop
  compiling. The reference plugin is updated in the lot and is the worked example.
- The structural test uses reflection over records; it is a test, outside `name-every-use-of-reflection`.

## Open Questions

- **Does removing public constructors of `application` and `domain` records require a new `sdk.apiVersion`?**
  A plugin compiled against the old constructors fails at link time, not at discovery. **Recommendation**: yes if the
  maintainer considers these records part of the plugin contract (ADR-0129 says the SDK depends on MORPHEUS's provider-neutral
  contracts, which makes them so); decide before the lot, since a version change touches the compatibility check and its
  documentation. If the answer is "not now", `Constraint` and `NormalizedProjectContent` stay on the exception list
  with that reason, and the rest of the change proceeds.
- **`check_product_update`.** The MCP tool is an error stub kept under its historical name and declared in the
  manifest. Removing it is a public-surface change (`.claude/skills/public-capability`) that M28 clients would see.
  **Recommendation**: keep it; decision 5 makes the rule page say why it is not a violation.
- **The two `default` methods.** Both production stores override them; several test doubles rely on them.
  **Recommendation**: keep them and correct their Javadoc. Making them abstract is cleaner and costs those test doubles.
