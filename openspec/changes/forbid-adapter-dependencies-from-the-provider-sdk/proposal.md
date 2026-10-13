# Proposal

## Why

AUD-ARC-08 (sprint 10) reports that `com.morpheus.sdk` is the subject of no ArchUnit rule, while the Javadoc of the test
that names it says it is. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds exactly**:

- `AdapterSiblingArchitectureTest.java:17-18` opens with "Dependency rules [...] with the provider SDK, the providers,
  the stores, the MINOS and NEXUS integrations and the MCP adapter as subjects".
- The nine rules of the class take as subject `DOMAIN, APPLICATION` (`:57`), the three built-in providers (`:66`),
  `PROVIDERS` (`:75`), `STORES` (`:92`), `MINOS` (`:109`), `NEXUS` (`:118`) and `MCP` (`:127`). `SDK` (`:37`) appears
  only as a forbidden target, at `:58`, `:67`, `:93`, `:110` and `:119`.
- No other class of `morpheus-architecture-tests` takes `com.morpheus.sdk..` as subject: the other mentions are file
  paths read as text.
- The boundary holds in fact: `morpheus-provider-sdk/pom.xml` declares `morpheus-domain`, `morpheus-application` and
  JUnit, and the imports of its sources name only `com.morpheus.application.{product,provider,read,security}`,
  `com.morpheus.domain.{diagnostic,provider,source}` and the JDK.

The class says at `:19-20` what these rules are for: to refuse by name "a future dependency added to a POM". For the SDK
that refusal does not exist. The SDK is the one module shipped to third parties to compile against; a dependency from
it to a store or to a transport would be published with it.

Two things found while re-reading belong to the same defect - a text that names the SDK boundary and is not compared to
it:

- The repository rules spell the SDK's package `provider.sdk` (`.claude/rules/architecture.md:137` and `:156`,
  `.claude/skills/milestone-quadruplet/SKILL.md:54`, `.claude/agents/architect.md:45`). The package is
  `com.morpheus.sdk.provider`; `PolicyPlatformArchitectureTest.java:24` spells it correctly, and the Javadoc of
  `AdapterSiblingArchitectureTest` warns at `:25-27` that a pattern spelled the other way matches nothing.
- `morpheus-provider-testkit` also lives under `com.morpheus.sdk.provider` (in `.testkit`) and is absent from the
  imported set (`ImportedClasspathCompletenessTest.NOT_ON_THE_CLASSPATH`). A rule whose subject is `com.morpheus.sdk..`
  therefore judges the SDK module only, and must say so.

## What Changes

- A rule takes the provider SDK as subject and forbids it every adapter: stores, providers, integrations, the MCP
  adapter, the HTTP adapter, the CLI.
- The class no longer lists its subjects in prose: the list of subjects is a declared constant, and a test fails when a
  declared subject is the subject of no rule.
- The repository rules spell the SDK's package as the code does and name the new rule.

## Capabilities

### New Capabilities

- `provider-sdk-dependency-boundary`: the published provider SDK depends on the domain and the application only, the
  build refuses anything else, and the sibling rules cover every subject they announce.

### Modified Capabilities

(none)

## Impact

- `morpheus-architecture-tests/.../AdapterSiblingArchitectureTest.java`: one rule, one self-check, the Javadoc.
- `.claude/rules/architecture.md`, `.claude/skills/milestone-quadruplet/SKILL.md`, `.claude/agents/architect.md`: the
  package name, and a `JAMAIS` entry for the SDK naming the rule.
- `config/m21-quality-ratchets.properties`: the count of architecture tests rises by the tests added; the ratchet may
  be raised in the lot, never lowered (`.claude/skills/coverage-ratchet`).
- No product code, no POM, no contract, no ADR: ADR-0090 and ADR-0028 already state the boundary; this change enforces
  it.
- Related: `give-the-shared-mcp-transport-its-own-package` (AUD-ARC-07) moves the transport out of
  `com.morpheus.integration..`; whichever lands second adds the transport's package to the SDK's forbidden targets.
