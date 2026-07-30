# R2 — Validation MORPHEUS 1.1.0

Statut : **NOT RUN — aucun PASS déclaré**

Date d'ouverture : 30 juillet 2026

Issue : **#113**  
PR draft : **#114**  
Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md)

## 1. Identité de préparation

```text
Repository              FTurleque/morpheus-engine
main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
release branch          r2-release-1.1.0
release branch baseline bccc118dda6fd818cf801750187afa4ad10b96e4
current release         v1.0.0
target version          1.1.0
target tag              v1.1.0
```

Le SHA exact de qualification R2 n'est pas encore défini. Il sera enregistré après le dernier changement exécutable et avant toute exécution Windows/Linux.

## 2. Préconditions

- [x] M25 qualifié et intégré dans `develop` ;
- [x] M26 qualifié et intégré dans `develop` ;
- [x] M27 qualifié et intégré dans `develop` ;
- [x] issue R2 créée ;
- [x] branche R2 créée depuis le head exact de `develop` ;
- [x] PR draft vers `main` ouverte ;
- [ ] version 1.1.0 cohérente ;
- [ ] workspace Windows propre ;
- [ ] workspace Linux/WSL propre ;
- [ ] SHA exact identique sur les deux plateformes ;
- [ ] données d'upgrade 1.0.0 préparées.

## 3. Commandes canoniques prévues

Windows :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Linux/WSL :

```bash
./scripts/validate-r2.sh 1.1.0
```

Build exact-tag Windows après autorisation du tag :

```powershell
.\distribution\build-release.ps1 -Version 1.1.0 -ExpectedTag v1.1.0
```

Build exact-tag Linux après autorisation du tag :

```bash
./distribution/build-release.sh 1.1.0 v1.1.0
```

## 4. Qualification Windows

Statut : **NOT RUN**

```text
sha                      NOT SET
reactor                  NOT RUN
tests                    NOT RUN
architectureTests        NOT RUN
lineCoverage             NOT RUN
branchCoverage           NOT RUN
versionCoherence         NOT RUN
sqliteUpgrade            NOT RUN
policyPacks              NOT RUN
remoteServer             NOT RUN
assistedReasoning        NOT RUN
surfaceConvergence       NOT RUN
sbom                     NOT RUN
provenance               NOT RUN
portable                 NOT RUN
installer                NOT RUN
packagedSmokes           NOT RUN
postGateExecutableDelta  NOT RUN
```

## 5. Qualification Linux/WSL

Statut : **NOT RUN**

```text
sha                      NOT SET
reactor                  NOT RUN
tests                    NOT RUN
architectureTests        NOT RUN
lineCoverage             NOT RUN
branchCoverage           NOT RUN
versionCoherence         NOT RUN
sqliteUpgrade            NOT RUN
policyPacks              NOT RUN
remoteServer             NOT RUN
assistedReasoning        NOT RUN
surfaceConvergence       NOT RUN
sbom                     NOT RUN
provenance               NOT RUN
portable                 NOT RUN
packagedSmokes           NOT RUN
postGateExecutableDelta  NOT RUN
```

## 6. Upgrade 1.0.0 → 1.1.0

Statut : **NOT RUN**

Le scénario doit au minimum prouver :

```text
backup before upgrade
open existing 1.0.0 SQLite database
apply missing migrations through V015 exactly once
preserve project and domain identities
preserve snapshots and published history
preserve audit records
preserve provider and portfolio state
introduce policy tables without corrupting prior data
restart 1.1.0 against upgraded database
repeat startup without reapplying migrations
```

Aucune affirmation de compatibilité n'est autorisée sans logs et vérifications de données observables.

## 7. Exact-tag et artefacts

Statut : **BLOCKED — merge/tag non autorisés**

| Asset | Bytes | SHA-256 | Statut |
|---|---:|---|---|
| `MORPHEUS-1.1.0-windows-x64-setup.exe` | — | — | NOT BUILT |
| `morpheus-1.1.0-windows-x64.zip` | — | — | NOT BUILT |
| `morpheus-1.1.0-linux-x64.tar.gz` | — | — | NOT BUILT |
| Windows manifest | — | — | NOT BUILT |
| Linux manifest | — | — | NOT BUILT |

Manifestes attendus :

```text
version = 1.1.0
tag     = v1.1.0
gitSha  = exact authorized release SHA
```

## 8. GitHub Release

Statut : **NOT CREATED**

```text
tagName       v1.1.0
isDraft       expected false
isPrerelease  expected false
assets        expected 8/8
```

## 9. Chronologie factuelle

```text
2026-07-30  audit main/develop : develop +188 / behind 0
2026-07-30  issue dupliquée #102 fermée duplicate
2026-07-30  issue R2 #113 créée
2026-07-30  branche r2-release-1.1.0 créée depuis develop@bccc118d...
2026-07-30  PR draft #114 ouverte vers main
2026-07-30  plan et validation R2 initialisés
```

## 10. Résultat courant

```text
release SHA exact                 NOT SET
Windows exact-head gate           NOT RUN
Linux exact-head gate             NOT RUN
same SHA cross-platform           NOT PROVEN
upgrade 1.0.0 -> 1.1.0            NOT RUN
post-gate executable delta        NOT PROVEN
merge main                        NOT AUTHORIZED
tag v1.1.0                        NOT AUTHORIZED
exact-tag builds                  NOT RUN
GitHub Release                    NOT CREATED
Result                            R2 IN PROGRESS
```

**Ce document est une structure de preuve, pas une déclaration de réussite. Chaque valeur PASS devra provenir de sorties réellement observées sur le SHA exact enregistré.**