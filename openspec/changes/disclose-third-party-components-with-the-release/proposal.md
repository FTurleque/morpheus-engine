# Proposal

## Why

AUD-DEP-03 and AUD-DEP-04 (sprint 2, both precede AUD-DEP-12) report that a release tells its recipient nothing about
the third-party components it carries: no attribution in the archives, no SBOM among the assets. Re-read on `develop` at
`9277a083` on 2026-10-11, both **hold**, and the evidence corrects the audit on three points that change the remedy:

- **Fourteen third-party components, not six.** `target/m21-supply-chain/morpheus-sbom.json` (CycloneDX 1.6, built
  2026-10-10) lists 14 components of scope `required`: the six the audit names, plus `mcp-json-jackson3`, `slf4j-api`,
  `jackson-annotations` 2.22, `jackson-core`, `jackson-dataformat-yaml`, `json-schema-validator`, `itu` and
  `snakeyaml-engine`. A hand-written list is already wrong on the day it is written; the population has to be derived.
- **The SBOM carries licence identifiers, not licence texts.** Each component has an SPDX id (`Apache-2.0`, `MIT`,
  `MIT-0`) and nothing else. Apache-2.0 §4 asks for a copy of the licence and the content of `NOTICE`; MIT asks for the
  copyright notice and the permission text. "Generate the notices from the SBOM", the audit's remedy, yields a table of
  names. The texts are in the dependency JARs for some components (`jackson-core`, `jackson-databind`, `slf4j-*`) and in
  none for seven of them (`sqlite-jdbc`, `reactor-core`, `mcp-core`, `reactive-streams`, `json-schema-validator`, `itu`,
  `snakeyaml-engine`: no `META-INF/*LICENSE*` or `*NOTICE*` entry).
- **The release build does not produce the SBOM.** `distribution/build-portable.sh:30` and `build-portable.ps1:124` run
  `mvnw -pl morpheus-cli -am -DskipTests package`; the CycloneDX execution is bound to `verify` on the root project
  (`pom.xml:237-254`). The only SBOM that exists is the one of a CI run, kept 14 days (`ci.yml:90-94`), built from
  another invocation than the artifact it would describe.

What still stands as written: the app-image root holds `app/`, `integration/`, `runtime/` and the launcher — no
`LICENSE`, no notices (`build-portable.sh:253` archives exactly that tree; the installer packages the same image).
Inside the uber-JAR the shade step leaves one `META-INF/LICENSE` and one `META-INF/NOTICE`, first writer wins among the
Jackson artifacts, attributed to nothing. `release.yml:217-228` lists ten expected assets, `verify-release-provenance.sh`
`:61-72` and `.ps1:71-80` list the same ten and refuse anything else, so publishing an SBOM today would make the
qualification fail. The embedded runtime does keep its `legal/` directory (seen in a local Windows image); nothing
asserts it and nothing names the JDK distribution, which `release.yml` fixes as Temurin 21.

A proprietary product that is strict about its own licence redistributes fourteen components without the texts their
licences require, and the inventory that would let an operator check their exposure to an announced CVE never leaves the
pipeline.

## What Changes

- Every distributed artifact carries, at the root of the application image, the product `LICENSE` and a
  `THIRD-PARTY-NOTICES` file covering each third-party component on the runtime classpath of the distributed program,
  with its version, its licence and the texts that licence requires. The population is derived at build time; a
  component without a text fails the packaging and is named.
- The packaging proof asserts those two files and the runtime's own legal notices; the notices name the JDK
  distribution the runtime comes from.
- The release publishes the SBOM of the tagged commit, with its checksum, covered by the provenance attestation and
  referenced by the release manifest.
- The three lists of expected assets (the release workflow and both qualification scripts) are held equal by a test, so
  that adding an asset to one cannot be discovered at the tag.

## Capabilities

### New Capabilities

- `release-component-disclosure`: what a MORPHEUS release discloses about the third-party components it redistributes —
  attribution inside each artifact, inventory beside them — and how both are kept equal to what is actually shipped.

### Modified Capabilities

(none)

## Impact

- `distribution/build-portable.{sh,ps1}`, `distribution/build-release.{sh,ps1}`, `distribution/windows/MORPHEUS.iss`
  (if the installer lists payload files), a committed directory of licence texts for components that ship none,
  `.github/workflows/release.yml`, `scripts/verify-release-provenance.{sh,ps1}`,
  `docs/validation/RELEASE_QUALIFICATION.md` (asset table), `distribution/README.md`, `LICENSE` section 5 (pointer).
- Tests: `ProductReleaseContractTest`, `ReleaseQualificationContractTest`, `AuditHardeningWorkflowContractTest` pin these
  files textually and are extended, not loosened.
- No CLI command, MCP tool or HTTP route moves. No dependency is added to the product.
- Legal review of the notices' content is outside the repository; see `design.md`, risks.
