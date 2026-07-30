# R2 — Validation MORPHEUS 1.1.0

Statut : **CANDIDATE FROZEN — GATES NOT RUN — AUCUN PASS DÉCLARÉ**

Date d'ouverture : 30 juillet 2026

```text
Issue                   #113 OPEN
PR                      #114 DRAFT vers main
Branch                  r2-release-1.1.0
Main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
Develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate    cde78c8172d720a01254f7463f4ff60d09a8b677
Target version          1.1.0
Target tag              v1.1.0
Stable published tag    v1.0.0
```

Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Périmètre candidat observé

La comparaison `develop...cde78c8` donne :

```text
status           ahead
commits          38
behind           0
changed files    35
```

Le delta candidat comprend :

- le reactor Maven complet en 1.1.0 ;
- les builders portable/installer/release en 1.1.0 ;
- le test de cohérence des 17 POM ;
- le test d'upgrade SQLite V012→V015 ;
- les validateurs R2 Windows et Linux/WSL ;
- les preuves packagées M25/M26 et la preuve M27 héritée ;
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
- [ ] workspace Windows exact-head contrôlé ;
- [ ] workspace Linux/WSL exact-head contrôlé ;
- [ ] SHA identique réellement qualifié sur les deux plateformes.

Les coches de préparation attestent de la présence du code ou de la documentation, **pas de leur réussite d'exécution**.

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

Statut : **NOT RUN**

```text
sha                      cde78c8172d720a01254f7463f4ff60d09a8b677 candidate only
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
installer                NOT RUN
postGateExecutableDelta  NOT RUN
```

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

```text
sha                      cde78c8172d720a01254f7463f4ff60d09a8b677 candidate only
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

Statut observé : **CODED / NOT RUN**.

## 7. Exact-tag et artefacts

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

## 8. GitHub Release

```text
tagName       v1.1.0
isDraft       expected false
isPrerelease  expected false
assets        expected 8/8
status        NOT CREATED
```

## 9. Chronologie factuelle

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
2026-07-30  executable candidate frozen at cde78c8172d720a01254f7463f4ff60d09a8b677
2026-07-30  subsequent allowed work restricted to documentation
```

## 10. Résultat courant

```text
executable candidate              cde78c8172d720a01254f7463f4ff60d09a8b677
Windows exact-head gate           NOT RUN
Linux exact-head gate             NOT RUN
same SHA cross-platform           NOT PROVEN
upgrade 1.0.0 -> 1.1.0            NOT RUN
post-gate executable delta        NOT PROVEN
PR ready                          NO / DRAFT
merge main                        NOT AUTHORIZED
tag v1.1.0                        NOT AUTHORIZED
exact-tag builds                  NOT RUN
GitHub Release                    NOT CREATED
Result                            R2 IN PROGRESS
```

**Chaque valeur PASS devra provenir de sorties réellement observées. Ce document ne transforme pas un candidat en release qualifiée.**