# Design

## Context

- The distributed program is the shaded JAR of `morpheus-cli` (`morpheus-cli-<version>-all.jar`), placed in a `jpackage`
  application image with an embedded runtime (`--add-modules jdk.httpserver,java.sql,java.net.http`,
  `--jlink-options "--strip-debug --no-man-pages --no-header-files"`). The image is archived as `tar.gz` on Linux, `zip`
  on Windows, and wrapped by the Inno Setup installer.
- The aggregate SBOM covers the whole reactor, test scope excluded (`includeTestScope=false`), serial number omitted. It
  therefore also lists modules that are **not** distributed (`morpheus-provider-reference`, `morpheus-provider-testkit`,
  the two verification modules). It is the right inventory for the repository and a superset for the distribution.
- `actions/attest` attests the Linux archive in one job and the Windows ZIP and installer in the other; the `publish` job
  only downloads and publishes. Each build job runs on its own runner from the exact tagged SHA.
- `release.yml` has never run with its current content (`rehearse-the-release-chain-before-the-tag`). Anything added here
  is first exercised by that rehearsal, which is why AUD-DEP-03 and AUD-DEP-04 precede AUD-DEP-12.

## Goals / Non-Goals

**Goals**: a recipient of any of the three artifacts finds the product licence and the attribution of every third-party
component inside it; the release page carries a verifiable inventory of the tagged commit; neither can drift from what
is shipped without a failing build.

**Non-Goals**: Authenticode signing (AUD-DEP-05, sprint 14); VEX or vulnerability statements; changing the product
licence; an SBOM per platform; licence compatibility analysis — this change makes the attribution complete, it does not
audit whether each licence permits the use.

## Decisions

1. **The notices population is the runtime classpath of the distributed module, resolved by the build.** Not the
   aggregate SBOM (a superset), not `META-INF/maven/**/pom.properties` in the shaded JAR (measured: `reactor-core` and
   `reactive-streams` ship none, so the JAR under-reports), not a hand list (already eight short). The build resolves the
   runtime-scope dependencies of `morpheus-cli` and the packaging compares that set to the notices. *Rejected*: deriving
   from the aggregate SBOM — it would attribute components the archive does not contain.

2. **Texts come from the component first, from the repository second, and their absence is a refusal.** For each
   component: the licence and notice files its JAR carries are reproduced under its name; where the JAR carries none, a
   committed text under `distribution/third-party/` keyed by `group:artifact` supplies the copyright line and the licence
   text; where neither exists the packaging fails with `THIRD_PARTY_NOTICE_MISSING` and names the component. The
   committed file is keyed by coordinates without version, so a patch bump does not need an edit, while a new component
   does. *Rejected*: one text per SPDX id — MIT requires *the* copyright notice of each work, not a generic one.
   *Rejected*: shade's `ApacheLicenseResourceTransformer` / `ApacheNoticeResourceTransformer` alone — they merge inside
   the JAR, where no operator looks, and do nothing for the seven components that ship no text.

3. **Where the files go: the root of the application image**, beside the launcher, on both platforms; the installer
   installs them with the payload. A recipient who unpacks the archive sees them without opening a JAR.

4. **The runtime's notices are asserted, not copied.** `jlink` keeps `legal/<module>/` in the runtime. The packaging
   proof requires that directory to be present and non-empty for each packaged module, and `THIRD-PARTY-NOTICES` names
   the distribution and version of the JDK that produced the runtime, read from the build's `java.vendor.version` /
   `release` file rather than written by hand. *Rejected*: copying the JDK licence into the notices — it is already in
   the image, and a second copy can diverge.

5. **One SBOM per release, produced in the release build from the tagged commit.** The Linux build job produces the
   aggregate CycloneDX JSON, names it `morpheus-<version>-sbom.cdx.json`, writes its `.sha256`, lists it in the Linux
   release manifest, and attests it with the archive. The XML form is not published: one format, one digest. The build
   step is the existing CycloneDX execution, invoked explicitly — the release build must not start running the whole
   `verify` lifecycle (tests are skipped in a release build by design; M28 and CI have already run them on that commit).
   *Rejected*: publishing the SBOM of the CI run of that commit — another invocation, an artifact that expires in 14
   days, and no proof it describes the published bytes. *Rejected*: one SBOM per platform — the dependency set is the
   same (task 2.3 proves it rather than assuming it), and two inventories invite the question of which one is true.

6. **The expected-asset lists stay three, and a test holds them equal.** `release.yml` and the two qualification scripts
   each enumerate the assets on purpose: the qualification is an independent read, and a shared file would make it read
   the pipeline's own expectation. The cost is drift, found only at the tag. A contract test extracts the three lists
   and requires the same names; `ReleaseQualificationContractTest` is its home. This is textual by nature (`.yml`,
   `.sh`, `.ps1`, ADR-0103). *Rejected*: a single `release-assets.txt` read by all three — it removes the independence
   that the "unexpected asset" refusal exists to provide.

7. **The qualification verifies the SBOM like any other artifact.** The checksum loop already covers every `.sha256`;
   the minimum count rises from three to four; `gh attestation verify` covers four artifacts; a seventh check reads the
   SBOM and requires it to name the product version of the tag. `RELEASE_QUALIFICATION.md` documents it. The scripts
   stay read-only (`theQualificationToolingCannotManufactureTheEvidenceItChecks`).

8. **No new ADR; the decision already exists and this change delivers it.** ADR-0089
   (`0089-production-integrity-surface-convergence.md:37,64`) decides that "les releases disposent d'un SBOM et d'une
   provenance vérifiable", and ADR-0088 (`:72`) that every release asset has a companion SHA-256. The SBOM asset is the
   unfulfilled half of the first; its checksum applies the second. ADR-0027 / ADR-0061 govern the native distribution
   the notices are placed in. If the maintainer wants the asset list itself recorded, it is a dated amendment to
   ADR-0088, not a new record (review objection, accepted in this form).

## Risks / Trade-offs

- **Completeness is mechanical, correctness is legal.** The build proves every component has a text; it cannot prove the
  text is the right one, nor that `sqlite-jdbc`'s bundled native SQLite (public domain) and the notices of Jackson's
  embedded FastDoubleParser are sufficiently reproduced. A one-time human review of the generated file before the tag is
  a task, not something the gate replaces.
- **`write-build-provenance.{sh,ps1}` records `sbomSha256=missing`** when the SBOM is absent, and continues. It is a
  silent degradation in the M21 lane; whether it can reach a release build after this change is checked in task 2.4 and,
  if so, turned into a refusal.
- **Release size and time**: one more Maven goal in the Linux job; negligible against `jpackage`.
- **Two build jobs, one inventory**: if the Windows runner ever resolved a different dependency set, the single SBOM would
  be wrong for Windows. Task 2.3 measures it; the rehearsal can keep measuring it.
- Until the first real release, this is verified by the rehearsal only. A rehearsal does not attest and does not publish:
  the attestation of the SBOM and its presence on the release page are proven for the first time at the tag.

## Open Questions

- Should `LICENSE` section 5 name `THIRD-PARTY-NOTICES`? It reads better; it is the maintainer's text. Task 1.1.
- Is the SBOM expected in the installed tree as well (beside the notices), or only on the release page? This design says
  release page only. Task 1.2.
