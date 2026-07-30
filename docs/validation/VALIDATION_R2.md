# R2 — Validation MORPHEUS 1.1.0

Statut : **CANDIDATE UPDATED — WINDOWS GATE FAILED — RERUN REQUIRED — AUCUN PASS DÉCLARÉ**

Date d'ouverture : 30 juillet 2026

```text
Issue                   #113 OPEN
PR                      #114 DRAFT vers main
Branch                  r2-release-1.1.0
Main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
Develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate    aef3ed8a65397e7ca2fa5aa6abdf41237025605a
Target version          1.1.0
Target tag              v1.1.0
Stable published tag    v1.0.0
```

Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Périmètre candidat observé

La comparaison `develop...aef3ed8` donne :

```text
status           ahead
commits          42
behind           0
changed files    36
```

Le delta candidat comprend :

- le reactor Maven complet en 1.1.0 ;
- les builders portable/installer/release en 1.1.0 ;
- le test de cohérence des 17 POM ;
- le test d'upgrade SQLite V012→V015 ;
- les validateurs R2 Windows et Linux/WSL ;
- les preuves packagées M25/M26 et la preuve M27 héritée ;
- le correctif du scénario de découverte de mise à jour pour dériver une version patch réellement supérieure à la version courante ;
- les notes de version et le guide d'upgrade ;
- la gouvernance et les index R2.

Aucun fichier `.github/workflows` n'est modifié.

## 2. Préparation

- [x] M25, M26 et M27 intégrés dans `develop` ;
- [x] issue #113 créée ;
- [x] branche R2 basée sur le head exact de `develop` ;
- [x] PR #114 ouverte en draft vers `main` ;
- [x] 17 POM préparés en version 1.1.0 ;
- [x] defaults de distribution préparés en 1.1.0 ;
- [x] fixture d'upgrade V012 préparée ;
- [x] contrôles de préservation et d'idempotence codés ;
- [x] validateurs Windows/Linux préparés ;
- [x] documentation candidate préparée ;
- [x] première tentative Windows observée sur `3db57b3...` ;
- [x] seconde tentative Windows observée sur `c500d70...` ;
- [ ] nouveau candidat `aef3ed8...` contrôlé sous Windows ;
- [ ] workspace Linux/WSL exact-head contrôlé ;
- [ ] SHA identique réellement qualifié sur les deux plateformes.

Les coches de préparation attestent de la présence du code, de la documentation ou d'une exécution observée. Elles ne valent pas PASS du gate complet.

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
cause                    test manifest announced 1.0.1, no longer newer than current 1.1.0
remaining modules        SKIPPED
result                   FAIL
```

Correctif exécutable : `aef3ed8a65397e7ca2fa5aa6abdf41237025605a`.

Le test calcule désormais une version patch supérieure à `ProductMetadata.version()` au lieu de figer `1.0.1`. Pour `1.1.0`, le manifeste de test annonce `1.1.1`.

### État à requalifier

```text
sha                      aef3ed8a65397e7ca2fa5aa6abdf41237025605a candidate executable
reactor                  NOT RUN on current candidate
tests                    NOT RUN on current candidate
architectureTests        NOT RUN
lineCoverage             NOT RUN
branchCoverage           NOT RUN
versionCoherence         NOT RUN on current candidate
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
installer                NOT RUN
postGateExecutableDelta  NOT RUN
```

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

```text
sha                      aef3ed8a65397e7ca2fa5aa6abdf41237025605a candidate executable
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

## 6. Scénario d'upgrade préparé

Le test `R2UpgradeCompatibilityTest` doit prouver à l'exécution :

```text
create canonical V001 -> V012 migration ledger
insert existing project identity
insert ACTIVE published snapshot
open database with 1.1.0
apply V013, V014 and V015 exactly once
preserve project identity and source locator
preserve ACTIVE snapshot and source revision
preserve V001 -> V012 migration checksums
create portfolios, saved_views and policy_packs tables
restart 1.1.0 without migration replay
preserve one project and one snapshot without duplication
```

Statut observé : **CODED / NOT RUN TO COMPLETION**.

## 7. Workspace et artefacts historiques locaux

La seconde tentative a observé un répertoire non suivi :

```text
dist-r1/
```

Le gate exact-head R2 ignore volontairement les fichiers non suivis et cet élément n'a pas causé l'échec Maven. En revanche, les builds exact-tag imposent un workspace totalement propre via `git status --porcelain`. `dist-r1/` devra donc être déplacé ou supprimé avant les builds de publication.

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

Manifestes attendus :

```text
version = 1.1.0
tag     = v1.1.0
gitSha  = exact authorized release SHA
```

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
2026-07-30  R2 issue #113 created
2026-07-30  r2-release-1.1.0 created from develop@bccc118d...
2026-07-30  draft PR #114 opened to main
2026-07-30  Maven reactor and distribution defaults moved to 1.1.0
2026-07-30  V012 -> V015 compatibility test added
2026-07-30  Windows/Linux R2 validators added
2026-07-30  packaged M25/M26 coverage added; M27 inherited
2026-07-30  first Windows attempt failed git diff --check on three trailing spaces
2026-07-30  whitespace corrected at c500d70ec7ee0ad2acfcbd4c4a49346c7c93f975
2026-07-30  second Windows attempt passed version/diff gates then failed ProductIntegrityTest
2026-07-30  update-discovery test corrected at aef3ed8a65397e7ca2fa5aa6abdf41237025605a
```

## 11. Résultat courant

```text
executable candidate              aef3ed8a65397e7ca2fa5aa6abdf41237025605a
Windows exact-head gate           FAILED / RERUN REQUIRED
Linux exact-head gate             NOT RUN
same SHA cross-platform           NOT PROVEN
upgrade 1.0.0 -> 1.1.0            NOT RUN TO COMPLETION
post-gate executable delta        NOT PROVEN
PR ready                          NO / DRAFT
merge main                        NOT AUTHORIZED
tag v1.1.0                        NOT AUTHORIZED
exact-tag builds                  NOT RUN
GitHub Release                    NOT CREATED
Result                            R2 IN PROGRESS
```

**Chaque valeur PASS devra provenir de sorties réellement observées sur le nouveau head exact. Les échecs précédents restent conservés comme faits de qualification.**