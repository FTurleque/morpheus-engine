# R2 — Stabilisation et publication MORPHEUS 1.1.0

Statut : **PRÉPARATION TECHNIQUE TERMINÉE — QUALIFICATION EXACT-HEAD NON EXÉCUTÉE**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #113 OPEN
PR                     #114 DRAFT vers main
Branch                 r2-release-1.1.0
Release baseline       develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Executable candidate   cde78c8172d720a01254f7463f4ff60d09a8b677
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
R2 executable candidate cde78c8172d720a01254f7463f4ff60d09a8b677
develop...candidate     38 commits / 35 changed files
published release       v1.0.0
candidate release       v1.1.0
```

M27 reste le dernier jalon déjà qualifié :

```text
qualified code SHA      f97307c878125550693699124ca717f64f305a3a
tests                   602 PASS Windows + Linux
architecture            238 PASS Windows + Linux
```

R2 modifie les POM, les contrats de version, le packaging, les validateurs et les tests. Une nouvelle qualification est donc obligatoire.

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

- [x] POM racine en `1.1.0` ;
- [x] seize POM enfants alignés sur le parent `1.1.0` ;
- [x] test de contrat exigeant exactement 17 POM cohérents ;
- [x] rejet explicite de toute version reactor `1.0.0` résiduelle ;
- [x] builders portable, installer et release Windows/Linux par défaut en `1.1.0` ;
- [x] version passée explicitement aux outils de packaging ;
- [ ] cohérence réellement exécutée sous Windows ;
- [ ] cohérence réellement exécutée sous Linux/WSL.

### R2-S2 — Upgrade SQLite

- [x] fixture de base compatible avec la baseline 1.0.0/V012 ;
- [x] migrations V001→V012 appliquées avec noms et checksums canoniques ;
- [x] projet et snapshot ACTIVE représentatifs insérés ;
- [x] test prévu pour appliquer V013, V014 et V015 ;
- [x] vérification prévue de la préservation des identités et de l'historique ;
- [x] vérification prévue de l'immutabilité des checksums V001→V012 ;
- [x] vérification prévue du replay idempotent ;
- [x] backup et rollback offline documentés ;
- [ ] scénario réellement exécuté sous Windows ;
- [ ] scénario réellement exécuté sous Linux/WSL.

### R2-S3 — Packaging

- [x] defaults des builders actifs en `1.1.0` ;
- [x] runtime Java embarqué conservé ;
- [x] smokes version/product-info/API conservés ;
- [x] preuve packagée M25 Policy Packs ajoutée au gate R2 ;
- [x] preuve packagée M26 remote/server ajoutée au gate R2 ;
- [x] preuve packagée M27 reasoning héritée du gate M27 ;
- [x] setup Windows et checksum intégrés au gate R2 ;
- [x] scripts exact-tag et manifestes réutilisés ;
- [ ] distributions réellement construites sous Windows et Linux ;
- [ ] setup Windows réellement construit ;
- [ ] hashes et manifestes exact-tag réellement produits.

### R2-S4 — Documentation de release

- [x] notes de version candidates M21→M27 ;
- [x] guide d'upgrade 1.0.0→1.1.0 ;
- [x] procédure de backup/restore offline ;
- [x] distinction candidate / release stable explicite ;
- [x] `v1.0.0` reste la seule release annoncée comme publiée ;
- [ ] valeurs finales de qualification et d'artefacts à injecter après observation.

## 5. Gates exact-head

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

## 6. Consolidation post-gate

Après les deux PASS réels :

- [ ] inscrire les sorties exactes dans `VALIDATION_R2.md` ;
- [ ] accepter uniquement des commits documentaires ;
- [ ] comparer le SHA qualifié au head PR final ;
- [ ] exiger `postGateExecutableDelta=NONE` ;
- [ ] vérifier les review threads ;
- [ ] marquer la PR Ready.

## 7. Merge, tag et publication

Le merge dans `main` reste interdit tant que la qualification n'est pas complète.

Après autorisation :

- [ ] merge de #114 avec `expected_head_sha` ;
- [ ] contrôle du SHA de merge/stabilisation ;
- [ ] création de `v1.1.0` sur le SHA autorisé ;
- [ ] compare tag/SHA identique ;
- [ ] build exact-tag Windows :

```powershell
.\distribution\build-release.ps1 -Version 1.1.0 -ExpectedTag v1.1.0
```

- [ ] build exact-tag Linux :

```bash
bash ./distribution/build-release.sh 1.1.0 v1.1.0
```

Assets attendus :

```text
MORPHEUS-1.1.0-windows-x64-setup.exe
MORPHEUS-1.1.0-windows-x64-setup.exe.sha256
morpheus-1.1.0-windows-x64.zip
morpheus-1.1.0-windows-x64.zip.sha256
morpheus-1.1.0-linux-x64.tar.gz
morpheus-1.1.0-linux-x64.tar.gz.sha256
morpheus-1.1.0-windows-x64-release-manifest.json
morpheus-1.1.0-linux-x64-release-manifest.json
```

- [ ] GitHub Release stable, non draft, non prerelease ;
- [ ] 8/8 assets publiés ;
- [ ] digests GitHub comparés aux preuves locales ;
- [ ] documentation post-release réconciliée ;
- [ ] issue #113 fermée `completed`.

## 8. Politique CI — juillet 2026

```text
GitHub Actions is not a release gate
no workflow rerun
no workflow_dispatch
no opportunistic .github/workflows change
local Windows + Linux/WSL exact-head logs are authoritative
```

## 9. État courant

```text
Preparation                    COMPLETE
Executable candidate           cde78c8172d720a01254f7463f4ff60d09a8b677
Windows exact-head             NOT RUN
Linux/WSL exact-head           NOT RUN
same SHA cross-platform        NOT PROVEN
merge main                     NOT AUTHORIZED
tag v1.1.0                     NOT AUTHORIZED
GitHub Release                 NOT CREATED
Result                         R2 IN PROGRESS
```

**Aucun PASS R2 n'est déclaré à ce stade.**