# R2 — Stabilisation et publication MORPHEUS 1.1.0

Statut : **QUALIFICATION WINDOWS + LINUX/WSL TERMINÉE — LIVRAISON EN COURS**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #113 OPEN
PR                     #114 vers main
Branch                 r2-release-1.1.0
Release baseline       develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate   c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
Target version         1.1.0
Target tag             v1.1.0
```

## 1. Question de sortie

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

**Réponse technique : oui.** Le même SHA exact a passé les gates Windows et Linux/WSL. La livraison reste à terminer : revue PR, merge, tag, builds exact-tag, publication et réconciliation post-release.

## 2. Baseline et candidat

```text
main                    0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop                 bccc118dda6fd818cf801750187afa4ad10b96e4
main...develop          188 commits ahead / 0 behind
release branch base     bccc118dda6fd818cf801750187afa4ad10b96e4
R2 executable candidate c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
develop...candidate     48 commits / 38 changed files
qualified exact head    31212087ee5fab3c88b269d56f7f21402f31b683
published release       v1.0.0
candidate release       v1.1.0
```

Le delta entre le dernier commit exécutable et le SHA qualifié contient uniquement :

```text
docs/roadmap/R2_EXECUTION.md
docs/validation/VALIDATION_R2.md
```

## 3. Invariants

```text
release tag != development branch
qualified SHA == packaged SHA
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
release publication != automatic update
```

Tous les invariants de qualification sont démontrés sur `31212087...`.

## 4. Préparation réalisée

### R2-S0 — Gouvernance

- [x] audit de `main`, `develop`, issues et PR ;
- [x] issue obsolète #102 fermée comme doublon de #103 ;
- [x] issue R2 #113 créée et assignée ;
- [x] branche `r2-release-1.1.0` créée depuis le head exact de `develop` ;
- [x] PR #114 ouverte vers `main` ;
- [x] roadmaps, statut documentaire et index réconciliés ;
- [x] politique CI de juillet préservée ;
- [x] aucun fichier `.github/workflows` modifié.

### R2-S1 — Version 1.1.0

- [x] POM racine et seize POM enfants alignés en `1.1.0` ;
- [x] test de contrat exigeant exactement 17 POM cohérents ;
- [x] rejet explicite de toute version reactor `1.0.0` résiduelle ;
- [x] builders portable, installer et release Windows/Linux en `1.1.0` ;
- [x] scénarios application et CLI d'update-check indépendants de la version courante ;
- [x] chemin du JAR de plugin externe M22 indépendant de la version courante ;
- [x] cohérence des 17 POM PASS sous Windows ;
- [x] cohérence des 17 POM PASS sous Linux/WSL.

### R2-S2 — Upgrade SQLite

- [x] fixture compatible avec la baseline 1.0.0/V012 ;
- [x] migrations V001→V012 appliquées avec noms et checksums canoniques ;
- [x] projet et snapshot ACTIVE représentatifs insérés ;
- [x] application unique de V013, V014 et V015 ;
- [x] préservation des identités, historique et checksums ;
- [x] replay idempotent ;
- [x] scénario PASS sous Windows sur `31212087...` ;
- [x] scénario PASS sous Linux/WSL sur `31212087...`.

### R2-S3 — Packaging

- [x] defaults des builders actifs en `1.1.0` ;
- [x] runtime Java embarqué conservé ;
- [x] smokes version/product-info/API ;
- [x] preuves packagées M25, M26 et M27 ;
- [x] distribution portable Windows PASS ;
- [x] distribution portable Linux PASS ;
- [x] setup Windows et checksum PASS ;
- [x] SBOM CycloneDX et provenance PASS sur les deux plateformes ;
- [ ] hashes et manifestes exact-tag produits après création du tag.

### R2-S4 — Documentation de release

- [x] notes de version candidates M21→M27 ;
- [x] guide d'upgrade 1.0.0→1.1.0 ;
- [x] procédure de backup/restore offline ;
- [x] distinction candidate / release stable explicite ;
- [x] quatre échecs Windows enregistrés factuellement ;
- [x] valeurs finales Windows et Linux/WSL inscrites dans `VALIDATION_R2.md` ;
- [ ] valeurs exact-tag et GitHub Release à injecter après publication.

## 5. Qualification finale

### Windows

```text
sha                      31212087ee5fab3c88b269d56f7f21402f31b683
reactor                  17/17 SUCCESS
tests                    603/603 PASS
architectureTests        238/238 PASS
lineCoverage             0.452226
branchCoverage           0.384456
sqliteV012ToV015Upgrade  PASS
packagedM25M26           PASS
packagedM27              PASS
portableWindows          PASS — 37,639,254 bytes
installerWindows         PASS — setup + SHA-256
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
Windows qualified SHA    31212087ee5fab3c88b269d56f7f21402f31b683
Linux qualified SHA      31212087ee5fab3c88b269d56f7f21402f31b683
same SHA                 PASS
same test count          PASS
same architecture count  PASS
branch coverage parity   PASS
```

## 6. Incidents Windows conservés

1. `3db57b3...` — whitespace documentaire ;
2. `c500d70...` — manifeste application figé à `1.0.1` ;
3. `24e3b0b...` — manifeste CLI figé à `1.0.1` ;
4. `34b8955...` — chemin provider figé à `morpheus-provider-reference-1.0.0.jar`.

Les correctifs ont convergé vers le candidat exécutable `c206e1b...`. Ces tentatives restent FAIL et ne remplacent pas la preuve finale.

## 7. Consolidation post-gate

- [x] inscrire les sorties exactes dans `VALIDATION_R2.md` ;
- [x] n'ajouter que des commits documentaires après qualification ;
- [ ] comparer le SHA qualifié au head PR final ;
- [ ] confirmer `postGateExecutableDelta=NONE` entre le code qualifié et le head final ;
- [ ] vérifier les review threads ;
- [ ] marquer la PR Ready ;
- [ ] merger #114 avec `expected_head_sha`.

## 8. Tag, builds exact-tag et publication

Après le merge autorisé :

- [ ] contrôler le SHA de merge/stabilisation ;
- [ ] créer `v1.1.0` sur le SHA autorisé ;
- [ ] déplacer ou supprimer `dist-r1/` pour obtenir un workspace totalement propre ;
- [ ] exécuter le build exact-tag Windows ;
- [ ] exécuter le build exact-tag Linux ;
- [ ] produire les huit assets attendus ;
- [ ] publier la GitHub Release stable ;
- [ ] comparer les digests GitHub aux preuves locales ;
- [ ] réconcilier la documentation post-release ;
- [ ] fermer l'issue #113 avec la raison `completed`.

Commandes exact-tag prévues :

```powershell
.\distribution\build-release.ps1 -Version 1.1.0 -ExpectedTag v1.1.0
```

```bash
bash ./distribution/build-release.sh 1.1.0 v1.1.0
```

## 9. Workspace local

`dist-r1/` est non suivi. Il n'a affecté aucun gate exact-head. Il doit être déplacé ou supprimé avant les builds exact-tag.

## 10. Politique CI — juillet 2026

```text
GitHub Actions is not a release gate
no workflow rerun
no workflow_dispatch
no opportunistic .github/workflows change
local Windows + Linux/WSL exact-head logs are authoritative
```

## 11. État courant

```text
Preparation                    COMPLETE
Executable candidate           c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Qualified exact head           31212087ee5fab3c88b269d56f7f21402f31b683
Windows exact-head             PASS
Linux/WSL exact-head           PASS
same SHA cross-platform        PASS
post-gate executable delta     NONE on qualified head
PR review                      PENDING
merge main                     AUTHORIZABLE AFTER FINAL PR CHECKS
tag v1.1.0                     NOT CREATED
GitHub Release                 NOT CREATED
Result                         R2 QUALIFIED — DELIVERY PENDING
```

**La qualification R2 est terminée. Les opérations restantes sont la consolidation PR, le merge, le tag, les builds exact-tag, la publication et la réconciliation finale.**