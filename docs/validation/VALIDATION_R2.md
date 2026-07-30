# R2 — Validation MORPHEUS 1.1.0

Statut : **CANDIDATE UPDATED — WINDOWS GATE FAILED — RERUN REQUIRED — AUCUN PASS DÉCLARÉ**

Date d'ouverture : 30 juillet 2026

```text
Issue                   #113 OPEN
PR                      #114 DRAFT vers main
Branch                  r2-release-1.1.0
Main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
Develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate    c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Target version          1.1.0
Target tag              v1.1.0
Stable published tag    v1.0.0
```

Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Périmètre candidat observé

La comparaison `develop...c206e1b` donne :

```text
status           ahead
commits          48
behind           0
changed files    38
```

Le delta candidat comprend :

- le reactor Maven complet en 1.1.0 ;
- les builders portable/installer/release en 1.1.0 ;
- le test de cohérence des 17 POM ;
- le test d'upgrade SQLite V012→V015 ;
- les validateurs R2 Windows et Linux/WSL ;
- les preuves packagées M25/M26 et la preuve M27 héritée ;
- les scénarios application et CLI de découverte de mise à jour indépendants de la version courante ;
- le contrat M22 de plugin externe résolvant le JAR de référence depuis `ProductMetadata.version()` ;
- les notes de version, le guide d'upgrade et la gouvernance R2.

Aucun fichier `.github/workflows` n'est modifié.

## 2. Préparation

- [x] M25, M26 et M27 intégrés dans `develop` ;
- [x] issue #113 et PR draft #114 créées ;
- [x] 17 POM et builders de distribution préparés en 1.1.0 ;
- [x] fixture d'upgrade V012→V015 et contrôles d'idempotence codés ;
- [x] validateurs Windows/Linux préparés ;
- [x] quatre tentatives Windows observées et conservées ;
- [x] deux scénarios d'update-check rendus indépendants de la version courante ;
- [x] chemin du JAR de plugin externe rendu indépendant de la version courante ;
- [ ] nouveau candidat `c206e1b...` contrôlé sous Windows ;
- [ ] workspace Linux/WSL exact-head contrôlé ;
- [ ] SHA identique réellement qualifié sur les deux plateformes.

Les coches de préparation ne valent pas PASS du gate complet.

## 3. Commandes canoniques

Windows :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Linux/WSL :

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

Build exact-tag Windows, uniquement après autorisation du tag :

```powershell
.\distribution\build-release.ps1 -Version 1.1.0 -ExpectedTag v1.1.0
```

Build exact-tag Linux, uniquement après autorisation du tag :

```bash
bash ./distribution/build-release.sh 1.1.0 v1.1.0
```

## 4. Qualification Windows

Statut : **FAILED — CORRECTIF POUSSÉ — RERUN REQUIRED**

### Tentative 1 — `3db57b33960ef16af9f3b6e49fc247e3bf843efb`

```text
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         FAIL
cause                    trailing whitespace in docs/governance/ROADMAP.md
maven                    NOT STARTED
result                   FAIL
```

Correction documentaire : `c500d70ec7ee0ad2acfcbd4c4a49346c7c93f975`.

### Tentative 2 — `c500d70ec7ee0ad2acfcbd4c4a49346c7c93f975`

```text
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         PASS
reactor root             SUCCESS
morpheus-domain          SUCCESS — 40 tests PASS
morpheus-application     FAILURE
application tests        137 run / 1 failure / 0 error / 0 skipped
failing test             ProductIntegrityTest.explicitFileManifestCanReportANewerVersion
cause                    manifest 1.0.1 no longer newer than current 1.1.0
remaining modules        SKIPPED
result                   FAIL
```

Correctif exécutable : `aef3ed8a65397e7ca2fa5aa6abdf41237025605a`.

### Tentative 3 — `24e3b0b66cb80388ffb687fd470237174e083121`

```text
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         PASS
reactor modules          15 SUCCESS / CLI FAILURE / architecture SKIPPED
tests observed           365 run through the CLI module / 1 failure
morpheus-application     SUCCESS — 137 tests PASS
store-sqlite             SUCCESS — 18 tests PASS
sqliteV012ToV015Upgrade  PASS — R2UpgradeCompatibilityTest
morpheus-mcp             SUCCESS — 22 tests PASS
morpheus-api             SUCCESS — 32 tests PASS
morpheus-cli             FAILURE — 53 tests / 1 failure
failing test             MorpheusProductCliTest.updateCheckIsExplicitAndDoesNotApplyAnything
cause                    CLI test manifest remained fixed at 1.0.1
architectureTests        NOT RUN
packaging gates          NOT REACHED
result                   FAIL
```

Correctif exécutable : `43dc9cfb78b8b40276b3eee8a05ec828660f88b4`.

### Tentative 4 — `34b8955a74270ded0b5464196a45eff746085168`

```text
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         PASS
reactor modules          16 SUCCESS / architecture FAILURE
tests                    603 run / 1 failure / 0 error / 0 skipped
morpheus-domain          SUCCESS — 40 tests PASS
morpheus-application     SUCCESS — 137 tests PASS
provider-sdk             SUCCESS — 11 tests PASS
provider-reference       SUCCESS — 2 tests PASS
provider-openspec        SUCCESS — 26 tests PASS
provider-markdown        SUCCESS — 2 tests PASS
provider-synthetic       SUCCESS — 7 tests PASS
store-sqlite             SUCCESS — 18 tests PASS
sqliteV012ToV015Upgrade  PASS — R2UpgradeCompatibilityTest
integration-minos        SUCCESS — 8 tests PASS
integration-nexus        SUCCESS — 7 tests PASS
morpheus-mcp             SUCCESS — 22 tests PASS
morpheus-api             SUCCESS — 32 tests PASS
morpheus-cli             SUCCESS — 53 tests PASS
architectureTests        238 run / 1 failure / 0 error / 0 skipped
failing test             ProviderPluginPlatformContractTest.externalReferenceJarIsDiscoveredActivatedInDedicatedLoaderProbedAndRead
cause                    contract expected morpheus-provider-reference-1.0.0.jar while reactor built 1.1.0
coverage                 generated during reactor but final gate not reached
packaging gates          NOT REACHED
result                   FAIL
```

Le JAR de référence `morpheus-provider-reference-1.1.0.jar` a bien été construit par le module précédent. L'échec concernait uniquement le chemin figé dans le contrat M22.

Correctif exécutable : `c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1`.

Le contrat compose désormais le nom du JAR depuis `ProductMetadata.version()`. Surefire fournit cette valeur via `morpheus.project.version=${project.version}`. La recherche du dépôt ne trouve aucun autre chemin exécutable `1.0.0.jar` figé.

### État à requalifier

```text
sha                      c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1 candidate executable
reactor                  NOT RUN on current candidate
tests                    NOT RUN on current candidate
architectureTests        NOT RUN on current candidate
lineCoverage             NOT RUN on current candidate
branchCoverage           NOT RUN on current candidate
versionCoherence         NOT RUN on current candidate
sqliteV012ToV015Upgrade  NOT RUN on current candidate
policyPacks              NOT RUN on current candidate
remoteServer             NOT RUN on current candidate
assistedReasoning        NOT RUN on current candidate
surfaceConvergence       NOT RUN on current candidate
packagedM25M26           NOT RUN on current candidate
packagedM27              NOT RUN on current candidate
sbom                     NOT QUALIFIED
provenance               NOT RUN
portable                 NOT RUN
installer                NOT RUN
postGateExecutableDelta  NOT RUN
```

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

```text
sha                      c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1 candidate executable
reactor                  NOT RUN
tests                    NOT RUN
architectureTests        NOT RUN
lineCoverage             NOT RUN
branchCoverage           NOT RUN
versionCoherence         NOT RUN
sqliteV012ToV015Upgrade  NOT RUN
policyPacks              NOT RUN
remoteServer             NOT RUN
assistedReasoning        NOT RUN
surfaceConvergence       NOT RUN
packagedM25M26           NOT RUN
packagedM27              NOT RUN
sbom                     NOT RUN
provenance               NOT RUN
portable                 NOT RUN
postGateExecutableDelta  NOT RUN
```

## 6. Upgrade SQLite observé

Les tentatives 3 et 4 ont exécuté `R2UpgradeCompatibilityTest` avec :

```text
tests       1
failures    0
errors      0
skipped     0
result      PASS on superseded Windows SHAs
```

Le scénario couvre V001→V012, l'application unique de V013/V014/V015, la préservation des identités et snapshots publiés, l'immutabilité des checksums et le redémarrage idempotent. Ces succès partiels ne qualifient pas le gate Windows complet et devront être reproduits sur le nouveau candidat exact.

## 7. Workspace et artefacts historiques locaux

Les tentatives Windows ont observé le répertoire non suivi :

```text
dist-r1/
```

Le gate exact-head R2 ignore les fichiers non suivis et cet élément n'a causé aucun échec Maven. Les builds exact-tag exigent toutefois un workspace totalement propre ; `dist-r1/` devra être déplacé ou supprimé avant la publication.

## 8. Exact-tag et artefacts

Statut : **BLOCKED — merge et tag non autorisés**

| Asset | Bytes | SHA-256 | Statut |
|---|---:|---|---|
| `MORPHEUS-1.1.0-windows-x64-setup.exe` | — | — | NOT BUILT |
| `MORPHEUS-1.1.0-windows-x64-setup.exe.sha256` | — | — | NOT BUILT |
| `morpheus-1.1.0-windows-x64.zip` | — | — | NOT BUILT |
| `morpheus-1.1.0-windows-x64.zip.sha256` | — | — | NOT BUILT |
| `morpheus-1.1.0-linux-x64.tar.gz` | — | — | NOT BUILT |
| `morpheus-1.1.0-linux-x64.tar.gz.sha256` | — | — | NOT BUILT |
| Windows release manifest | — | — | NOT BUILT |
| Linux release manifest | — | — | NOT BUILT |

## 9. GitHub Release

```text
tagName       v1.1.0
isDraft       expected false
isPrerelease  expected false
assets        expected 8/8
status        NOT CREATED
```

## 10. Chronologie factuelle

```text
2026-07-30  main/develop audit completed
2026-07-30  duplicate issue #102 closed
2026-07-30  R2 issue #113 and draft PR #114 created
2026-07-30  Maven reactor and distribution defaults moved to 1.1.0
2026-07-30  V012 -> V015 compatibility test and R2 validators added
2026-07-30  attempt 1 failed git diff --check; corrected at c500d70...
2026-07-30  attempt 2 failed ProductIntegrityTest; corrected at aef3ed8...
2026-07-30  attempt 3 reached 15 successful modules, then failed MorpheusProductCliTest; corrected at 43dc9cf...
2026-07-30  attempt 4 executed 603 tests and reached architecture, then failed on a hard-coded 1.0.0 plugin JAR path
2026-07-30  provider plugin architecture contract corrected at c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
```

## 11. Résultat courant

```text
executable candidate              c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Windows exact-head gate           FAILED / RERUN REQUIRED
Linux exact-head gate             NOT RUN
same SHA cross-platform           NOT PROVEN
upgrade 1.0.0 -> 1.1.0            PARTIAL PASS on superseded SHAs / rerun required
post-gate executable delta        NOT PROVEN
PR ready                          NO / DRAFT
merge main                        NOT AUTHORIZED
tag v1.1.0                        NOT AUTHORIZED
exact-tag builds                  NOT RUN
GitHub Release                    NOT CREATED
Result                            R2 IN PROGRESS
```

**Chaque valeur PASS finale devra provenir de sorties réellement observées sur le nouveau head exact. Les quatre échecs précédents restent conservés comme faits de qualification.**
