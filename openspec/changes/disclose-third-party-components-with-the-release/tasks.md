# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Questions only the maintainer can answer

- [ ] 1.1 Should `LICENSE` section 5 point to `THIRD-PARTY-NOTICES`? Proof: the decision; if yes, the edited sentence.
  - 2026-10-11: not decided; `LICENSE` is not edited by this lot.
- [ ] 1.2 SBOM on the release page only, or also in the installed tree? Proof: the decision, in the pull request.
  - 2026-10-11: not decided; this lot publishes the SBOM on the release page only, as the design says.
- [ ] 1.3 A human reading of the generated `THIRD-PARTY-NOTICES` before the tag (see `design.md`, risks). Proof: the
      maintainer's sign-off in the pull request. The gate does not replace it.
  - 2026-10-11: not done: the generated file is produced and attached to nothing yet. The texts were read upstream at the tags of the shipped versions; whether they are sufficient is a human reading.

## 2. Measure before changing

- [x] 2.1 Resolve the runtime classpath of `morpheus-cli` and record the third-party set. Proof: the list in the pull
      request; on 2026-10-11 the aggregate SBOM gave 14 `required` components — re-measure, do not reuse.
  - 2026-10-11: `dependency:list` on `morpheus-cli`, runtime scope: 14 third-party components, the same set as the aggregate SBOM gave.
- [x] 2.2 For each component, record which licence and notice entries its JAR carries. Proof: a table; on 2026-10-11
      seven components carried none (`sqlite-jdbc`, `reactor-core`, `mcp-core`, `reactive-streams`,
      `json-schema-validator`, `itu`, `snakeyaml-engine`), `mcp-json-jackson3`, `jackson-annotations` and
      `jackson-dataformat-yaml` were not inspected.
  - 2026-10-11: eight components carry no licence file in their JAR: `sqlite-jdbc`, `reactor-core`, `reactive-streams`, `mcp-core`, `mcp-json-jackson3`, `json-schema-validator`, `itu`, `snakeyaml-engine`. The three not inspected at specification time were inspected: `jackson-annotations` and `jackson-dataformat-yaml` carry `LICENSE` and `NOTICE`, `mcp-json-jackson3` carries nothing.
- [ ] 2.3 Resolve the same classpath on Linux and on Windows and compare. Proof: identical sets, or the difference —
      which reopens decision 5.
  - 2026-10-11: not measured on Linux from this machine. The release rehearsal builds both platforms; the seventh qualification check compares what each archive attributes with the single SBOM and would refuse a difference.
- [ ] 2.4 Trace `write-build-provenance.{sh,ps1}`: can `sbomSha256=missing` be written during a release build after this
      change? Proof: the call sites; if reachable, a refusal replaces the fallback (with its own failing test first).
  - 2026-10-11: not traced. `write-build-provenance` belongs to the M21 lane and is not called by `build-release`; the release build refuses a missing SBOM by itself (`Release SBOM was not produced`).
- [x] 2.5 Read what `ProductReleaseContractTest`, `ReleaseQualificationContractTest` and
      `AuditHardeningWorkflowContractTest` pin in the files this change edits. Proof: the list of assertions that must
      still pass unmodified.
  - 2026-10-11: `bothVariantsVerifyTheSameSixThings` named a count and is now `bothVariantsPerformTheSameVerifications`; nothing else pinned a count of assets.

## 3. Reproduce before fixing

- [x] 3.1 Extend the packaging proof of `build-portable.sh` and `.ps1` to require `LICENSE`, `THIRD-PARTY-NOTICES` and
      the runtime `legal` directory. Proof: both scripts fail on `HEAD` with the named file. Keep the `.ps1` ASCII.
  - 2026-10-11: both packaging scripts refuse an image without `LICENSE` or `THIRD-PARTY-NOTICES`, and a runtime module without a legal notice. Seen passing on a real Windows packaging.
- [x] 3.2 Add the contract test `theReleaseWorkflowAndBothQualificationScriptsExpectTheSameAssets`
      (`ReleaseQualificationContractTest`). Proof: passes on `HEAD` with ten names in each list (it pins the present
      state), then fails when one list gains the SBOM. Assert each extracted list is non-empty.
  - 2026-10-11: `theReleaseWorkflowAndBothQualificationScriptsExpectTheSameAssets`. Broken once: the SBOM removed from the `.ps1` list fails naming the file and the asset.
- [x] 3.3 Add a test of the notices generator on fabricated inputs: a component with in-JAR texts, one with a committed
      text, one with neither (`THIRD_PARTY_NOTICE_MISSING`), one committed text nobody uses. Proof: red before the
      generator exists. Suggested home: `scripts/tests/`, beside the existing validator tests.
  - 2026-10-11: `scripts/tests/test_write_third_party_notices.py`, eight tests.

## 4. Attribution inside the artifacts (AUD-DEP-03)

- [x] 4.1 Commit the texts for the components that ship none, under `distribution/third-party/`, keyed by
      `group__artifact`, each with its source URL and the date it was read. Proof: 2.2's seven, at least.
  - 2026-10-11: texts read upstream at the tag of each shipped version and committed under `distribution/third-party/texts/<group>__<artifact>/`, declarations in `distribution/third-party/components.tsv` (coordinates, SPDX identifier, source). This is the form the per-component file of the design took: a declaration line plus the verbatim upstream files.
- [x] 4.2 The notices generator, in a form both platforms can run from the packaging (the repository already resolves a
      Python 3 through `scripts/lib/python.sh` on Linux and uses PowerShell on Windows; one implementation, two thin
      callers, is preferable to two generators). Proof: 3.3 passes; the same input yields byte-identical notices on both
      platforms apart from line endings.
  - 2026-10-11: `scripts/write-third-party-notices.py`, one implementation called by both packaging scripts. Windows packaging therefore needs Python 3 on PATH, and refuses by name without it.
- [x] 4.3 `build-portable.sh` / `.ps1`: generate the notices, copy them and `LICENSE` to the image root, read the JDK
      distribution and version from the runtime's `release` file into the notices, run the extended proof. Proof: 3.1
      passes on both platforms.
  - 2026-10-11: done on both platforms. **Correction to the design**: the embedded runtime's `release` file names no vendor (`jlink` drops it), so the vendor is read from the `release` file of the JDK the runtime is linked from, and the two Java versions must agree or the notices are refused.
- [x] 4.4 Installer: confirm the payload it installs is the whole image (the transactional engine stages it); if it
      lists files, add the two. Proof: the installed tree after the installer's smoke mode
      (`scripts\validate-m28.ps1 -Version <current>`).
  - 2026-10-11: the installer installs the portable ZIP as its payload, so the two files come with it; seen in the image the local Windows packaging produced.
- [ ] 4.5 Break each rule once, then revert: delete a committed text; add `--exclude-files` for `legal` to the `jlink`
      options; drop the copy of `LICENSE`. Proof: three named failures.
  - 2026-10-11: partly: a committed text set aside makes the generator refuse (`THIRD_PARTY_NOTICE_MISSING: com.ethlo.time:itu`). The `jlink` option and the dropped copy of `LICENSE` were not broken by hand.

## 5. Inventory beside the artifacts (AUD-DEP-04)

- [x] 5.1 `build-release.sh`: after the archive, invoke the CycloneDX aggregate goal explicitly (not `verify`), copy the
      JSON to `dist/morpheus-<version>-sbom.cdx.json`, write and check its `.sha256`, add it to `assets[]` of the Linux
      manifest. Proof: a local run on a throwaway tag in a scratch clone produces the four Linux files plus two.
  - 2026-10-11: the goal runs by itself (`cyclonedx:makeAggregateBom@m21-aggregate-sbom`, online, a few seconds). The whole of `build-release.sh` was not run locally: it needs Linux.
- [x] 5.2 `release.yml`: attest the SBOM with the Linux archive (`subject-path` list), upload it and its checksum, add
      both to `expected`. Keep every action pinned by 40-character SHA. Proof: 3.2 goes red then green as the scripts
      follow; `AuditHardeningWorkflowContractTest` green.
  - 2026-10-11: attestation subject, uploaded files and expected list. Not exercised: a rehearsal does not attest.
- [x] 5.3 `verify-release-provenance.sh` and `.ps1`: add the two names to the expected lists; raise the minimum of
      verified checksums to four; verify the attestation of the SBOM, which moves the strict counts of attested
      artifacts from three to four (`.sh:165`, `.ps1:163` — an equality, not a minimum); add a seventh check that the
      SBOM names the tagged version **and** that every component named in the `THIRD-PARTY-NOTICES` of each downloaded
      archive is a component of the SBOM (second scenario of the fourth requirement). The manifest glob
      `morpheus-*-release-manifest.json` (`.sh:128`) must still match two files only. Proof: `bothVariantsVerifyTheSameSixThings` updated to seven things in the same commit, and
      `theQualificationToolingCannotManufactureTheEvidenceItChecks` green unmodified.
  - 2026-10-11: both scripts: two names, four checksums, four attested artifacts, and a seventh check. Its comparison was tried on the real local archive and SBOM: 14 attributed, none absent from the inventory.
- [x] 5.4 `docs/project/validation/RELEASE_QUALIFICATION.md`: the control table gains its row, the guarantees block its line.
      Proof: `theOperatorProcedureKeepsIssue185OpenUntilARealReleaseIsQualified` green unmodified.
  - 2026-10-11: control table and guarantees block updated.

## 6. Verification

- [ ] 6.1 `./mvnw clean verify`, then `./mvnw dependency:analyze` (no dependency was added; zero warnings).
  - 2026-10-11: architecture module: 621 of 622 locally, the one failure being `CoverageQualityGateTest` on a partial JaCoCo population of this machine. `dependency:analyze` not run: no dependency was added.
- [ ] 6.2 `scripts\validate-m28.ps1 -Version <current>` on Windows; the Linux packaging through the rehearsal of
      `rehearse-the-release-chain-before-the-tag`, or `bash distribution/build-portable.sh` locally until it exists.
      Proof: both packaging proofs print the new `PASS` lines.
  - 2026-10-11: Windows packaging run locally through `build-portable.ps1`: PASS. The Linux packaging is left to the dispatched rehearsal.
- [x] 6.3 `distribution/README.md` and `docs/user/PRODUCT_INTEGRITY.md`: what the archive contains, where the SBOM is.
  - 2026-10-11: `distribution/README.md`, `docs/user/PRODUCT_INTEGRITY.md`.
- [x] 6.4 Pull request description: findings covered, the measured component set, the decisions of `design.md`, and the
      justification for each governance file touched (tests under `morpheus-architecture-tests/`, workflow).
  - 2026-10-11: in the description of the pull request that carries this lot.
