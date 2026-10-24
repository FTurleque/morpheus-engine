# Proposal

## Why

AUD-DEP-13 and AUD-DEP-14 (sprint 10) report module POMs that carry a dependency version, against the repository
rule. Re-read on `develop` at `8b26d3d7` on 2026-10-13, both **hold**, and the same scan finds **a fourth site the
audit did not list**.

The rule, `.claude/rules/build.md:21` and `:28`: "Déclarer toute dépendance dans le `<dependencyManagement>` du POM
racine, puis la référencer sans version dans le module" and "Jamais de version de dépendance en dur dans un POM de
module".

Every `<version>` of a module POM that is not `${project.version}` on a sibling module, measured today:

| Site | Version | Finding |
|---|---|---|
| `morpheus-mcp-transport/pom.xml:28` - `org.reactivestreams:reactive-streams` | literal `1.0.4` | AUD-DEP-13 |
| `morpheus-store-sqlite/pom.xml:30` - `org.xerial:sqlite-jdbc` | `${sqlite-jdbc.version}` | AUD-DEP-14 |
| `morpheus-cli/pom.xml:76` - `org.slf4j:slf4j-nop` | `${slf4j.version}` | AUD-DEP-14 |
| `morpheus-architecture-tests/pom.xml:35` - `com.tngtech.archunit:archunit` | `${archunit.version}` | not in the audit |
| `morpheus-cli/pom.xml:101` - `maven-jar-plugin` | `${maven.jar.plugin.version}` | plugin, not in the audit |
| `morpheus-cli/pom.xml:115` - `maven-shade-plugin` | `${maven.shade.plugin.version}` | plugin, not in the audit |

The root `<dependencyManagement>` (`pom.xml:93-131`) imports four BOMs (JUnit, MCP, Reactor, Jackson) and manages
`slf4j-api` alone.

Two facts change the remedy the audit proposes:

- **`reactive-streams` is already managed.** The Reactor BOM the root imports declares
  `org.reactivestreams:reactive-streams` (`reactor-bom-2024.0.18.pom`, read in the local repository: version `1.0.4`).
  The module's literal therefore overrides a managed version with the same value. No new root entry and no new
  property are needed: the literal is deleted. The audit left this to be settled by `dependency:tree`; it is settled
  by reading the BOM, and the lot confirms it.
- **`slf4j-nop` and `slf4j-api` are aligned by a shared property, not by the mechanism.** The comment at `pom.xml:124`
  says the API must stay aligned with the backend the CLI packages; the backend's version is written in another file.

Nothing compares the rule to the POMs. `D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned`
asserts that six literal strings are present in the root POM; it says nothing of a module POM. A fifth site would be
accepted the same way, with the four existing ones as precedent.

## What Changes

- The three third-party dependencies versioned in a module are declared in the root `<dependencyManagement>`; the
  four module POMs reference their dependencies without a version.
- The two plugin versions of `morpheus-cli` move to the root `<pluginManagement>`.
- A test reads every module POM of the reactor and refuses a `<version>` other than `${project.version}`.
- The rule page says what the build enforces, including the one form it allows.

## Capabilities

### New Capabilities

- `dependency-version-management`: the version of every dependency and plugin of the reactor is decided in the root
  POM, and a module POM cannot state one.

### Modified Capabilities

(none)

## Impact

- `pom.xml`, `morpheus-mcp-transport/pom.xml`, `morpheus-store-sqlite/pom.xml`, `morpheus-cli/pom.xml`,
  `morpheus-architecture-tests/pom.xml`.
- `morpheus-architecture-tests`: one textual test.
- `.claude/rules/build.md`: the two sentences and the example.
- **No resolved version changes.** The proof is the dependency tree and the SBOM before and after, identical.
  `distribution/third-party/components.tsv` and the generated notices are untouched.
- `D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` keeps passing: the property
  `<sqlite-jdbc.version>` stays in the root POM, where the test looks for it.
- No ADR (ADR-0133 decides the multi-module build; this applies a rule that exists). No contract, no ratchet beyond
  the count of architecture tests.
- Related: `keep-reactor-on-its-patched-line` owns `reactor-bom.version` and the update bot's rule for it; this change
  does not touch either.
