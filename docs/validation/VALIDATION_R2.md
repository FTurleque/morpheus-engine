# R2 — Validation MORPHEUS 1.1.0

Statut : **WINDOWS + LINUX/WSL EXACT-HEAD PASS — MERGE AUTORISABLE APRÈS CONTRÔLES PR**

Date : 30 juillet 2026

```text
Issue                   #113 OPEN
PR                      #114 DRAFT vers main
Branch                  r2-release-1.1.0
Main baseline           0e37d85fc7efe9843094416898b6fbdbc45b7da4
Develop baseline        bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate    c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Qualified exact head    31212087ee5fab3c88b269d56f7f21402f31b683
Target version          1.1.0
Target tag              v1.1.0
Stable published tag    v1.0.0
```

Plan : [`../roadmap/R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md).

## 1. Périmètre qualifié

La comparaison `develop...c206e1b` donne :

```text
status           ahead
commits          48
behind           0
changed files    38
```

Le candidat comprend :

- le reactor Maven complet en 1.1.0 ;
- les builders portable, installer et release en 1.1.0 ;
- le test de cohérence des 17 POM ;
- le test d'upgrade SQLite V012→V015 ;
- les validateurs R2 Windows et Linux/WSL ;
- les preuves packagées M25, M26 et M27 ;
- les scénarios application et CLI de découverte de mise à jour indépendants de la version courante ;
- le contrat M22 de plugin externe résolvant le JAR depuis `ProductMetadata.version()` ;
- les notes de version, le guide d'upgrade et la gouvernance R2.

Aucun fichier `.github/workflows` n'est modifié.

Le delta `c206e1b...31212087` contient uniquement les deux fichiers documentaires R2 :

```text
docs/roadmap/R2_EXECUTION.md
docs/validation/VALIDATION_R2.md
```

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
portableWindows          PASS — 37,639,254 bytes
installerWindows         PASS — setup + SHA-256
postGateExecutableDelta  NONE
result                   R2 VALIDATION PASS
```

Le bootstrap Inno Setup 7.0.2 a été vérifié par signature Authenticode avant compilation. Les artefacts de validation suivants ont été produits :

```text
validation-output/m27/dist/morpheus-1.1.0-windows-x64.zip
validation-output/m27/dist/MORPHEUS-1.1.0-windows-x64-setup.exe
validation-output/m27/dist/MORPHEUS-1.1.0-windows-x64-setup.exe.sha256
```

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

Artefact de validation produit :

```text
validation-output/m27/dist/morpheus-1.1.0-linux-x64.tar.gz
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

La faible différence de couverture ligne (`0.452226` Windows, `0.452246` Linux/WSL) reste au-dessus du seuil M27 et n'affecte aucun gate.

## 6. Upgrade SQLite 1.0.0 → 1.1.0

`R2UpgradeCompatibilityTest` est PASS sur les deux plateformes et couvre :

- la baseline V001→V012 ;
- l'application unique de V013, V014 et V015 ;
- la préservation des identités et snapshots publiés ;
- l'immutabilité des noms et checksums historiques ;
- le redémarrage idempotent.

```text
Windows                 PASS
Linux/WSL               PASS
qualified SHA           31212087ee5fab3c88b269d56f7f21402f31b683
```

## 7. Incidents Windows conservés

Les quatre premières tentatives restent enregistrées comme **FAIL** :

1. `3db57b3...` — `git diff --check` rejeté par des espaces de fin de ligne ;
2. `c500d70...` — manifeste application figé à `1.0.1` ;
3. `24e3b0b...` — manifeste CLI figé à `1.0.1` ;
4. `34b8955...` — chemin du JAR provider figé à `morpheus-provider-reference-1.0.0.jar`.

Corrections exécutables successives :

```text
aef3ed8a65397e7ca2fa5aa6abdf41237025605a
43dc9cfb78b8b40276b3eee8a05ec828660f88b4
c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
```

La cinquième tentative Windows et la première tentative Linux/WSL sont les seules preuves PASS finales.

## 8. Workspace local

Le répertoire non suivi suivant a été observé sur le workspace partagé :

```text
dist-r1/
```

Il n'a pas affecté les gates exact-head, qui contrôlent les deltas suivis. Les builds exact-tag exigent un workspace intégralement propre ; ce répertoire devra être déplacé ou supprimé avant la construction des artefacts de publication.

## 9. Exact-tag et artefacts de publication

Statut : **AUTORISABLE APRÈS MERGE ET CRÉATION DU TAG — NON ENCORE EXÉCUTÉ**

| Asset | Bytes | SHA-256 | Statut |
|---|---:|---|---|
| `MORPHEUS-1.1.0-windows-x64-setup.exe` | — | — | exact-tag NOT BUILT |
| `MORPHEUS-1.1.0-windows-x64-setup.exe.sha256` | — | — | exact-tag NOT BUILT |
| `morpheus-1.1.0-windows-x64.zip` | — | — | exact-tag NOT BUILT |
| `morpheus-1.1.0-windows-x64.zip.sha256` | — | — | exact-tag NOT BUILT |
| `morpheus-1.1.0-linux-x64.tar.gz` | — | — | exact-tag NOT BUILT |
| `morpheus-1.1.0-linux-x64.tar.gz.sha256` | — | — | exact-tag NOT BUILT |
| Windows release manifest | — | — | NOT BUILT |
| Linux release manifest | — | — | NOT BUILT |

Les artefacts créés pendant les gates prouvent le packaging, mais ne remplacent pas les builds exact-tag de publication.

## 10. GitHub Release

```text
tagName       v1.1.0
isDraft       expected false
isPrerelease  expected false
assets        expected 8/8
status        NOT CREATED
```

## 11. Résultat courant

```text
executable candidate              c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
qualified exact head              31212087ee5fab3c88b269d56f7f21402f31b683
Windows exact-head gate           PASS
Linux/WSL exact-head gate         PASS
same SHA cross-platform           PASS
upgrade 1.0.0 -> 1.1.0            PASS Windows + Linux/WSL
post-gate executable delta        NONE on both platforms
PR ready                          PENDING DOCUMENTARY RECONCILIATION / REVIEW CHECK
merge main                        AUTHORIZABLE AFTER FINAL PR CHECKS
tag v1.1.0                        NOT CREATED
exact-tag builds                  NOT RUN
GitHub Release                    NOT CREATED
Result                            R2 QUALIFIED — DELIVERY PENDING
```

**Les preuves de qualification fonctionnelle sont complètes sur le même SHA exact. Les opérations restantes concernent la consolidation documentaire, la revue PR, le merge, le tag, les builds exact-tag et la publication.**