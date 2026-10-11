# R2 — Stabilisation et publication MORPHEUS 1.1.0

Statut : **TERMINÉ — MORPHEUS 1.1.0 PUBLIÉ**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #113 CLOSED / completed
PR                     #114 MERGED vers main
Branch                 r2-release-1.1.0
Release baseline       develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate   c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
Final documentary head 7e97071f88a3eef0c275708e12f6ff3cc0ce8c25
Main merge commit      31506029ded1101f0571edeb0d79c59bbf3f68c6
Published version      1.1.0
Published tag          v1.1.0
GitHub Release         stable / 8 assets
```

## 1. Question de sortie

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

**Réponse finale : oui.**

MORPHEUS 1.1.0 a été qualifié sous Windows et Linux/WSL, mergé dans `main`, tagué sur le commit autorisé, reconstruit depuis le tag exact puis publié comme GitHub Release stable avec huit assets vérifiés.

## 2. Chaîne d’autorité

```text
qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
final PR docs head     7e97071f88a3eef0c275708e12f6ff3cc0ce8c25
main merge commit      31506029ded1101f0571edeb0d79c59bbf3f68c6
tag                    v1.1.0 -> 31506029ded1101f0571edeb0d79c59bbf3f68c6
release published      2026-07-30T14:13:17Z
```

Le delta post-gate entre le SHA qualifié et le head final de PR est strictement documentaire. Aucun exécutable n’a été modifié après qualification.

## 3. Invariants démontrés

```text
release tag != development branch
qualified SHA == packaged candidate SHA
Windows qualified SHA == Linux qualified SHA
post-gate executable delta == NONE
main is stabilization / delivery branch
develop remains integration branch
upgrade preserves identities and published facts
migration history remains immutable
facts != inference
local mode remains first-class
remote mode remains opt-in
checksum != signature
published assets == locally verified assets
```

## 4. Qualification finale exact-head

### Windows

```text
sha                      31212087ee5fab3c88b269d56f7f21402f31b683
reactor                  17/17 SUCCESS
tests                    603/603 PASS
architectureTests        238/238 PASS
lineCoverage             0.452226
branchCoverage           0.384456
sqliteV012ToV015Upgrade  PASS
policyPacks              PASS
remoteServer             PASS
assistedReasoning        PASS
surfaceConvergence       PASS
packagedM25M26           PASS
packagedM27              PASS
portableWindows          PASS
installerWindows         PASS
sbom                     PASS
provenance               PASS
postGateExecutableDelta  NONE
result                   R2 VALIDATION PASS
```

### Linux/WSL

```text
sha                      31212087ee5fab3c88b269d56f7f21402f31b683
reactor                  17/17 SUCCESS
tests                    603/603 PASS
architectureTests        238/238 PASS
lineCoverage             0.452246
branchCoverage           0.384456
sqliteV012ToV015Upgrade  PASS
policyPacks              PASS
remoteServer             PASS
assistedReasoning        PASS
surfaceConvergence       PASS
packagedM25M26           PASS
packagedM27              PASS
portableLinux            PASS
installer                NOT_APPLICABLE
sbom                     PASS
provenance               PASS
postGateExecutableDelta  NONE
result                   R2 VALIDATION PASS
```

### Parité

```text
same SHA                 PASS
same test count          PASS
same architecture count  PASS
branch coverage parity   PASS
```

## 5. Merge et tag

```text
PR                     #114 MERGED
merge commit           31506029ded1101f0571edeb0d79c59bbf3f68c6
tag                    v1.1.0
tag target             31506029ded1101f0571edeb0d79c59bbf3f68c6
```

Le tag `v1.1.0` est immuable et ne doit pas être déplacé après publication.

## 6. Builds exact-tag

Les builds de publication ont été exécutés depuis `v1.1.0` sur Windows et Linux/WSL.

### Windows

```text
MORPHEUS-1.1.0-windows-x64-setup.exe          33,226,658 bytes
morpheus-1.1.0-windows-x64.zip                37,639,166 bytes
morpheus-1.1.0-windows-x64-release-manifest.json 1,160 bytes
```

### Linux

```text
morpheus-1.1.0-linux-x64.tar.gz                40,469,026 bytes
morpheus-1.1.0-linux-x64-release-manifest.json        766 bytes
```

Les trois charges utiles ont passé la vérification SHA-256 locale :

```text
Windows setup  099d09c7cdce4c278ec227d51e5b1e12efd8a6d9ee5de2700aa551c86cf58dad
Windows ZIP    ca967df2ca682954c915fb6e2e4f656534b113a398e449a55a36388e10754e83
Linux TAR.GZ   3e3bcff3760b8838a318bf1fe0fdf5898cbae7fa3699500f8a30032b5829071c
```

Les deux manifestes contiennent le tag `v1.1.0` et le SHA `31506029ded1101f0571edeb0d79c59bbf3f68c6`.

## 7. GitHub Release

```text
tagName       v1.1.0
name          MORPHEUS 1.1.0
isDraft       false
isPrerelease  false
publishedAt   2026-07-30T14:13:17Z
assets        8/8 uploaded
```

Les huit assets publiés ont été retéléchargés puis comparés aux fichiers locaux. Les huit comparaisons SHA-256 sont **PASS**.

## 8. Incidents conservés

Les premières tentatives Windows restent enregistrées comme FAIL :

1. `3db57b3...` — espaces de fin de ligne documentaires ;
2. `c500d70...` — manifeste application figé à `1.0.1` ;
3. `24e3b0b...` — manifeste CLI figé à `1.0.1` ;
4. `34b8955...` — chemin provider figé à `morpheus-provider-reference-1.0.0.jar`.

Elles ne remplacent pas la preuve finale PASS.

## 9. Politique CI — juillet 2026

```text
GitHub Actions is not a release gate
no workflow rerun
no workflow_dispatch
no opportunistic .github/workflows change
local Windows + Linux/WSL exact-head logs are authoritative
```

## 10. Résultat final

```text
Preparation                    COMPLETE
Windows exact-head             PASS
Linux/WSL exact-head           PASS
same SHA cross-platform        PASS
post-gate executable delta     NONE
PR #114                        MERGED
main                           31506029ded1101f0571edeb0d79c59bbf3f68c6
tag v1.1.0                     PUBLISHED
exact-tag builds               PASS Windows + Linux
GitHub Release                 STABLE / 8 ASSETS
published asset parity         8/8 PASS
Result                         R2 COMPLETE
```

Suite produit : [`#115 — R3 / MORPHEUS 1.2.0 — MCP Client Integration & Installer Wiring`](https://github.com/FTurleque/morpheus-engine/issues/115).
