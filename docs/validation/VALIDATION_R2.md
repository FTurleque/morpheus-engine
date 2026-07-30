# R2 — Validation MORPHEUS 1.1.0

Statut : **PASS — RELEASE 1.1.0 PUBLIÉE ET VÉRIFIÉE**

Date : 30 juillet 2026

```text
Issue                   #113 CLOSED / completed
PR                      #114 MERGED vers main
Branch                  r2-release-1.1.0
Main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
Develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate    c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Qualified exact head    31212087ee5fab3c88b269d56f7f21402f31b683
Final PR docs head      7e97071f88a3eef0c275708e12f6ff3cc0ce8c25
Main merge commit       31506029ded1101f0571edeb0d79c59bbf3f68c6
Published version       1.1.0
Published tag           v1.1.0
GitHub Release          stable / 8 assets
```

Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Périmètre qualifié

Le candidat comprend :

- le reactor Maven complet en 1.1.0 ;
- les builders portable, installer et release en 1.1.0 ;
- le contrôle de cohérence des 17 POM ;
- l’upgrade SQLite V012→V015 ;
- les validateurs R2 Windows et Linux/WSL ;
- les preuves packagées M25, M26 et M27 ;
- les notes de version et le guide d’upgrade ;
- les builds exact-tag et les manifestes de publication.

Aucun fichier `.github/workflows` n’a été modifié ni utilisé comme gate.

Le delta exécutable après qualification est **NONE**. Les modifications entre le SHA qualifié et le head final de PR sont documentaires uniquement.

## 2. Commandes canoniques exécutées

Windows :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Linux/WSL :

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

Les deux commandes ont qualifié exactement :

```text
31212087ee5fab3c88b269d56f7f21402f31b683
```

## 3. Qualification Windows

Statut : **PASS**

```text
sha                      31212087ee5fab3c88b269d56f7f21402f31b683
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         PASS
reactor                  PASS — 17/17 modules SUCCESS
tests                    PASS — 603 / 603
architectureTests        PASS — 238 / 238
lineCoverage             PASS — 0.452226
branchCoverage           PASS — 0.384456
sqliteV012ToV015Upgrade  PASS
policyPacks              PASS
remoteServer             PASS
assistedReasoning        PASS
surfaceConvergence       PASS
packagedM25M26           PASS
packagedM27              PASS
sbom                     PASS
provenance               PASS
portableWindows          PASS
installerWindows         PASS
postGateExecutableDelta  NONE
result                   R2 VALIDATION PASS
```

Le bootstrap Inno Setup 7.0.2 a été vérifié par signature Authenticode avant compilation.

## 4. Qualification Linux/WSL

Statut : **PASS**

```text
sha                      31212087ee5fab3c88b269d56f7f21402f31b683
versionCoherence         PASS — 1.1.0 across 17 POMs
git diff --check         PASS
reactor                  PASS — 17/17 modules SUCCESS
tests                    PASS — 603 / 603
architectureTests        PASS — 238 / 238
lineCoverage             PASS — 0.452246
branchCoverage           PASS — 0.384456
sqliteV012ToV015Upgrade  PASS
policyPacks              PASS
remoteServer             PASS
assistedReasoning        PASS
surfaceConvergence       PASS
packagedM25M26           PASS
packagedM27              PASS
sbom                     PASS
provenance               PASS
portableLinux            PASS
installer                NOT_APPLICABLE
postGateExecutableDelta  NONE
result                   R2 VALIDATION PASS
```

## 5. Parité cross-platform

```text
Windows SHA              31212087ee5fab3c88b269d56f7f21402f31b683
Linux/WSL SHA            31212087ee5fab3c88b269d56f7f21402f31b683
same SHA                 PASS
Windows tests            603 PASS
Linux/WSL tests          603 PASS
Windows architecture     238 PASS
Linux/WSL architecture   238 PASS
branch coverage          0.384456 on both platforms
post-gate delta          NONE on both platforms
```

La faible différence de couverture ligne (`0.452226` Windows, `0.452246` Linux/WSL) reste au-dessus du seuil et n’affecte aucun gate.

## 6. Upgrade SQLite 1.0.0 → 1.1.0

`R2UpgradeCompatibilityTest` est PASS sur les deux plateformes et couvre :

- la baseline V001→V012 ;
- l’application unique de V013, V014 et V015 ;
- la préservation des identités et snapshots publiés ;
- l’immutabilité des noms et checksums historiques ;
- le redémarrage idempotent.

## 7. Merge et tag

```text
PR                      #114 MERGED
final PR head           7e97071f88a3eef0c275708e12f6ff3cc0ce8c25
main merge commit       31506029ded1101f0571edeb0d79c59bbf3f68c6
tag                     v1.1.0
tag target              31506029ded1101f0571edeb0d79c59bbf3f68c6
```

## 8. Builds exact-tag

Statut : **PASS Windows + Linux/WSL**

| Asset | Bytes | SHA-256 | Statut |
|---|---:|---|---|
| `MORPHEUS-1.1.0-windows-x64-setup.exe` | 33,226,658 | `099d09c7cdce4c278ec227d51e5b1e12efd8a6d9ee5de2700aa551c86cf58dad` | PASS |
| `MORPHEUS-1.1.0-windows-x64-setup.exe.sha256` | 102 | `47cee3b166d7d9e2b6e3ea65d2af0f8f9294298d3db671cbcbf11dfa9420d6d8` | PASS |
| `morpheus-1.1.0-windows-x64.zip` | 37,639,166 | `ca967df2ca682954c915fb6e2e4f656534b113a398e449a55a36388e10754e83` | PASS |
| `morpheus-1.1.0-windows-x64.zip.sha256` | 96 | `0ac5b78f90deebdd2cda34a5fb16f78b6792257c55b4d66bbb403c7ea28d4676` | PASS |
| `morpheus-1.1.0-windows-x64-release-manifest.json` | 1,160 | `8c0a7b108456250c60751d795f4a2b402d95c264d0e9b5ccf553af62ea61ca16` | PASS |
| `morpheus-1.1.0-linux-x64.tar.gz` | 40,469,026 | `3e3bcff3760b8838a318bf1fe0fdf5898cbae7fa3699500f8a30032b5829071c` | PASS |
| `morpheus-1.1.0-linux-x64.tar.gz.sha256` | 98 | `0e99773f3bb89d49cce7ba5741f477c33162246c26d723b1ac6b19740ecdc4da` | PASS |
| `morpheus-1.1.0-linux-x64-release-manifest.json` | 766 | `a62c3e95082419044ea824976afdd0bbb63974e096ad20bc495aaebf171eff92` | PASS |

Les deux manifestes contiennent :

```text
tag      v1.1.0
gitSha   31506029ded1101f0571edeb0d79c59bbf3f68c6
```

## 9. GitHub Release

```text
tagName       v1.1.0
name          MORPHEUS 1.1.0
isDraft       false
isPrerelease  false
publishedAt   2026-07-30T14:13:17Z
assets        8/8 uploaded
```

Les huit assets ont été retéléchargés avec `gh release download`, puis comparés aux fichiers locaux avec SHA-256.

```text
published asset parity  8/8 PASS
```

Les digests GitHub des trois charges utiles correspondent aux preuves locales :

```text
Windows setup  099d09c7cdce4c278ec227d51e5b1e12efd8a6d9ee5de2700aa551c86cf58dad
Windows ZIP    ca967df2ca682954c915fb6e2e4f656534b113a398e449a55a36388e10754e83
Linux TAR.GZ   3e3bcff3760b8838a318bf1fe0fdf5898cbae7fa3699500f8a30032b5829071c
```

## 10. Incidents conservés

Les quatre premières tentatives Windows restent enregistrées comme **FAIL** :

1. `3db57b3...` — espaces de fin de ligne ;
2. `c500d70...` — manifeste application figé à `1.0.1` ;
3. `24e3b0b...` — manifeste CLI figé à `1.0.1` ;
4. `34b8955...` — chemin du JAR provider figé à `morpheus-provider-reference-1.0.0.jar`.

Elles ne remplacent pas la preuve finale PASS.

## 11. Résultat final

```text
executable candidate              c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
qualified exact head              31212087ee5fab3c88b269d56f7f21402f31b683
Windows exact-head gate           PASS
Linux/WSL exact-head gate         PASS
same SHA cross-platform           PASS
upgrade 1.0.0 -> 1.1.0            PASS Windows + Linux/WSL
post-gate executable delta        NONE
PR #114                           MERGED
main merge commit                 31506029ded1101f0571edeb0d79c59bbf3f68c6
tag v1.1.0                        PUBLISHED
exact-tag builds                  PASS Windows + Linux
GitHub Release                    STABLE / 8 ASSETS
published asset parity            8/8 PASS
Result                            R2 COMPLETE
```

**MORPHEUS 1.1.0 est publié, qualifié et vérifié.**
