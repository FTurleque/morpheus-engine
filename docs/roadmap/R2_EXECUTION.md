# R2 — Stabilisation et publication MORPHEUS 1.1.0

Statut : **PRÉPARATION TECHNIQUE TERMINÉE — WINDOWS GATE FAILED — CORRECTIF POUSSÉ — REQUALIFICATION REQUISE**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #113 OPEN
PR                     #114 DRAFT vers main
Branch                 r2-release-1.1.0
Release baseline       develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate   c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
Target version         1.1.0
Target tag             v1.1.0
```

## 1. Question de sortie

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

**Réponse courante : non démontrée.** Aucun PASS, merge, tag ou publication n'est autorisé avant les sorties réelles Windows et Linux/WSL sur le même SHA exact.

## 2. Baseline

```text
main                    0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop                 bccc118dda6fd818cf801750187afa4ad10b96e4
main...develop          188 commits ahead / 0 behind
release branch base     bccc118dda6fd818cf801750187afa4ad10b96e4
R2 executable candidate c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1
develop...candidate     48 commits / 38 changed files
published release       v1.0.0
candidate release       v1.1.0
```

M27 reste le dernier jalon déjà qualifié :

```text
qualified code SHA      f97307c878125550693699124ca717f64f305a3a
tests                   602 PASS Windows + Linux
architecture            238 PASS Windows + Linux
```

R2 modifie les POM, les contrats de version, le packaging, les validateurs et les tests. Une nouvelle qualification complète reste obligatoire.

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

## 4. Préparation réalisée

### R2-S0 — Gouvernance

- [x] audit de `main`, `develop`, issues et PR ;
- [x] issue obsolète #102 fermée comme doublon de #103 ;
- [x] issue R2 #113 créée et assignée ;
- [x] branche `r2-release-1.1.0` créée depuis le head exact de `develop` ;
- [x] PR draft #114 ouverte vers `main` ;
- [x] roadmaps, statut documentaire et index réconciliés ;
- [x] politique CI de juillet préservée ;
- [x] aucun fichier `.github/workflows` modifié.

### R2-S1 — Version 1.1.0

- [x] POM racine et seize POM enfants alignés en `1.1.0` ;
- [x] test de contrat exigeant exactement 17 POM cohérents ;
- [x] rejet explicite de toute version reactor `1.0.0` résiduelle ;
- [x] builders portable, installer et release Windows/Linux en `1.1.0` ;
- [x] cohérence des 17 POM observée PASS sur les quatre tentatives Windows ;
- [x] scénarios application et CLI d'update-check rendus indépendants de la version courante ;
- [x] chemin du JAR de plugin externe M22 rendu indépendant de la version courante ;
- [ ] cohérence réellement exécutée sous Linux/WSL.

### R2-S2 — Upgrade SQLite

- [x] fixture compatible avec la baseline 1.0.0/V012 ;
- [x] migrations V001→V012 appliquées avec noms et checksums canoniques ;
- [x] projet et snapshot ACTIVE représentatifs insérés ;
- [x] application de V013, V014 et V015 couverte ;
- [x] préservation des identités, historique et checksums couverte ;
- [x] replay idempotent couvert ;
- [x] tentatives 3 et 4 : `R2UpgradeCompatibilityTest` PASS sur des SHA désormais remplacés ;
- [ ] scénario reproduit sur le nouveau candidat Windows ;
- [ ] scénario exécuté sous Linux/WSL.

### R2-S3 — Packaging

- [x] defaults des builders actifs en `1.1.0` ;
- [x] runtime Java embarqué conservé ;
- [x] smokes version/product-info/API conservés ;
- [x] preuves packagées M25, M26 et M27 intégrées au gate ;
- [x] setup Windows et checksum intégrés ;
- [ ] distributions réellement construites sous Windows et Linux ;
- [ ] setup Windows réellement construit ;
- [ ] hashes et manifestes exact-tag réellement produits.

### R2-S4 — Documentation de release

- [x] notes de version candidates M21→M27 ;
- [x] guide d'upgrade 1.0.0→1.1.0 ;
- [x] procédure de backup/restore offline ;
- [x] distinction candidate / release stable explicite ;
- [x] les quatre échecs Windows enregistrés factuellement ;
- [ ] valeurs finales de qualification et d'artefacts à injecter après observation.

## 5. Incidents de qualification Windows

### Tentative 1 — `3db57b33960ef16af9f3b6e49fc247e3bf843efb`

`git diff --check` a rejeté trois espaces de fin de ligne dans `docs/governance/ROADMAP.md`. Maven n'a pas démarré. Correction : `c500d70ec7ee0ad2acfcbd4c4a49346c7c93f975`.

### Tentative 2 — `c500d70ec7ee0ad2acfcbd4c4a49346c7c93f975`

Le root reactor et `morpheus-domain` ont réussi, puis `morpheus-application` a échoué sur `ProductIntegrityTest.explicitFileManifestCanReportANewerVersion`. Le manifeste figé à `1.0.1` n'était plus supérieur à `1.1.0`. Correction : `aef3ed8a65397e7ca2fa5aa6abdf41237025605a`.

### Tentative 3 — `24e3b0b66cb80388ffb687fd470237174e083121`

Le gate a atteint 15 modules SUCCESS sur 17. L'application, SQLite, MCP et API ont réussi ; `R2UpgradeCompatibilityTest` a prouvé l'upgrade V012→V015. Le module CLI a échoué sur un second manifeste figé à `1.0.1`. L'architecture et le packaging n'ont pas été exécutés. Correction : `43dc9cfb78b8b40276b3eee8a05ec828660f88b4`.

### Tentative 4 — `34b8955a74270ded0b5464196a45eff746085168`

Le gate a exécuté **603 tests** et atteint le dernier module :

```text
reactor                 16 SUCCESS / architecture FAILURE
non-architecture tests  365 PASS
architecture tests      238 run / 1 failure
failing contract        ProviderPluginPlatformContractTest.externalReferenceJarIsDiscoveredActivatedInDedicatedLoaderProbedAndRead
expected path           morpheus-provider-reference-1.0.0.jar
built path              morpheus-provider-reference-1.1.0.jar
```

Le CLI est désormais entièrement PASS avec 53 tests. L'upgrade V012→V015 est également PASS sur cette tentative. Le packaging n'a pas été exécuté puisque Maven a échoué dans les tests d'architecture.

Correction : `c206e1bdb8e98df2e6d74f1fb3b151e0bba812e1`. Le contrat M22 compose désormais le nom du JAR depuis `ProductMetadata.version()`, alimenté par `${project.version}` dans Surefire.

Les quatre tentatives restent **FAIL** et ne sont pas transformées en PASS par les correctifs.

## 6. Gates exact-head

### Windows

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Le gate doit produire au minimum :

```text
reactor 17/17 SUCCESS
tests >= 603
architecture >= 238
coverage >= M27 thresholds
SQLite V012 -> V015 upgrade PASS
Policy Packs PASS
remote TLS/auth/RBAC/backup PASS
assisted reasoning facts/claims/no-mutation PASS
CLI/MCP/HTTP convergence PASS
packaged M25/M26/M27 PASS
Windows portable PASS
Windows setup + SHA-256 PASS
CycloneDX/provenance PASS
postGateExecutableDelta=NONE
```

### Linux/WSL

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

Le SHA doit être strictement identique au SHA Windows.

## 7. Consolidation post-gate

Après les deux PASS réels :

- [ ] inscrire les sorties exactes dans `VALIDATION_R2.md` ;
- [ ] accepter uniquement des commits documentaires ;
- [ ] comparer le SHA qualifié au head PR final ;
- [ ] exiger `postGateExecutableDelta=NONE` ;
- [ ] vérifier les review threads ;
- [ ] marquer la PR Ready.

## 8. Merge, tag et publication

Le merge dans `main` reste interdit tant que la qualification n'est pas complète.

Après autorisation :

- [ ] merge de #114 avec `expected_head_sha` ;
- [ ] contrôle du SHA de merge/stabilisation ;
- [ ] création de `v1.1.0` sur le SHA autorisé ;
- [ ] build exact-tag Windows et Linux ;
- [ ] publication des huit assets ;
- [ ] comparaison des digests GitHub aux preuves locales ;
- [ ] documentation post-release réconciliée ;
- [ ] issue #113 fermée `completed`.

## 9. Workspace local

Le répertoire non suivi `dist-r1/` n'a causé aucun des échecs : le gate R2 contrôle les deltas suivis. Les scripts exact-tag exigent cependant un workspace intégralement propre ; ce répertoire devra être déplacé ou supprimé avant la publication.

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
Windows exact-head             FAILED / RERUN REQUIRED
Linux/WSL exact-head           NOT RUN
same SHA cross-platform        NOT PROVEN
merge main                     NOT AUTHORIZED
tag v1.1.0                     NOT AUTHORIZED
GitHub Release                 NOT CREATED
Result                         R2 IN PROGRESS
```

**Aucun PASS R2 n'est déclaré à ce stade.**
