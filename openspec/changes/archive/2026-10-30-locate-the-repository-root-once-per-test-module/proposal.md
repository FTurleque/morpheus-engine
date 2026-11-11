# Proposal

## Why

AUD-QUA-02 (sprint 9) reports 73 private copies of the repository root locator in the tests, under three names and
seven divergent bodies. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and has grown**:

- 88 declarations of a method returning the repository root (`Path repositoryRoot()`, `Path repoRoot()`, `Path root()`)
  under `src/test`: 87 in `morpheus-architecture-tests` (87 of its 161 test files), one in `morpheus-coverage-report`
  (`AggregateCoverageGateTest.java:431`). The audit counted 73, 72 of them in the architecture tests: fifteen copies
  were added in four days.
- By name: 58 `repositoryRoot()`, 29 `repoRoot()`, one `root()`
  (`PagedResponseVocabularyArchitectureTest.java:125`).
- 15 distinct bodies after whitespace normalisation (the audit counted seven), and **eight different anchors**:

  | Copies | What marks the root | How far it looks |
  |---|---|---|
  | 55 | `contracts/public-surfaces.tsv` and `pom.xml` | every ancestor |
  | 17 | `pom.xml` and the `distribution` directory | the working directory and its parent only |
  | 5 | a `pom.xml` whose text names `morpheus-engine` and `<modules>` | every ancestor |
  | 4 | `contracts/public-surfaces.tsv` alone | every ancestor |
  | 3 | `pom.xml` and `docs/architecture/adr` | every ancestor |
  | 2 | `pom.xml` and `morpheus-application` | every ancestor |
  | 1 | `config/m21-quality-ratchets.properties` and `pom.xml` | every ancestor |
  | 1 | `pom.xml`, `morpheus-application` and `distribution` (coverage report module) | every ancestor |

- Three failure types for the same condition: `IllegalStateException`, a checked `IOException` (16 declarations carry
  `throws IOException`) and `AssertionError`; five different messages, most of which do not say where the search
  started.

The copies are not equivalent. Seventeen suites stop at the parent of the working directory and the other seventy climb
to the file system root; moving `contracts/`, `distribution/` or the ADR directory breaks one group of suites and not
the others. The frozen `docs/` tree was reorganised on 2026-10-11: the three copies anchored on `docs/architecture/adr`
were rewritten by hand that day, which is what a single owner would have made one edit.

Not reported by the audit, measured on the way: the same climb exists under another name in three other test modules -
eight `fixture(String)` methods in `morpheus-cli` tests and two in `morpheus-provider-synthetic` tests walk up from the
working directory until `experiments/m0/fixtures` appears, and `MorpheusApiProjectSyncIntegrationTest` has a third
shape. They are the same helper with a different last step.

## What Changes

- One owner per test module locates the repository root, with one documented anchor and one failure that names the
  starting directory and the anchor it looked for.
- Every test class of the module asks that owner; none declares a locator of its own.
- A governance test refuses a new private locator, the way `BoundedWaitOwnershipTest` refuses a private bounded wait.
- The owners of different modules are deliberate copies and a test keeps them in step.

## Capabilities

### New Capabilities

- `repository-root-location`: how a test finds the repository it runs in, and who is allowed to decide it.

### Modified Capabilities

(none)

## Impact

- Test sources only: `morpheus-architecture-tests` (87 files today), `morpheus-coverage-report` (one), and, if the
  maintainer accepts decision 5 of the design, the test sources of `morpheus-cli`, `morpheus-provider-synthetic` and
  `morpheus-api`. No product code, no contract, no workflow.
- **Test support, not production code.** Inside one module the copies are plain duplication and are removed. Across
  modules the repository convention applies - test support is copied per module rather than shared through a new
  reactor module or a new dependency edge - so each module keeps exactly one owner, and that is stated, not tolerated.
- One new textual governance test; `architectureTestsMinimum` rises with it, never falls.
- No ADR: no structural decision is taken. No ratchet is lowered. Coverage is not affected: neither scale counts test
  sources.
- Independent of the other changes of sprint 9. Sprint 12 moves product classes, not test support.
