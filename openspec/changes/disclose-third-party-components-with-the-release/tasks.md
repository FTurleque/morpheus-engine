# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Questions only the maintainer can answer

- [ ] 1.1 Should `LICENSE` section 5 point to `THIRD-PARTY-NOTICES`? Proof: the decision; if yes, the edited sentence.
- [ ] 1.2 SBOM on the release page only, or also in the installed tree? Proof: the decision, in the pull request.
- [ ] 1.3 A human reading of the generated `THIRD-PARTY-NOTICES` before the tag (see `design.md`, risks). Proof: the
      maintainer's sign-off in the pull request. The gate does not replace it.

## 2. Measure before changing

- [ ] 2.1 Resolve the runtime classpath of `morpheus-cli` and record the third-party set. Proof: the list in the pull
      request; on 2026-10-11 the aggregate SBOM gave 14 `required` components — re-measure, do not reuse.
- [ ] 2.2 For each component, record which licence and notice entries its JAR carries. Proof: a table; on 2026-10-11
      seven components carried none (`sqlite-jdbc`, `reactor-core`, `mcp-core`, `reactive-streams`,
      `json-schema-validator`, `itu`, `snakeyaml-engine`), `mcp-json-jackson3`, `jackson-annotations` and
      `jackson-dataformat-yaml` were not inspected.
- [ ] 2.3 Resolve the same classpath on Linux and on Windows and compare. Proof: identical sets, or the difference —
      which reopens decision 5.
- [ ] 2.4 Trace `write-build-provenance.{sh,ps1}`: can `sbomSha256=missing` be written during a release build after this
      change? Proof: the call sites; if reachable, a refusal replaces the fallback (with its own failing test first).
- [ ] 2.5 Read what `ProductReleaseContractTest`, `ReleaseQualificationContractTest` and
      `AuditHardeningWorkflowContractTest` pin in the files this change edits. Proof: the list of assertions that must
      still pass unmodified.

## 3. Reproduce before fixing

- [ ] 3.1 Extend the packaging proof of `build-portable.sh` and `.ps1` to require `LICENSE`, `THIRD-PARTY-NOTICES` and
      the runtime `legal` directory. Proof: both scripts fail on `HEAD` with the named file. Keep the `.ps1` ASCII.
- [ ] 3.2 Add the contract test `theReleaseWorkflowAndBothQualificationScriptsExpectTheSameAssets`
      (`ReleaseQualificationContractTest`). Proof: passes on `HEAD` with ten names in each list (it pins the present
      state), then fails when one list gains the SBOM. Assert each extracted list is non-empty.
- [ ] 3.3 Add a test of the notices generator on fabricated inputs: a component with in-JAR texts, one with a committed
      text, one with neither (`THIRD_PARTY_NOTICE_MISSING`), one committed text nobody uses. Proof: red before the
      generator exists. Suggested home: `scripts/tests/`, beside the existing validator tests.

## 4. Attribution inside the artifacts (AUD-DEP-03)

- [ ] 4.1 Commit the texts for the components that ship none, under `distribution/third-party/`, keyed by
      `group__artifact`, each with its source URL and the date it was read. Proof: 2.2's seven, at least.
- [ ] 4.2 The notices generator, in a form both platforms can run from the packaging (the repository already resolves a
      Python 3 through `scripts/lib/python.sh` on Linux and uses PowerShell on Windows; one implementation, two thin
      callers, is preferable to two generators). Proof: 3.3 passes; the same input yields byte-identical notices on both
      platforms apart from line endings.
- [ ] 4.3 `build-portable.sh` / `.ps1`: generate the notices, copy them and `LICENSE` to the image root, read the JDK
      distribution and version from the runtime's `release` file into the notices, run the extended proof. Proof: 3.1
      passes on both platforms.
- [ ] 4.4 Installer: confirm the payload it installs is the whole image (the transactional engine stages it); if it
      lists files, add the two. Proof: the installed tree after the installer's smoke mode
      (`scripts\validate-m28.ps1 -Version <current>`).
- [ ] 4.5 Break each rule once, then revert: delete a committed text; add `--exclude-files` for `legal` to the `jlink`
      options; drop the copy of `LICENSE`. Proof: three named failures.

## 5. Inventory beside the artifacts (AUD-DEP-04)

- [ ] 5.1 `build-release.sh`: after the archive, invoke the CycloneDX aggregate goal explicitly (not `verify`), copy the
      JSON to `dist/morpheus-<version>-sbom.cdx.json`, write and check its `.sha256`, add it to `assets[]` of the Linux
      manifest. Proof: a local run on a throwaway tag in a scratch clone produces the four Linux files plus two.
- [ ] 5.2 `release.yml`: attest the SBOM with the Linux archive (`subject-path` list), upload it and its checksum, add
      both to `expected`. Keep every action pinned by 40-character SHA. Proof: 3.2 goes red then green as the scripts
      follow; `AuditHardeningWorkflowContractTest` green.
- [ ] 5.3 `verify-release-provenance.sh` and `.ps1`: add the two names to the expected lists; raise the minimum of
      verified checksums to four; verify the attestation of the SBOM, which moves the strict counts of attested
      artifacts from three to four (`.sh:165`, `.ps1:163` — an equality, not a minimum); add a seventh check that the
      SBOM names the tagged version **and** that every component named in the `THIRD-PARTY-NOTICES` of each downloaded
      archive is a component of the SBOM (second scenario of the fourth requirement). The manifest glob
      `morpheus-*-release-manifest.json` (`.sh:128`) must still match two files only. Proof: `bothVariantsVerifyTheSameSixThings` updated to seven things in the same commit, and
      `theQualificationToolingCannotManufactureTheEvidenceItChecks` green unmodified.
- [ ] 5.4 `docs/project/validation/RELEASE_QUALIFICATION.md`: the control table gains its row, the guarantees block its line.
      Proof: `theOperatorProcedureKeepsIssue185OpenUntilARealReleaseIsQualified` green unmodified.

## 6. Verification

- [ ] 6.1 `./mvnw clean verify`, then `./mvnw dependency:analyze` (no dependency was added; zero warnings).
- [ ] 6.2 `scripts\validate-m28.ps1 -Version <current>` on Windows; the Linux packaging through the rehearsal of
      `rehearse-the-release-chain-before-the-tag`, or `bash distribution/build-portable.sh` locally until it exists.
      Proof: both packaging proofs print the new `PASS` lines.
- [ ] 6.3 `distribution/README.md` and `docs/user/PRODUCT_INTEGRITY.md`: what the archive contains, where the SBOM is.
- [ ] 6.4 Pull request description: findings covered, the measured component set, the decisions of `design.md`, and the
      justification for each governance file touched (tests under `morpheus-architecture-tests/`, workflow).
