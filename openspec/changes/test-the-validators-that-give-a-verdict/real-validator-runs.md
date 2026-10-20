# Real runs of the validators no workflow launches (task 1.4)

Run on 2026-10-18 on `develop` at `29c942ae` (version 1.2.1), from two fresh clones of the repository, one per platform,
each on a branch cut from that commit (see finding 1 for why a branch). Nothing was stubbed: each run is the validator
as a workflow would launch it, a full `./mvnw clean verify` included, with the version argument `1.2.1`.

- **Linux**: WSL Ubuntu 24.04, `/usr/lib/jvm/java-21-openjdk-amd64`, `bash scripts/validate-<target>.sh 1.2.1`,
  `MORPHEUS_D2_SKIP_SECURITY_SCAN=true` as the lane `validators-without-a-lane` of `nightly.yml` sets it.
- **Windows**: Windows 10, Windows PowerShell 5.1 (`powershell -NoProfile -ExecutionPolicy Bypass -File
  scriptsalidate-<target>.ps1 -Version 1.2.1`), JDK 21 (`ms-21.0.12.1`), a private hardened `TMP`,
  `NoDefaultCurrentDirectoryInExePath` unset, `-SkipSecurityScan` for `d2`. The installer legs of `m28` and `r3` ran
  (`installer=True` in their summaries).
- Targets run in series, each alone in its clone. The machine ran other sessions' builds meanwhile: the durations
  below are a development machine's, not a runner's, and say only that nothing hangs.
- `r3` chains `m28`, `r2` and `m27`; their summaries are the ones those validators wrote during the `r3` run.
  `m28.ps1`, `r2.ps1` and `m27.ps1` are launched by a workflow already (through `validate-m28.ps1`); their `.sh` are
  not, and are the Linux rows below. `validate-m21.ps1` is launched by no workflow either, and is the last Windows row.

| target | Linux (bash) | Windows (PowerShell 5.1) |
|---|---|---|
| `d2` | PASS in 2149 s | PASS in 1543 s |
| `m21` | launched by `ci.yml` | PASS in 579 s |
| `m22` | PASS in 1442 s | FAIL rc=1 in 1061 s (finding 1), PASS in 573 s on a branch |
| `m23` | PASS in 1186 s | PASS in 1084 s |
| `m24` | PASS in 611 s | PASS in 1025 s |
| `m25` | PASS in 542 s | PASS in 717 s |
| `m26` | PASS in 542 s | PASS in 666 s |
| `r3` | PASS in 503 s | PASS in 807 s |
| `m27`, `m28`, `r2` | PASS, through `r3` (summaries below) | PASS, through `r3` (launched by a workflow already) |

Every run ended on `BUILD SUCCESS` (checked in the log, not from the exit code alone) and on the same counts: 4194 tests, 693 architecture tests, against the living minimums `3820 / 585`.

## Findings

1. **`write-build-provenance.ps1` fails on a detached HEAD without a tag.** `validate-m22.ps1` ran its whole build and its
   test, count and coverage checks, printed `JaCoCo: PASS`, and died at `write-build-provenance.ps1:35` with *Cannot call a
   method on a null expression*: `git branch --show-current` prints nothing on a detached HEAD, and `.Trim()` was called on
   that nothing before the `detached` fallback two lines below could apply. The `.sh` writer is not affected. Seven
   validators call the writer (`m21` to `m27`, `.ps1`). No test had a detached checkout: the fabricated checkout is
   on a branch. Reproduced on the writer alone, corrected with a null-safe read, and held by
   `scripts/tests/test_write_build_provenance.py` (red on the old line, green on the new one, both interpreters).
   The workflows are not hit as far as read: `actions/checkout` puts a push or a dispatch on a branch, and a pull
   request sets `GITHUB_REF_NAME`; a person running a `.ps1` on a checked-out commit is.
2. **Nothing else failed to finish.** On both platforms every other validator ran to its summary. `validate-r3`, which could
   not pass before the reactor-count correction (3.9), passes in both variants and chains `m28`, `r2` and `m27` to
   their summaries.
3. **A refused run leaves no summary**, as the validators claim: the failed `m22` run on Windows wrote none
   (`validation-output/m22/` was empty).
4. **What the runs do not cover.** `d2` skipped its Dependency-Check scan, as the lane does. The `.ps1` of `d2`, `m21`
   to `m26` and `r3` ran once, here; no lane runs them, and `nightly.yml` is Linux only. The Windows runs cover the
   packaging half (portable archive, installer for `m28` and `r3`) that no test reaches.

## Summaries written by the validators

### Linux `d2`

```text
D2 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910060
branchCoverage=0.771111
dependencyHygiene=PASS
securityScan=SKIPPED
sbom=PASS
portable=PASS
ciUsed=false
ciWorkflowDelta=NONE
workspaceTrackedClean=PASS
```

### Linux `m22`

```text
M22 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
sdkApiVersion=1
externalReferenceProvider=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

### Linux `m23`

```text
M23 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910060
branchCoverage=0.771206
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

### Linux `m24`

```text
M24 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910093
branchCoverage=0.771206
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

### Linux `m25`

```text
M25 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
policyPacks=PASS
policyVersioning=PASS
policyOverrides=PASS
policyDryRun=PASS
policyExplainability=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

### Linux `m26`

```text
M26 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
expectedSchema=22
tests=4194
architectureTests=693
lineCoverage=0.910093
branchCoverage=0.771111
localFirst=PASS
remoteTlsAuthRbac=PASS
boundedConcurrency=PASS
secretNonDisclosure=PASS
backupRestore=PASS
schemaCompatibility=PASS
surfaceConvergence=PASS
sqliteSchema=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

### Linux `r3`

```text
R3 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
reactorVersion=PASS
mcpClientIntegration=PASS
clients=5
releaseDocumentation=PASS
schemaMigration=UNCHANGED
ciWorkflowDelta=NONE
portable=true
installer=NOT_APPLICABLE
sbom=PASS
provenance=PASS
dockerRequired=false
postGateExecutableDelta=NONE
```

### Linux `m28`

```text
M28 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
mcpClientManager=STATIC_PASS
clients=5
jsonMerge=WINDOWS_ONLY
cliRegistration=WINDOWS_ONLY
idempotency=WINDOWS_ONLY
foreignEntryPreservation=WINDOWS_ONLY
modifiedEntryPreservation=WINDOWS_ONLY
stateDrivenUninstall=WINDOWS_ONLY
invalidJsonProtection=WINDOWS_ONLY
portable=true
installer=NOT_APPLICABLE
dockerRequired=false
postGateExecutableDelta=NONE
```

### Linux `r2`

```text
R2 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
reactorVersion=PASS
sqliteV012ToV015Upgrade=PASS
policyPacks=PASS
remoteServer=PASS
assistedReasoning=PASS
surfaceConvergence=PASS
packagedM25M26=SKIPPED
sbom=PASS
provenance=PASS
portable=false
installer=NOT_APPLICABLE
postGateExecutableDelta=NONE
```

### Linux `m27`

```text
M27 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910159
branchCoverage=0.771301
factsInferenceSeparation=PASS
explicitConfidence=PASS
evidenceProvenance=PASS
adapterOptionality=PASS
adapterFailureIsolation=PASS
noSilentMutation=PASS
surfaceConvergence=PASS
remoteReadRbac=PASS
sbom=PASS
provenance=PASS
portable=false
postGateExecutableDelta=NONE
```

### Windows `m21`

```text
M21 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
coverageScope=aggregate
lineCoverage=0.910425
branchCoverage=0.771396
qualityRatchets=3820/585/0.900/0.757
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `d2`

```text
D2 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910525
branchCoverage=0.771586
dependencyHygiene=PASS
securityScan=SKIPPED
sbom=PASS
portable=PASS
ciUsed=false
ciWorkflowDelta=NONE
workspaceTrackedClean=PASS
```

### Windows `m22` (rerun on a branch)

```text
M22 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
sdkApiVersion=1
externalReferenceProvider=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `m23`

```text
M23 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `m24`

```text
M24 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/main
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `m25`

```text
M25 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771301
policyPacks=PASS
policyVersioning=PASS
policyOverrides=PASS
policyDryRun=PASS
policyExplainability=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `m26`

```text
M26 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
expectedSchema=22
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771301
localFirst=PASS
remoteTlsAuthRbac=PASS
boundedConcurrency=PASS
secretNonDisclosure=PASS
backupRestore=PASS
schemaCompatibility=PASS
surfaceConvergence=PASS
sqliteSchema=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

### Windows `r3`

```text
R3 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
reactorVersion=PASS
mcpClientIntegration=PASS
clients=5
releaseDocumentation=PASS
schemaMigration=UNCHANGED
ciWorkflowDelta=NONE
portable=True
installer=True
sbom=PASS
provenance=PASS
dockerRequired=false
postGateExecutableDelta=NONE
```

### Windows `m28`

```text
M28 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
mcpClientManager=PASS
clients=5
jsonMerge=PASS
cliRegistration=PASS
idempotency=PASS
foreignEntryPreservation=PASS
modifiedEntryPreservation=PASS
stateDrivenUninstall=PASS
invalidJsonProtection=PASS
portable=True
installer=True
smokeSetupMcpSelected=True
dockerRequired=false
postGateExecutableDelta=NONE
```

### Windows `r2`

```text
R2 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
reactorVersion=PASS
sqliteV012ToV015Upgrade=PASS
policyPacks=PASS
remoteServer=PASS
assistedReasoning=PASS
surfaceConvergence=PASS
packagedM25M26=SKIPPED
sbom=PASS
provenance=PASS
portable=False
installer=False
postGateExecutableDelta=NONE
```

### Windows `m27`

```text
M27 VALIDATION PASS
sha=29c942ae29a10ae5a14ae9f04f669d95c0c2eac4
baseRef=origin/develop
version=1.2.1
tests=4194
architectureTests=693
lineCoverage=0.910425
branchCoverage=0.771396
factsInferenceSeparation=PASS
explicitConfidence=PASS
evidenceProvenance=PASS
adapterOptionality=PASS
adapterFailureIsolation=PASS
noSilentMutation=PASS
surfaceConvergence=PASS
remoteReadRbac=PASS
sbom=PASS
provenance=PASS
portable=False
postGateExecutableDelta=NONE
```
