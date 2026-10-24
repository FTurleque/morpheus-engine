# Refusals claimed by each validator (task 1.3)

Read on 2026-10-18 from `develop` at `29c942ae` (version 1.2.1). One row per `exit 1` / `raise SystemExit(` site of a
`validate-<target>.sh` and per `throw` / `exit 1` site of a `validate-<target>.ps1`; comment lines are skipped.
**545 sites** (184 before the build, 122 between the build and the packaging switch,
239 after it).

How to read it:

- `zone` is positional: `before build` is above the first `./mvnw clean verify` (or the PowerShell wrapper of it),
  `after build` is below it and above the first packaging switch (`SKIP_PORTABLE` / `-SkipPortable` / `build-portable`),
  `after packaging switch` is everything from that line on, including the final head-and-tree re-check.
  A `.ps1` function defined above the build and called below it is listed in the zone of its definition.
- `message` is the text the site writes, clipped at 110 characters; the site is the line number in the file at that commit.
- Which sites a test provokes: the scenarios of `scripts/tests/test_validator_verdicts.py` (VerdictContract,
  LivingMinimumsContract, aggregate coverage cases) and `scripts/tests/test_validator_entry_refusals.py` (r2/r3, reactor
  count) drive the sites of the first two zones on the fabricated checkout of `validator_harness.py`. **No test reaches
  a site of the `after packaging switch` zone**, apart from `m19`, `m20`, `m28` and `r3`, which the harness runs
  only as far as they need a product (`NOT_REACHED_BY_A_TEST` in the harness says what is out of reach per target).
  The real runs of task 1.4 are the only observation of that zone.
- The files each validator reads, in the order the validator reads them: `config/m21-quality-ratchets.properties`
  (through `lib/read-quality-ratchet.sh` or `lib/Read-QualityRatchets.ps1`), `pom.xml` and the module POMs,
  `**/target/surefire-reports/TEST-*.xml`, `morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt`
  (through `lib/require-aggregate-coverage-evidence.*`), `target/m21-supply-chain/morpheus-sbom.{json,xml}` and
  `build-provenance.properties`, `morpheus-provider-reference/target/morpheus-provider-reference-<version>.jar`,
  `morpheus-cli/target/morpheus-cli-*-all.jar`, the packaged launcher under `validation-output/<target>/`. The fabricated
  checkout of `validator_harness.py` holds exactly that set.

| target | variant | line | kind | zone | message |
|---|---|---|---|---|---|
| d2 | sh | 30 | exit 1 | before build | D2 exact-head gate requires no tracked workspace delta before validation |
| d2 | sh | 38 | exit 1 | before build | D2 base ref not found and fallback develop is unavailable |
| d2 | sh | 45 | exit 1 | before build | D2 is local-only and must not modify GitHub Actions workflows |
| d2 | sh | 61 | SystemExit | before build | D2 root POM declares no reactor modules') |
| d2 | sh | 72 | SystemExit | before build |  |
| d2 | sh | 78 | SystemExit | before build | MORPHEUS {version} version missing from {pom}') |
| d2 | sh | 91 | SystemExit | before build | D2 dependency/quality token missing from pom.xml: {token}') |
| d2 | sh | 118 | exit 1 | after build | D2 Surefire failures=$FAILURES errors=$ERRORS |
| d2 | sh | 122 | exit 1 | after build | D2 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| d2 | sh | 126 | exit 1 | after build | D2 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCHETS) |
| d2 | sh | 139 | SystemExit | after build | D2 line coverage below 0.40: {line}') |
| d2 | sh | 141 | SystemExit | after build | D2 branch coverage below 0.35: {branch}') |
| d2 | sh | 149 | exit 1 | after build | D2 CycloneDX aggregate SBOM JSON/XML missing after clean verify |
| d2 | sh | 159 | exit 1 | after build | D2 dependency-check JSON report missing: $SECURITY_REPORT |
| d2 | sh | 172 | exit 1 | after packaging switch | D2 packaged launcher missing: $LAUNCHER |
| d2 | sh | 180 | SystemExit | after packaging switch | D2 packaged version mismatch: {payload.get('version')} != {sys.argv[2]}") |
| d2 | sh | 189 | exit 1 | after packaging switch | HEAD changed during D2 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| d2 | sh | 194 | exit 1 | after packaging switch | Tracked workspace delta appeared during D2 validation |
| d2 | ps1 | 29 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| d2 | ps1 | 37 | throw | before build | D2 base ref not found: $Requested (fallback develop also missing) |
| d2 | ps1 | 46 | throw | before build | D2 root POM declares no reactor modules |
| d2 | ps1 | 51 | throw | before build | D2 root POM contains an empty module declaration |
| d2 | ps1 | 60 | throw | before build | Unexpected Maven reactor POM count: $($actualPomPaths.Count), expected $($expectedPomPaths.Count) from root... |
| d2 | ps1 | 64 | throw | before build | Expected Maven reactor POM missing or unexpected POM present: $expectedPom |
| d2 | ps1 | 70 | throw | before build | MORPHEUS $ExpectedVersion version missing from $($pom.FullName) |
| d2 | ps1 | 90 | throw | before build | D2 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| d2 | ps1 | 98 | throw | before build | D2 is local-only and must not modify GitHub Actions workflows |
| d2 | ps1 | 113 | throw | before build | D2 dependency/quality token missing from pom.xml: $token |
| d2 | ps1 | 140 | throw | after build | D2 Surefire failures=$failures errors=$errors |
| d2 | ps1 | 143 | throw | after build | D2 test baseline regression: $tests < $testsMinimum (testsMinimum in $ratchetsPath) |
| d2 | ps1 | 146 | throw | after build | D2 architecture baseline regression: $architectureTests < $architectureTestsMinimum (architectureTestsMinim... |
| d2 | ps1 | 155 | throw | after build | D2 line coverage below 0.40: $lineCoverage |
| d2 | ps1 | 156 | throw | after build | D2 branch coverage below 0.35: $branchCoverage |
| d2 | ps1 | 162 | throw | after build | D2 CycloneDX aggregate SBOM JSON/XML missing after clean verify |
| d2 | ps1 | 172 | throw | after build | D2 dependency-check JSON report missing: $securityReport |
| d2 | ps1 | 185 | throw | after packaging switch | D2 packaged launcher missing: $launcher |
| d2 | ps1 | 191 | throw | after packaging switch | D2 packaged version mismatch: $($productInfo.version) != $Version |
| d2 | ps1 | 198 | throw | after packaging switch | HEAD changed during D2 validation: $validationSha -> $currentSha |
| d2 | ps1 | 201 | throw | after packaging switch | Tracked workspace delta appeared during D2 validation:`n$($finalTracked -join "`n") |
| m19 | sh | 146 | SystemExit | before build | packaged launcher iteration {index} failed: {proc.returncode}") |
| m19 | sh | 155 | SystemExit | before build | packaged startup p95 {p95:.1f} ms exceeds frozen 5000 ms budget") |
| m19 | sh | 177 | exit 1 | before build | Exact-head validation requires a clean Git workspace |
| m19 | sh | 200 | exit 1 | before build | Reference environment requires at least 4 logical processors |
| m19 | sh | 204 | SystemExit | before build | Reference environment requires at least 8 GiB visible RAM") |
| m19 | sh | 207 | exit 1 | before build | Workspace filesystem is not local: $FILESYSTEM_TYPE |
| m19 | sh | 246 | exit 1 | after packaging switch | HEAD changed during validation: $VALIDATION_SHA -> $ENDING_SHA |
| m19 | sh | 247 | exit 1 | after packaging switch | Workspace changed during validation |
| m19 | ps1 | 89 | throw | before build | Stage '$Name' failed with exit code $exitCode |
| m19 | ps1 | 96 | throw | before build | Required tool is not available on PATH: $Name |
| m19 | ps1 | 153 | throw | before build | Reference environment requires at least 4 logical processors; found $logicalProcessors |
| m19 | ps1 | 154 | throw | before build | Reference environment requires at least 8 GiB visible RAM; found $visibleRamGiB |
| m19 | ps1 | 155 | throw | before build | Workspace drive must be local/fixed; found $($drive.DriveType) |
| m19 | ps1 | 157 | throw | before build | Workspace must be on a local SSD; media=$mediaType bus=$($disk.BusType) |
| m19 | ps1 | 163 | throw | before build | Cannot compute p95 from an empty sample set |
| m19 | ps1 | 178 | throw | before build | No Windows portable archive was produced in dist/ |
| m19 | ps1 | 186 | throw | after packaging switch | morpheus.exe was not found in the extracted portable archive |
| m19 | ps1 | 199 | throw | after packaging switch | Packaged launcher warmup failed |
| m19 | ps1 | 217 | throw | after packaging switch | Packaged launcher iteration $index failed with exit code $exitCode |
| m19 | ps1 | 231 | throw | after packaging switch |  |
| m19 | ps1 | 303 | throw | after packaging switch | Repository root does not contain .git: $repoRoot |
| m19 | ps1 | 306 | throw | after packaging switch | Cannot resolve git HEAD |
| m19 | ps1 | 315 | throw | after packaging switch | Exact-head validation requires a clean Git workspace |
| m19 | ps1 | 327 | throw | after packaging switch | java -version failed |
| m19 | ps1 | 328 | throw | after packaging switch | Maven Wrapper --version failed |
| m19 | ps1 | 378 | throw | after packaging switch | HEAD changed during validation: $script:ValidationSha -> $endingSha |
| m19 | ps1 | 379 | throw | after packaging switch | Tracked or untracked workspace state changed during validation |
| m19 | ps1 | 391 | exit 1 | after packaging switch |  |
| m20 | sh | 62 | exit 1 | before build |  |
| m20 | ps1 | 42 | throw | before build | Required tool is not available on PATH: $Name |
| m20 | ps1 | 86 | throw | before build | Stage '$Name' failed with exit code $exitCode |
| m20 | ps1 | 128 | throw | before build | Missing release asset: $AssetPath |
| m20 | ps1 | 130 | throw | before build | Missing checksum: $checksumPath |
| m20 | ps1 | 133 | throw | before build | SHA-256 mismatch for $AssetPath recorded=$recorded actual=$actual |
| m20 | ps1 | 147 | throw | before build | Installer contract missing: $token |
| m20 | ps1 | 150 | throw | before build | Installer must not delete persistent MORPHEUS state during uninstall |
| m20 | ps1 | 159 | throw | before build | Setup failed with exit code $($process.ExitCode) |
| m20 | ps1 | 164 | throw | before build | Uninstaller not found under $Target |
| m20 | ps1 | 167 | throw | before build | Uninstall failed with exit code $($process.ExitCode) |
| m20 | ps1 | 192 | throw | before build | Installed no-JDK version smoke failed: $versionText |
| m20 | ps1 | 195 | throw | before build | Installed version is $($versionView.version); expected $Version |
| m20 | ps1 | 199 | throw | before build | Installed paths smoke failed: $pathsText |
| m20 | ps1 | 205 | throw | before build | Unexpected PROD data path: $($paths.dataDirectory) expected=$expectedData |
| m20 | ps1 | 208 | throw | before build | Unexpected PROD config path: $($paths.configDirectory) expected=$expectedConfig |
| m20 | ps1 | 211 | throw | before build | Unexpected PROD logs path: $($paths.logsDirectory) expected=$expectedLogs |
| m20 | ps1 | 215 | throw | before build | Installed MINOS status failed: $minosText |
| m20 | ps1 | 217 | throw | before build | MINOS must be opt-in: $minosText |
| m20 | ps1 | 220 | throw | before build | Installed NEXUS status failed: $nexusText |
| m20 | ps1 | 222 | throw | before build | NEXUS must be opt-in: $nexusText |
| m20 | ps1 | 225 | throw | before build | Installed SQLite creation smoke failed: $projects |
| m20 | ps1 | 255 | throw | before build | Installed API exited before becoming ready: $diagnostic |
| m20 | ps1 | 264 | throw | before build | Installed API health/readiness/metrics timed out |
| m20 | ps1 | 330 | throw | before build | Cannot resolve Git HEAD |
| m20 | ps1 | 332 | throw | before build | Exact-head validation requires a clean Git workspace |
| m20 | ps1 | 335 | throw | before build | pom.xml version is $projectVersion; expected $Version |
| m20 | ps1 | 349 | throw | before build | Full Maven reactor contains failures/errors |
| m20 | ps1 | 356 | throw | before build |  |
| m20 | ps1 | 368 | throw | before build | Unable to inspect local validation tags |
| m20 | ps1 | 371 | throw | before build | Unable to delete stale local validation tag $($script:ValidationTag) |
| m20 | ps1 | 374 | throw | before build | Unable to create local validation tag |
| m20 | ps1 | 390 | throw | before build | Release manifest version/tag/SHA does not match validation head |
| m20 | ps1 | 393 | throw | before build | Release manifest runtime/data invariants are invalid |
| m20 | ps1 | 403 | throw | after packaging switch | Installed launcher missing: $launcher |
| m20 | ps1 | 404 | throw | after packaging switch | PATH option did not add the install directory to the user PATH |
| m20 | ps1 | 410 | throw | after packaging switch | Installed runtime did not create persistent database: $database |
| m20 | ps1 | 416 | throw | after packaging switch | Upgrade replaced or deleted persistent state |
| m20 | ps1 | 424 | throw | after packaging switch | Uninstall deleted persistent MORPHEUS state |
| m20 | ps1 | 426 | throw | after packaging switch | Uninstall left the MORPHEUS install directory in the user PATH |
| m20 | ps1 | 434 | throw | after packaging switch | Reinstall did not preserve existing data/config |
| m20 | ps1 | 442 | throw | after packaging switch | HEAD changed during validation: $script:ValidationSha -> $endingSha |
| m20 | ps1 | 443 | throw | after packaging switch | Tracked or untracked workspace state changed during validation |
| m20 | ps1 | 454 | exit 1 | after packaging switch |  |
| m21 | sh | 34 | exit 1 | before build | M21 exact-head gate requires no tracked workspace delta before validation |
| m21 | sh | 41 | exit 1 | before build | M21 base ref not found: ${MORPHEUS_M21_BASE_REF:-origin/main} (fallback main also missing) |
| m21 | sh | 68 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m21 | sh | 72 | exit 1 | after build | M21 test baseline regression: $TESTS < $TESTS_MINIMUM |
| m21 | sh | 76 | exit 1 | after build | M21 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM |
| m21 | sh | 89 | SystemExit | after build | M21 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m21 | sh | 91 | SystemExit | after build | M21 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m21 | sh | 99 | exit 1 | after build | M21 CycloneDX JSON/XML SBOM is missing |
| m21 | sh | 105 | exit 1 | after packaging switch | M21 build provenance is missing |
| m21 | sh | 114 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m21 | sh | 123 | SystemExit | after packaging switch | packaged product metadata mismatch: {payload!r}') |
| m21 | sh | 139 | SystemExit | after packaging switch | same-version manifest must not report an update: {payload!r}') |
| m21 | sh | 167 | exit 1 | after packaging switch | Packaged API exited before version check |
| m21 | sh | 187 | exit 1 | after packaging switch | Packaged API version check timed out |
| m21 | sh | 197 | exit 1 | after packaging switch | HEAD changed during M21 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m21 | sh | 202 | exit 1 | after packaging switch | Tracked workspace delta appeared during M21 validation |
| m21 | ps1 | 29 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m21 | ps1 | 94 | throw | before build | Packaged API exited before version check: $diagnostic |
| m21 | ps1 | 102 | throw | before build | Packaged API version check timed out |
| m21 | ps1 | 120 | throw | before build | HTTP product version is $actualVersion; expected $Version |
| m21 | ps1 | 139 | throw | before build | M21 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m21 | ps1 | 147 | throw | before build | M21 base ref not found: origin/main (fallback main also missing) |
| m21 | ps1 | 156 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m21 | ps1 | 159 | throw | after build | M21 test baseline regression: $($totals.Tests) < $($ratchets.Tests) |
| m21 | ps1 | 163 | throw | after build | M21 architecture baseline regression: $($architecture.Tests) < $($ratchets.ArchitectureTests) |
| m21 | ps1 | 177 | throw | after build | M21 line coverage below $($ratchets.LineCoverageText) ratchet: $($coverage.lineRatio) |
| m21 | ps1 | 180 | throw | after build | M21 branch coverage below $($ratchets.BranchCoverageText) ratchet: $($coverage.branchRatio) |
| m21 | ps1 | 187 | throw | after build | M21 CycloneDX JSON/XML SBOM is missing |
| m21 | ps1 | 190 | throw | after build | M21 provenance writer failed with exit code $LASTEXITCODE |
| m21 | ps1 | 192 | throw | after build | M21 build provenance is missing |
| m21 | ps1 | 197 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m21 | ps1 | 199 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m21 | ps1 | 202 | throw | after packaging switch | Packaged product-info smoke failed: $productInfoText |
| m21 | ps1 | 205 | throw | after packaging switch | Packaged product metadata mismatch: $productInfoText |
| m21 | ps1 | 216 | throw | after packaging switch | Packaged update-check smoke failed: $updateText |
| m21 | ps1 | 218 | throw | after packaging switch | Same-version manifest must not report an update: $updateText |
| m21 | ps1 | 224 | throw | after packaging switch | HEAD changed during M21 validation: $validationSha -> $currentSha |
| m21 | ps1 | 227 | throw | after packaging switch | Tracked workspace delta appeared during M21 validation:`n$($finalTracked -join "`n") |
| m22 | sh | 30 | exit 1 | before build | M22 exact-head gate requires no tracked workspace delta before validation |
| m22 | sh | 37 | exit 1 | before build | M22 base ref not found: ${MORPHEUS_M22_BASE_REF:-origin/main} (fallback main also missing) |
| m22 | sh | 64 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m22 | sh | 68 | exit 1 | after build | M22 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m22 | sh | 72 | exit 1 | after build | M22 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m22 | sh | 85 | SystemExit | after build | M22 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m22 | sh | 87 | SystemExit | after build | M22 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m22 | sh | 95 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m22 | sh | 100 | exit 1 | after build | Build provenance is missing |
| m22 | sh | 107 | exit 1 | after build | M22 reference provider JAR missing after reactor verify: $REFERENCE_JAR |
| m22 | sh | 115 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m22 | sh | 121 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m22 | sh | 127 | exit 1 | after packaging switch | M22 packaged runtime is missing ProviderPluginService |
| m22 | sh | 131 | exit 1 | after packaging switch | M22 reference provider must remain external and must not be embedded in the MORPHEUS launcher |
| m22 | sh | 195 | exit 1 | after packaging switch | Packaged API exited before M22 check |
| m22 | sh | 220 | exit 1 | after packaging switch | Packaged API M22 check timed out |
| m22 | sh | 230 | exit 1 | after packaging switch | HEAD changed during M22 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m22 | sh | 235 | exit 1 | after packaging switch | Tracked workspace delta appeared during M22 validation |
| m22 | ps1 | 36 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m22 | ps1 | 81 | throw | before build | Packaged API exited before M22 checks: $diagnostic |
| m22 | ps1 | 100 | throw | before build | Packaged API M22 check timed out. stderr=$diagnostic |
| m22 | ps1 | 112 | throw | before build | M22 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m22 | ps1 | 120 | throw | before build | M22 base ref not found: origin/main (fallback main also missing) |
| m22 | ps1 | 129 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m22 | ps1 | 132 | throw | after build | M22 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m22 | ps1 | 136 | throw | after build | M22 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m22 | ps1 | 150 | throw | after build | M22 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m22 | ps1 | 153 | throw | after build | M22 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m22 | ps1 | 160 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m22 | ps1 | 163 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m22 | ps1 | 165 | throw | after build | Build provenance is missing |
| m22 | ps1 | 169 | throw | after build | M22 reference provider JAR missing after reactor verify: $referenceJar |
| m22 | ps1 | 173 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m22 | ps1 | 175 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m22 | ps1 | 178 | throw | after packaging switch | jar.exe not found under JAVA_HOME=$env:JAVA_HOME |
| m22 | ps1 | 181 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| m22 | ps1 | 183 | throw | after packaging switch | Unable to inspect shaded MORPHEUS JAR |
| m22 | ps1 | 185 | throw | after packaging switch | M22 packaged runtime is missing ProviderPluginService |
| m22 | ps1 | 188 | throw | after packaging switch | M22 reference provider must remain external and must not be embedded in the MORPHEUS launcher |
| m22 | ps1 | 200 | throw | after packaging switch | Packaged provider discovery failed: $discoveryText |
| m22 | ps1 | 203 | throw | after packaging switch | Packaged external provider discovery mismatch: $discoveryText |
| m22 | ps1 | 211 | throw | after packaging switch | Packaged external provider probe failed: $probeText |
| m22 | ps1 | 214 | throw | after packaging switch | Packaged external provider probe mismatch: $probeText |
| m22 | ps1 | 223 | throw | after packaging switch | HEAD changed during M22 validation: $validationSha -> $currentSha |
| m22 | ps1 | 226 | throw | after packaging switch | Tracked workspace delta appeared during M22 validation:`n$($finalTracked -join "`n") |
| m23 | sh | 30 | exit 1 | before build | M23 exact-head gate requires no tracked workspace delta before validation |
| m23 | sh | 35 | exit 1 | before build | M23 base ref not found: origin/main (fallback main also missing) |
| m23 | sh | 59 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m23 | sh | 63 | exit 1 | after build | M23 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m23 | sh | 67 | exit 1 | after build | M23 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m23 | sh | 80 | SystemExit | after build | M23 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m23 | sh | 82 | SystemExit | after build | M23 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m23 | sh | 87 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m23 | sh | 90 | exit 1 | after build | Build provenance is missing |
| m23 | sh | 96 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m23 | sh | 99 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m23 | sh | 110 | exit 1 | after packaging switch | M23 packaged runtime is missing $entry |
| m23 | sh | 113 | exit 1 | after packaging switch | Packaged M23 CLI help smoke failed |
| m23 | sh | 130 | SystemExit | after packaging switch | UUIDv7 not found: {text}') |
| m23 | sh | 137 | exit 1 | after packaging switch | Packaged portfolio overview mismatch: $OVERVIEW |
| m23 | sh | 154 | exit 1 | after packaging switch |  |
| m23 | sh | 165 | exit 1 | after packaging switch | Packaged API M23 portfolio check timed out |
| m23 | sh | 171 | exit 1 | after packaging switch | HEAD changed during M23 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m23 | sh | 175 | exit 1 | after packaging switch | Tracked workspace delta appeared during M23 validation |
| m23 | ps1 | 35 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m23 | ps1 | 63 | throw | before build | UUIDv7 not found in payload: $Text |
| m23 | ps1 | 75 | throw | before build | Packaged portfolio create failed: $createdText |
| m23 | ps1 | 82 | throw | before build | Packaged portfolio project registration failed: $registeredText |
| m23 | ps1 | 85 | throw | before build | Packaged portfolio overview mismatch: $overviewText |
| m23 | ps1 | 99 | throw | before build | Packaged API exited before M23 portfolio checks: $diagnostic |
| m23 | ps1 | 109 | throw | before build | Packaged API M23 portfolio check timed out |
| m23 | ps1 | 121 | throw | before build | M23 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m23 | ps1 | 128 | throw | before build | M23 base ref not found: origin/main (fallback main also missing) |
| m23 | ps1 | 136 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m23 | ps1 | 139 | throw | after build | M23 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m23 | ps1 | 143 | throw | after build | M23 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m23 | ps1 | 157 | throw | after build | M23 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m23 | ps1 | 160 | throw | after build | M23 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m23 | ps1 | 166 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m23 | ps1 | 168 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m23 | ps1 | 170 | throw | after build | Build provenance is missing |
| m23 | ps1 | 176 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m23 | ps1 | 178 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m23 | ps1 | 192 | throw | after packaging switch | M23 packaged runtime is missing $entry |
| m23 | ps1 | 195 | throw | after packaging switch | Packaged M23 CLI help smoke failed: $help |
| m23 | ps1 | 202 | throw | after packaging switch | HEAD changed during M23 validation: $validationSha -> $currentSha |
| m23 | ps1 | 205 | throw | after packaging switch | Tracked workspace delta appeared during M23 validation:`n$($finalTracked -join "`n") |
| m24 | sh | 30 | exit 1 | before build | M24 exact-head gate requires no tracked workspace delta before validation |
| m24 | sh | 35 | exit 1 | before build | M24 base ref not found: origin/main (fallback main also missing) |
| m24 | sh | 59 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m24 | sh | 63 | exit 1 | after build | M24 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m24 | sh | 67 | exit 1 | after build | M24 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m24 | sh | 80 | SystemExit | after build | M24 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m24 | sh | 82 | SystemExit | after build | M24 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m24 | sh | 87 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m24 | sh | 90 | exit 1 | after build | Build provenance is missing |
| m24 | sh | 96 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m24 | sh | 99 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m24 | sh | 113 | exit 1 | after packaging switch | M24 packaged runtime is missing $entry |
| m24 | sh | 116 | exit 1 | after packaging switch | Packaged M24 CLI help smoke failed |
| m24 | sh | 128 | exit 1 | after packaging switch | Packaged query mismatch: $QUERY |
| m24 | sh | 130 | exit 1 | after packaging switch | Packaged query page budget unexpectedly accepted limit 501 |
| m24 | sh | 132 | exit 1 | after packaging switch | Packaged query page budget unexpectedly accepted limit 501 |
| m24 | sh | 139 | SystemExit | after packaging switch | UUIDv7 not found: {sys.argv[1]}') |
| m24 | sh | 144 | exit 1 | after packaging switch | Saved-view revision did not advance: $UPDATED |
| m24 | sh | 146 | exit 1 | after packaging switch | Stale saved-view CAS unexpectedly succeeded |
| m24 | sh | 148 | exit 1 | after packaging switch | Stale saved-view CAS unexpectedly succeeded |
| m24 | sh | 150 | exit 1 | after packaging switch | Saved-view versions mismatch: $VERSIONS |
| m24 | sh | 156 | exit 1 | after packaging switch | Canonical JSON export mismatch: $JSON_EXPORT |
| m24 | sh | 157 | exit 1 | after packaging switch | CSV export mismatch: $CSV_EXPORT |
| m24 | sh | 158 | exit 1 | after packaging switch | Markdown export mismatch: $MD_EXPORT |
| m24 | sh | 160 | exit 1 | after packaging switch | Export mutated saved-view revision |
| m24 | sh | 177 | exit 1 | after packaging switch |  |
| m24 | sh | 198 | exit 1 | after packaging switch | Packaged API M24 query/saved-view check timed out |
| m24 | sh | 204 | exit 1 | after packaging switch | HEAD changed during M24 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m24 | sh | 208 | exit 1 | after packaging switch | Tracked workspace delta appeared during M24 validation |
| m24 | ps1 | 35 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m24 | ps1 | 63 | throw | before build | UUIDv7 not found in payload: $Text |
| m24 | ps1 | 69 | throw | before build | Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text |
| m24 | ps1 | 86 | throw | before build | Packaged query mismatch: $queryText |
| m24 | ps1 | 94 | throw | before build | Packaged query page budget unexpectedly accepted limit 501 |
| m24 | ps1 | 97 | throw | before build | Packaged page-budget diagnostic mismatch: $budgetError |
| m24 | ps1 | 110 | throw | before build | Saved-view revision did not advance: $updatedText |
| m24 | ps1 | 117 | throw | before build | Stale saved-view CAS unexpectedly succeeded |
| m24 | ps1 | 119 | throw | before build | Saved-view stale CAS diagnostic mismatch: $staleError |
| m24 | ps1 | 123 | throw | before build | Saved-view versions mismatch: $versionsText |
| m24 | ps1 | 131 | throw | before build | Canonical JSON export mismatch: $jsonExport |
| m24 | ps1 | 134 | throw | before build | CSV export mismatch: $csvExport |
| m24 | ps1 | 137 | throw | before build | Markdown export mismatch: $markdownExport |
| m24 | ps1 | 140 | throw | before build | Export mutated saved-view revision |
| m24 | ps1 | 154 | throw | before build | Packaged API exited before M24 checks: $diagnostic |
| m24 | ps1 | 171 | throw | before build | Packaged API M24 query/saved-view check timed out |
| m24 | ps1 | 184 | throw | before build | M24 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m24 | ps1 | 191 | throw | before build | M24 base ref not found: origin/main (fallback main also missing) |
| m24 | ps1 | 199 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m24 | ps1 | 202 | throw | after build | M24 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m24 | ps1 | 206 | throw | after build | M24 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m24 | ps1 | 220 | throw | after build | M24 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m24 | ps1 | 223 | throw | after build | M24 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m24 | ps1 | 229 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m24 | ps1 | 231 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m24 | ps1 | 233 | throw | after build | Build provenance is missing |
| m24 | ps1 | 239 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m24 | ps1 | 241 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m24 | ps1 | 244 | throw | after packaging switch | jar tool not found under JAVA_HOME=$env:JAVA_HOME |
| m24 | ps1 | 247 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| m24 | ps1 | 249 | throw | after packaging switch | jar tf failed with exit code $LASTEXITCODE |
| m24 | ps1 | 261 | throw | after packaging switch | M24 packaged runtime is missing $entry |
| m24 | ps1 | 265 | throw | after packaging switch | Packaged M24 CLI help smoke failed: $help |
| m24 | ps1 | 272 | throw | after packaging switch | HEAD changed during M24 validation: $validationSha -> $currentSha |
| m24 | ps1 | 275 | throw | after packaging switch | Tracked workspace delta appeared during M24 validation:`n$($finalTracked -join "`n") |
| m25 | sh | 42 | exit 1 | before build | M25 exact-head gate requires no tracked workspace delta before validation |
| m25 | sh | 47 | exit 1 | before build | M25 base ref not found: origin/develop (fallback develop also missing) |
| m25 | sh | 66 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m25 | sh | 69 | exit 1 | after build | M25 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m25 | sh | 73 | exit 1 | after build | M25 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m25 | sh | 86 | SystemExit | after build | M25 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m25 | sh | 88 | SystemExit | after build | M25 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m25 | sh | 92 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m25 | sh | 94 | exit 1 | after build | Build provenance is missing |
| m25 | sh | 100 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m25 | sh | 102 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m25 | sh | 115 | exit 1 | after packaging switch | M25 packaged runtime is missing $entry |
| m25 | sh | 118 | exit 1 | after packaging switch | Packaged M25 CLI help smoke failed |
| m25 | sh | 134 | SystemExit | after packaging switch | pack UUIDv7 not found') |
| m25 | sh | 142 | SystemExit | after packaging switch | version/rule UUIDv7 not found') |
| m25 | sh | 147 | exit 1 | after packaging switch | Policy revision did not advance: $UPDATED |
| m25 | sh | 149 | exit 1 | after packaging switch | Stale policy CAS unexpectedly succeeded |
| m25 | sh | 151 | exit 1 | after packaging switch | Stale policy CAS unexpectedly succeeded |
| m25 | sh | 154 | exit 1 | after packaging switch | Policy dry-run exit $DRY_EXIT, expected 4 (UNKNOWN is a refusal): $DRY |
| m25 | sh | 155 | exit 1 | after packaging switch | Policy dry-run mismatch: $DRY |
| m25 | sh | 157 | exit 1 | after packaging switch | Dry-run unexpectedly wrote activation audit |
| m25 | sh | 162 | exit 1 | after packaging switch | Policy evaluate exit $EVAL_EXIT, expected 4 (BLOCK is a refusal): $EVALUATED |
| m25 | sh | 163 | exit 1 | after packaging switch | Policy override provenance mismatch: $EVALUATED |
| m25 | sh | 179 | exit 1 | after packaging switch |  |
| m25 | sh | 190 | exit 1 | after packaging switch | Packaged API M25 policy route check timed out |
| m25 | sh | 196 | exit 1 | after packaging switch | HEAD changed during M25 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m25 | sh | 197 | exit 1 | after packaging switch | Tracked workspace delta appeared during M25 validation |
| m25 | ps1 | 31 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m25 | ps1 | 59 | throw | before build | Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text |
| m25 | ps1 | 66 | throw | before build | Packaged launcher exited $LASTEXITCODE, expected $ExpectedExit : $($Arguments -join ' ')`n$text |
| m25 | ps1 | 82 | throw | before build | Policy pack ID not found: $created |
| m25 | ps1 | 87 | throw | before build | Policy version/rule IDs not found: $versions |
| m25 | ps1 | 94 | throw | before build | Policy revision did not advance: $updated |
| m25 | ps1 | 109 | throw | before build | Stale policy CAS unexpectedly succeeded |
| m25 | ps1 | 111 | throw | before build | Stale policy diagnostic mismatch: $staleError |
| m25 | ps1 | 115 | throw | before build | Policy dry-run mismatch: $dry |
| m25 | ps1 | 117 | throw | before build | Dry-run unexpectedly wrote activation audit |
| m25 | ps1 | 126 | throw | before build | Policy override provenance mismatch: $evaluated |
| m25 | ps1 | 139 | throw | before build | Packaged API exited before M25 check: $diagnostic |
| m25 | ps1 | 146 | throw | before build | Packaged API M25 policy route check timed out |
| m25 | ps1 | 158 | throw | before build | M25 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m25 | ps1 | 163 | throw | before build | M25 base ref not found: origin/develop (fallback develop also missing) |
| m25 | ps1 | 170 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m25 | ps1 | 172 | throw | after build | M25 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m25 | ps1 | 176 | throw | after build | M25 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m25 | ps1 | 187 | throw | after build | M25 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m25 | ps1 | 190 | throw | after build | M25 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m25 | ps1 | 195 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m25 | ps1 | 197 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m25 | ps1 | 198 | throw | after build | Build provenance is missing |
| m25 | ps1 | 203 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m25 | ps1 | 205 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m25 | ps1 | 207 | throw | after packaging switch | jar tool not found under JAVA_HOME=$env:JAVA_HOME |
| m25 | ps1 | 209 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| m25 | ps1 | 221 | throw | after packaging switch | M25 packaged runtime is missing $entry |
| m25 | ps1 | 224 | throw | after packaging switch | Packaged M25 CLI help smoke failed: $help |
| m25 | ps1 | 230 | throw | after packaging switch | HEAD changed during M25 validation: $validationSha -> $currentSha |
| m25 | ps1 | 232 | throw | after packaging switch | Tracked workspace delta appeared during M25 validation:`n$($finalTracked -join "`n") |
| m26 | sh | 34 | SystemExit | before build | No SQLite migrations found in {migration_dir}') |
| m26 | sh | 38 | exit 1 | before build | Invalid expected SQLite schema: $EXPECTED_SCHEMA |
| m26 | sh | 57 | exit 1 | before build | M26 exact-head gate requires no tracked workspace delta before validation |
| m26 | sh | 62 | exit 1 | before build | M26 base ref not found: origin/develop (fallback develop also missing) |
| m26 | sh | 81 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m26 | sh | 84 | exit 1 | after build | M26 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m26 | sh | 88 | exit 1 | after build | M26 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m26 | sh | 101 | SystemExit | after build | M26 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m26 | sh | 103 | SystemExit | after build | M26 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m26 | sh | 107 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m26 | sh | 109 | exit 1 | after build | Build provenance is missing |
| m26 | sh | 115 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m26 | sh | 117 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m26 | sh | 127 | exit 1 | after packaging switch | M26 packaged runtime is missing $entry |
| m26 | sh | 130 | exit 1 | after packaging switch | Packaged M26 CLI help smoke failed |
| m26 | sh | 148 | exit 1 | after packaging switch | M26 identity result mismatch: $IDENTITY |
| m26 | sh | 150 | exit 1 | after packaging switch | M26 remote auth file missing: $AUTH_FILE |
| m26 | sh | 151 | exit 1 | after packaging switch | Plaintext bearer token leaked into persisted auth file |
| m26 | sh | 152 | exit 1 | after packaging switch | Plaintext bearer token leaked into persisted auth file |
| m26 | sh | 161 | exit 1 | after packaging switch | M26 backup result mismatch: $BACKUP |
| m26 | sh | 169 | exit 1 | after packaging switch | Unconfirmed M26 restore unexpectedly succeeded |
| m26 | sh | 171 | exit 1 | after packaging switch | Unconfirmed M26 restore unexpectedly succeeded |
| m26 | sh | 181 | exit 1 | after packaging switch | Local non-loopback API unexpectedly started |
| m26 | sh | 185 | exit 1 | after packaging switch | Local non-loopback API unexpectedly started |
| m26 | sh | 187 | exit 1 | after packaging switch | Remote API without TLS unexpectedly started |
| m26 | sh | 189 | exit 1 | after packaging switch | Remote API without TLS unexpectedly started |
| m26 | sh | 194 | exit 1 | after packaging switch | HEAD changed during M26 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m26 | sh | 195 | exit 1 | after packaging switch | Tracked workspace delta appeared during M26 validation |
| m26 | ps1 | 34 | throw | before build | No valid SQLite migrations found in $migrationDir |
| m26 | ps1 | 41 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m26 | ps1 | 60 | throw | before build | Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text |
| m26 | ps1 | 75 | throw | before build | $Name unexpectedly succeeded |
| m26 | ps1 | 80 | throw | before build | $Name diagnostic mismatch: $diagnostic |
| m26 | ps1 | 94 | throw | before build | Generated M26 bearer token missing: $identityJson |
| m26 | ps1 | 95 | throw | before build | Token persistence contract mismatch: $identityJson |
| m26 | ps1 | 97 | throw | before build | M26 remote auth file missing: $authFile |
| m26 | ps1 | 99 | throw | before build | Plaintext bearer token leaked into persisted auth file |
| m26 | ps1 | 100 | throw | before build | Hashed auth entry missing: $authText |
| m26 | ps1 | 105 | throw | before build | M26 backup contract mismatch: $backupJson |
| m26 | ps1 | 106 | throw | before build | M26 backup file missing: $($backup.path) |
| m26 | ps1 | 110 | throw | before build | M26 backup verification mismatch: $verifyJson |
| m26 | ps1 | 115 | throw | before build | M26 offline restore mismatch: $restoredJson |
| m26 | ps1 | 130 | throw | before build | M26 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m26 | ps1 | 135 | throw | before build | M26 base ref not found: origin/develop (fallback develop also missing) |
| m26 | ps1 | 142 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m26 | ps1 | 144 | throw | after build | M26 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m26 | ps1 | 148 | throw | after build | M26 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m26 | ps1 | 159 | throw | after build | M26 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m26 | ps1 | 162 | throw | after build | M26 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m26 | ps1 | 167 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m26 | ps1 | 169 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m26 | ps1 | 170 | throw | after build | Build provenance is missing |
| m26 | ps1 | 175 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m26 | ps1 | 177 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m26 | ps1 | 179 | throw | after packaging switch | jar tool not found under JAVA_HOME=$env:JAVA_HOME |
| m26 | ps1 | 181 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| m26 | ps1 | 190 | throw | after packaging switch | M26 packaged runtime is missing $entry |
| m26 | ps1 | 193 | throw | after packaging switch | Packaged M26 CLI help smoke failed: $help |
| m26 | ps1 | 199 | throw | after packaging switch | HEAD changed during M26 validation: $validationSha -> $currentSha |
| m26 | ps1 | 201 | throw | after packaging switch | Tracked workspace delta appeared during M26 validation:`n$($finalTracked -join "`n") |
| m27 | sh | 39 | exit 1 | before build | M27 exact-head gate requires no tracked workspace delta before validation |
| m27 | sh | 44 | exit 1 | before build | M27 base ref not found: origin/develop (fallback develop also missing) |
| m27 | sh | 63 | exit 1 | after build | Surefire failures=$FAILURES errors=$ERRORS |
| m27 | sh | 66 | exit 1 | after build | M27 test baseline regression: $TESTS < $TESTS_MINIMUM (testsMinimum in $RATCHETS) |
| m27 | sh | 70 | exit 1 | after build | M27 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM (architectureTestsMinimum in $RATCH... |
| m27 | sh | 83 | SystemExit | after build | M27 line coverage below {minimum_line:.3f} ratchet: {line}') |
| m27 | sh | 85 | SystemExit | after build | M27 branch coverage below {minimum_branch:.3f} ratchet: {branch}') |
| m27 | sh | 104 | exit 1 | after build | CycloneDX JSON/XML SBOM is missing |
| m27 | sh | 106 | exit 1 | after build | Build provenance is missing |
| m27 | sh | 112 | exit 1 | after packaging switch | Packaged launcher not found: $LAUNCHER |
| m27 | sh | 114 | exit 1 | after packaging switch | jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset> |
| m27 | sh | 125 | exit 1 | after packaging switch | M27 packaged runtime is missing $entry |
| m27 | sh | 150 | exit 1 | after packaging switch | HEAD changed during M27 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m27 | sh | 151 | exit 1 | after packaging switch | Tracked workspace delta appeared during M27 validation |
| m27 | ps1 | 31 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m27 | ps1 | 50 | throw | before build | Packaged launcher failed ($LASTEXITCODE): $($Arguments -join ' ')`n$text |
| m27 | ps1 | 56 | throw | before build | M27 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m27 | ps1 | 61 | throw | before build | M27 base ref not found: origin/develop (fallback develop also missing) |
| m27 | ps1 | 68 | throw | after build | Surefire failures=$($totals.Failures) errors=$($totals.Errors) |
| m27 | ps1 | 70 | throw | after build | M27 test baseline regression: $($totals.Tests) < $testsMinimum (testsMinimum in $ratchetsPath) |
| m27 | ps1 | 74 | throw | after build | M27 architecture baseline regression: $($architecture.Tests) < $architectureTestsMinimum (architectureTests... |
| m27 | ps1 | 85 | throw | after build | M27 line coverage below $lineCoverageMinimum ratchet: $($coverage.lineRatio) |
| m27 | ps1 | 88 | throw | after build | M27 branch coverage below $branchCoverageMinimum ratchet: $($coverage.branchRatio) |
| m27 | ps1 | 97 | throw | after build | M27 public convergence contract missing: $contract |
| m27 | ps1 | 100 | throw | after build | M27 OpenAPI separation/read-only contract is incomplete |
| m27 | ps1 | 105 | throw | after build | CycloneDX JSON/XML SBOM is missing |
| m27 | ps1 | 107 | throw | after build | Provenance writer failed with exit code $LASTEXITCODE |
| m27 | ps1 | 108 | throw | after build | Build provenance is missing |
| m27 | ps1 | 113 | throw | after packaging switch | Portable Windows build failed with exit code $LASTEXITCODE |
| m27 | ps1 | 115 | throw | after packaging switch | Packaged launcher not found: $launcher |
| m27 | ps1 | 117 | throw | after packaging switch | jar tool not found under JAVA_HOME=$env:JAVA_HOME |
| m27 | ps1 | 119 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| m27 | ps1 | 129 | throw | after packaging switch | M27 packaged runtime is missing $entry |
| m27 | ps1 | 134 | throw | after packaging switch | Packaged M27 adapter discovery smoke failed |
| m27 | ps1 | 140 | throw | after packaging switch | Packaged M27 facts-only separation smoke failed |
| m27 | ps1 | 148 | throw | after packaging switch | Packaged M27 assisted reasoning smoke failed |
| m27 | ps1 | 151 | throw | after packaging switch | Packaged M27 evidence/confidence contract failed |
| m27 | ps1 | 157 | throw | after packaging switch | HEAD changed during M27 validation: $validationSha -> $currentSha |
| m27 | ps1 | 159 | throw | after packaging switch | Tracked workspace delta appeared during M27 validation:`n$($finalTracked -join "`n") |
| m28 | sh | 22 | exit 1 | before build | M28 exact-head gate requires no tracked workspace delta before validation |
| m28 | sh | 45 | SystemExit | after packaging switch | M28 manager contract token is missing: {token}') |
| m28 | sh | 47 | SystemExit | after packaging switch | M28 native MCP client manager must not require Docker') |
| m28 | sh | 66 | SystemExit | after packaging switch | M28 installer contract token is missing: {token}') |
| m28 | sh | 68 | SystemExit | after packaging switch | M28 installer must not copy the app-image directly via [Files]; PrepareToInstall must activate it') |
| m28 | sh | 71 | SystemExit | after packaging switch | M28 installer must not reintroduce the retired static task: {task}') |
| m28 | sh | 76 | SystemExit | after packaging switch | M28 installer builder is missing the smoke-mode switch: {token}') |
| m28 | sh | 85 | SystemExit | after packaging switch | M28 transactional upgrade engine token is missing: {token}') |
| m28 | sh | 100 | SystemExit | after packaging switch | M28 required file is missing: {path}') |
| m28 | sh | 108 | exit 1 | after packaging switch | Packaged manager is missing |
| m28 | sh | 109 | exit 1 | after packaging switch | Packaged setup wrapper is missing |
| m28 | sh | 110 | exit 1 | after packaging switch | Packaged integration README is missing |
| m28 | sh | 116 | exit 1 | after packaging switch | HEAD changed during M28 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| m28 | sh | 120 | exit 1 | after packaging switch | Tracked workspace delta appeared during M28 validation |
| m28 | sh | 124 | exit 1 | after packaging switch | Inherited R2 summary missing: $R2_SUMMARY |
| m28 | ps1 | 22 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| m28 | ps1 | 28 | throw | before build | M28 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| m28 | ps1 | 44 | throw | before build | M28 required file is missing: $required |
| m28 | ps1 | 52 | throw | before build | M28 manager contract token is missing: $token |
| m28 | ps1 | 54 | throw | before build | M28 native MCP client manager must not require Docker |
| m28 | ps1 | 70 | throw | after packaging switch | Packaged M28 integration file is missing: $relative |
| m28 | ps1 | 88 | throw | after packaging switch | M28 Windows setup or checksum is missing |
| m28 | ps1 | 99 | throw | after packaging switch | M28 Windows smoke setup is missing |
| m28 | ps1 | 106 | throw | after packaging switch | HEAD changed during M28 validation: $validationSha -> $currentSha |
| m28 | ps1 | 109 | throw | after packaging switch | Tracked workspace delta appeared during M28 validation:`n$($finalTracked -join "`n") |
| m28 | ps1 | 113 | throw | after packaging switch | Inherited R2 summary missing: $r2SummaryPath |
| r2 | sh | 22 | exit 1 | before build | R2 exact-head gate requires no tracked workspace delta before validation |
| r2 | sh | 36 | SystemExit | before build | Unexpected Maven reactor POM count: {len(poms)}, expected {expected}') |
| r2 | sh | 40 | SystemExit | before build | MORPHEUS {version} version missing from {pom.relative_to(root)}') |
| r2 | sh | 42 | SystemExit | before build | Stale MORPHEUS 1.0.0 version remains in {pom.relative_to(root)}') |
| r2 | sh | 51 | exit 1 | before build | R2 upgrade report is missing: $UPGRADE_REPORT |
| r2 | sh | 61 | SystemExit | before build | R2 upgrade compatibility test failed: tests={tests} failures={failures} errors={errors}') |
| r2 | sh | 68 | exit 1 | after packaging switch | R2 packaged launcher is missing: $LAUNCHER |
| r2 | sh | 72 | exit 1 | after packaging switch | jar tool is unavailable |
| r2 | sh | 74 | exit 1 | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| r2 | sh | 92 | exit 1 | after packaging switch | R2 packaged runtime is missing $entry |
| r2 | sh | 96 | exit 1 | after packaging switch | Packaged M25 help surface is missing |
| r2 | sh | 97 | exit 1 | after packaging switch | Packaged M26 help surface is missing |
| r2 | sh | 102 | exit 1 | after packaging switch | Root POM is not $VERSION |
| r2 | sh | 103 | exit 1 | after packaging switch | Windows release default version is incoherent |
| r2 | sh | 104 | exit 1 | after packaging switch | Linux release default version is incoherent |
| r2 | sh | 108 | exit 1 | after packaging switch | HEAD changed during R2 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| r2 | sh | 112 | exit 1 | after packaging switch | Tracked workspace delta appeared during R2 validation |
| r2 | sh | 116 | exit 1 | after packaging switch | Inherited M27 summary missing: $M27_SUMMARY |
| r2 | ps1 | 22 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| r2 | ps1 | 33 | throw | before build | Unexpected Maven reactor POM count: $($poms.Count), expected $expectedPoms |
| r2 | ps1 | 38 | throw | before build | MORPHEUS $ExpectedVersion version missing from $($pom.FullName) |
| r2 | ps1 | 41 | throw | before build | Stale MORPHEUS 1.0.0 version remains in $($pom.FullName) |
| r2 | ps1 | 49 | throw | after packaging switch | R2 packaged launcher is missing: $launcher |
| r2 | ps1 | 52 | throw | after packaging switch | jar tool not found under JAVA_HOME=$env:JAVA_HOME |
| r2 | ps1 | 55 | throw | after packaging switch | Shaded MORPHEUS CLI JAR not found |
| r2 | ps1 | 74 | throw | after packaging switch | R2 packaged runtime is missing $entry |
| r2 | ps1 | 81 | throw | after packaging switch | R2 packaged M25/M26 CLI help smoke failed: $help |
| r2 | ps1 | 89 | throw | after packaging switch | R2 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| r2 | ps1 | 98 | throw | after packaging switch | R2 upgrade report is missing: $upgradeReport |
| r2 | ps1 | 101 | throw | after packaging switch | R2 upgrade compatibility test failed: tests=$($upgrade.testsuite.tests) failures=$($upgrade.testsuite.failu... |
| r2 | ps1 | 128 | throw | after packaging switch | R2 setup or checksum is missing under $installerOutput |
| r2 | ps1 | 135 | throw | after packaging switch | Root POM is not $Version |
| r2 | ps1 | 138 | throw | after packaging switch | Windows release default version is incoherent |
| r2 | ps1 | 139 | throw | after packaging switch | Linux release default version is incoherent |
| r2 | ps1 | 143 | throw | after packaging switch | HEAD changed during R2 validation: $validationSha -> $currentSha |
| r2 | ps1 | 146 | throw | after packaging switch | Tracked workspace delta appeared during R2 validation:`n$($finalTracked -join "`n") |
| r2 | ps1 | 150 | throw | after packaging switch | Inherited M27 summary missing: $m27SummaryPath |
| r3 | sh | 22 | exit 1 | before build | R3 exact-head gate requires no tracked workspace delta before validation |
| r3 | sh | 29 | exit 1 | before build | R3 must not modify GitHub Actions workflows during the July 2026 freeze |
| r3 | sh | 33 | exit 1 | before build | R3 must not introduce a SQLite migration for the configuration-only M28 release |
| r3 | sh | 48 | SystemExit | before build | Unexpected Maven reactor POM count: {len(poms)}, expected {expected}') |
| r3 | sh | 52 | SystemExit | before build | MORPHEUS {version} version missing from {pom.relative_to(root)}') |
| r3 | sh | 55 | SystemExit | before build | Stale MORPHEUS {stale} version remains in {pom.relative_to(root)}') |
| r3 | sh | 80 | SystemExit | after packaging switch | R3 required documentation is missing: {relative}') |
| r3 | sh | 92 | SystemExit | after packaging switch | R3 default version is incoherent in {relative}: expected {token}') |
| r3 | sh | 97 | SystemExit | after packaging switch | R3 release notes token is missing: {token}') |
| r3 | sh | 102 | exit 1 | after packaging switch | HEAD changed during R3 validation: $VALIDATION_SHA -> $CURRENT_SHA |
| r3 | sh | 106 | exit 1 | after packaging switch | Tracked workspace delta appeared during R3 validation |
| r3 | sh | 110 | exit 1 | after packaging switch | Inherited M28 summary missing: $M28_SUMMARY |
| r3 | ps1 | 22 | throw | before build | $Label failed with exit code $LASTEXITCODE |
| r3 | ps1 | 33 | throw | before build | Unexpected Maven reactor POM count: $($poms.Count), expected $expectedPoms |
| r3 | ps1 | 38 | throw | before build | MORPHEUS $ExpectedVersion version missing from $($pom.FullName) |
| r3 | ps1 | 42 | throw | before build | Stale MORPHEUS $stale version remains in $($pom.FullName) |
| r3 | ps1 | 52 | throw | before build | R3 exact-head gate requires no tracked workspace delta before validation:`n$($initialTracked -join "`n") |
| r3 | ps1 | 60 | throw | before build | R3 must not modify GitHub Actions workflows during the July 2026 freeze |
| r3 | ps1 | 63 | throw | before build | R3 must not introduce a SQLite migration for the configuration-only M28 release |
| r3 | ps1 | 84 | throw | before build | R3 required documentation is missing: $relative |
| r3 | ps1 | 98 | throw | after packaging switch | R3 default version is incoherent in $($check.Path): expected token $($check.Token) |
| r3 | ps1 | 105 | throw | after packaging switch | R3 release notes token is missing: $token |
| r3 | ps1 | 110 | throw | after packaging switch | HEAD changed during R3 validation: $validationSha -> $currentSha |
| r3 | ps1 | 113 | throw | after packaging switch | Tracked workspace delta appeared during R3 validation:`n$($finalTracked -join "`n") |
| r3 | ps1 | 117 | throw | after packaging switch | Inherited M28 summary missing: $m28SummaryPath |
