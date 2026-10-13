# Design

## Context

- `morpheus-architecture-tests` reads the repository as text - POMs, workflows, scripts, documentation, sources - so
  most of its suites need the root. Its test classes live in several packages (`com.morpheus.architecture`, its
  `d2` and `m20` to `m28` sub-packages, `com.morpheus.api`, `com.morpheus.mcp`).
- The module already has shared, non-test support classes: `m21/PublicSurfaceManifest` and `m19/M19LargeFixtureSupport`.
  A support class there is not a novelty.
- `BoundedWaitOwnershipTest` is the precedent for refusing a private copy of a test helper: it scans the test sources
  as text, names two owners (one per module) and has a test that keeps the two owners in step.
- `morpheus-coverage-report` does not depend on `morpheus-architecture-tests` and must not start to.
- Surefire runs a module's tests with the module directory as working directory; an IDE may run them from the
  repository root. A Git worktree under `.claude/worktrees/` is a complete checkout nested inside another one: climbing
  from it meets the worktree's own root first.

## Goals / Non-Goals

**Goals**: one locator per test module; one anchor, written once and explained; a failure that says where the search
started; a test that refuses the next private copy.

**Non-Goals**: a shared test-support reactor module; a dependency from one test module to another; changing what any
suite reads once it has the root; locating the root from product code.

## Decisions

1. **One owner per module, not one for the repository.** `morpheus-architecture-tests` gets one public support class
   that every package of the module can call. `morpheus-coverage-report` keeps one of its own. *Rejected*: a
   `morpheus-test-support` module - a new reactor module changes the module count the documentation states (AUD-QUA-04), the
   coverage population and the import set of the architecture tests, for one method. *Rejected*: a test-jar dependency
   from the coverage report on the architecture tests - it inverts a build order that the aggregate gate depends on.

2. **One anchor: the reactor's root POM.** The root is the nearest ancestor whose `pom.xml` declares the reactor's
   module list. It is the only one of the eight anchors that *defines* the root instead of recognising it by a
   neighbour: a module POM names the reactor as its parent but declares no modules, and a documentation reorganisation
   cannot move it. *Rejected*: `contracts/public-surfaces.tsv` and `pom.xml`, the majority anchor - it ties every suite
   to the location of the convergence manifest. *Rejected*: `pom.xml` and `distribution` in the working directory or its
   parent - it is the only bounded search and fails when a test is run from a nested directory.
   The owner documents the anchor and the reason in its class comment; the specification states the property, not the
   file content test.

3. **One failure, unchecked, that names what it saw.** The starting directory and the anchor looked for. The sixteen
   `throws IOException` clauses that exist only because of the locator disappear with it; a test method that reads
   files keeps its own.

4. **The refusal is textual.** The proposition is "this declaration must not appear here": a method that compiles
   perfectly well and should not exist. No dependency is involved, the coverage report module is outside the classpath
   of the architecture tests, and test sources are what is scanned - exactly the case of `BoundedWaitOwnershipTest`
   (ADR-0103, `.claude/skills/enforcement-choice`). The rule looks for the *shape*, not the three names: a method that
   returns a `Path`, starts from the working directory and climbs through parents. A renamed copy is the likely next
   one. Comments and string literals are blanked before scanning, as `CliOptionParsingRefusesUnknownOptionsTest` does.

5. **The fixture locators are the same helper and get the same treatment.** A module whose tests climb to
   `experiments/m0/fixtures` has one owner for that climb. This goes beyond the audit's count; it is in scope because
   the exit criterion of the sprint is about the helper, and the shape rule of decision 4 finds them anyway - they are
   either owned or refused.

6. **The owners stay in step by a test**, as the two `BoundedWait` do: same anchor, same failure wording. Without it
   the convention "copy per module" reproduces across modules the drift this change removes inside one.

## Risks / Trade-offs

- A shape rule can catch a method that climbs for another reason. Two test files of the architecture module contain a
  parent climb without declaring a locator; the implementation lot reads them before fixing the pattern, and an honest
  exception is listed by name with its reason, not excused by a looser pattern.
- 87 files change in one lot. The change is mechanical and the suites prove it: every one of them fails at once if the
  owner is wrong. It is delivered as one commit per package to keep the review readable, not as 87.
- Suites that pass today from a directory the new anchor does not reach would fail. None is expected: Surefire and the
  validators run from a module directory or the root.

## Open Questions

- **The anchor** (decision 2). Recommendation: the root POM's module list. The maintainer may prefer the majority
  anchor to keep the diff smaller; the specification holds either way, the owner's comment says which.
- **Scope of decision 5.** Recommendation: include the three other modules in a second lot of the same change. If
  refused, the shape rule must list their eleven methods as named exceptions, which is the list this change exists to
  avoid.
