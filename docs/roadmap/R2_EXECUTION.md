# R2 — Stabilisation et publication MORPHEUS 1.1.0

Statut : **EN COURS — CADRAGE ET PR DRAFT OUVERTS, QUALIFICATION NON EXÉCUTÉE**

Dernière mise à jour : 30 juillet 2026

Issue : **#113**  
PR draft : **#114**  
Branche : **`r2-release-1.1.0`**

## 1. Question de sortie

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

Aucune réponse positive n'est autorisée avant la totalité des gates Windows, Linux/WSL, packaging, exact-tag et publication.

## 2. Baseline autoritative

```text
main                    0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop                 bccc118dda6fd818cf801750187afa4ad10b96e4
release branch          r2-release-1.1.0
release branch baseline bccc118dda6fd818cf801750187afa4ad10b96e4
main...develop          188 commits ahead / 0 behind
current release         v1.0.0
current Maven version   1.0.0
target release          v1.1.0
target Maven version    1.1.0
```

Le dernier SHA de code M27 qualifié est :

```text
f97307c878125550693699124ca717f64f305a3a
```

La réconciliation post-M27 a conduit `develop` à :

```text
bccc118dda6fd818cf801750187afa4ad10b96e4
```

Le delta M27 qualifié vers `develop` est exclusivement documentaire. R2 introduit cependant un changement exécutable de version et doit donc être qualifié sur son propre SHA exact.

## 3. Périmètre

### Inclus

- promotion contrôlée de M25, M26 et M27 vers `main` ;
- version Maven et packaging `1.1.0` ;
- compatibilité des données et migrations SQLite V001→V015 ;
- upgrade depuis une installation et une base `1.0.0` ;
- setup Windows per-user ;
- portable Windows x64 ;
- portable Linux x64 ;
- runtime Java embarqué ;
- hashes SHA-256 ;
- manifestes de release exacts ;
- CycloneDX JSON/XML ;
- provenance de build ;
- smoke tests packagés des surfaces M25/M26/M27 ;
- notes de version et guide d'upgrade ;
- tag `v1.1.0` et GitHub Release stable ;
- réconciliation documentaire post-merge et post-release.

### Exclus

- nouveau jalon fonctionnel M28 ;
- changement opportuniste des workflows GitHub Actions en juillet 2026 ;
- auto-update ;
- signature de code non cadrée ;
- service cloud obligatoire ;
- LLM obligatoire ;
- migration implicite ou destructive des données.

## 4. Invariants

```text
release tag != branche de développement
qualified SHA == packaged SHA
Windows qualified SHA == Linux qualified SHA
post-gate executable delta == NONE
main is stabilization / delivery branch
develop remains integration branch
release branch starts from develop exact head
upgrade preserves domain identities and published facts
migration is explicit and bounded
facts != inference
inference never overwrites published facts
local mode remains first-class
remote mode remains opt-in
checksum != signature
release publication != automatic update
```

## 5. Slices

### R2-S0 — Cadrage et gouvernance

- [x] audit de l'écart `main...develop` ;
- [x] fermeture de l'issue dupliquée #102 ;
- [x] création de l'issue #113 ;
- [x] création de `r2-release-1.1.0` depuis `develop@bccc118d...` ;
- [x] ouverture de la PR draft #114 vers `main` ;
- [x] plan d'exécution R2 ;
- [ ] index documentaire mis à jour.

### R2-S1 — Version produit 1.1.0

- [ ] root POM en `1.1.0` ;
- [ ] tous les parent POM en `1.1.0` ;
- [ ] valeurs par défaut des scripts de distribution en `1.1.0` ;
- [ ] tests de contrat produit mis à jour sans affaiblissement ;
- [ ] aucune référence active incohérente `1.0.0` dans le packaging ;
- [ ] la documentation historique R1 reste inchangée.

### R2-S2 — Upgrade et compatibilité

- [ ] création d'une base représentative avec MORPHEUS 1.0.0 ;
- [ ] ouverture et migration sous 1.1.0 ;
- [ ] V001→V015 appliquées une seule fois ;
- [ ] identités, snapshots, historiques et audit préservés ;
- [ ] policy packs et données remote compatibles ;
- [ ] backup avant upgrade documenté ;
- [ ] rollback applicatif documenté sans rollback destructif de schéma.

### R2-S3 — Gate exact-head Windows

Commande canonique :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Attendus : reactor 17/17, tests >= 602, architecture >= 238, couverture >= seuils M27, surfaces M25–M27, SQLite V015, portable, setup, SBOM, provenance et smokes packagés.

### R2-S4 — Gate exact-head Linux/WSL

Commande canonique :

```bash
./scripts/validate-r2.sh 1.1.0
```

Le SHA doit être strictement identique au SHA Windows.

### R2-S5 — Consolidation post-gate

- [ ] ADR/plan/validation finalisés ;
- [ ] release notes et guide d'upgrade finalisés ;
- [ ] delta depuis le SHA qualifié exclusivement documentaire ;
- [ ] PR #114 mise à jour avec preuves exactes ;
- [ ] aucun thread de review bloquant ;
- [ ] PR marquée Ready uniquement après ces contrôles.

### R2-S6 — Merge et exact-tag

- [ ] merge dans `main` avec `expected_head_sha` ;
- [ ] contrôle du SHA de merge ;
- [ ] absence de delta exécutable inattendu ;
- [ ] tag stable `v1.1.0` créé sur le SHA autorisé ;
- [ ] compare tag/SHA = identical.

### R2-S7 — Builds exact-tag et publication

Windows :

```powershell
.\distribution\build-release.ps1 -Version 1.1.0 -ExpectedTag v1.1.0
```

Linux :

```bash
./distribution/build-release.sh 1.1.0 v1.1.0
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

- [ ] tous les hashes rehachés après staging ;
- [ ] manifestes `version/tag/gitSha` identiques ;
- [ ] GitHub Release stable, non draft, non prerelease ;
- [ ] 8/8 assets publiés ;
- [ ] digests GitHub comparés aux preuves locales.

### R2-S8 — Réconciliation post-release

- [ ] `README.md` ;
- [ ] portail documentation ;
- [ ] roadmap gouvernance ;
- [ ] statut documentaire ;
- [ ] index validation ;
- [ ] installation et upgrade ;
- [ ] issue #113 fermée `completed` ;
- [ ] PR #114 et release référencées ;
- [ ] prochain jalon explicitement non défini ou cadré séparément.

## 6. Politique CI de juillet 2026

Jusqu'au 31 juillet 2026 inclus :

```text
GitHub Actions is not a release gate
no workflow rerun
no manual workflow dispatch
no opportunistic .github/workflows change
local Windows + Linux/WSL exact-head logs are authoritative
```

La modernisation de la CI commence au plus tôt en août et constitue un chantier séparé.

## 7. Gates de décision

### Autoriser la mise Ready de la PR

Uniquement si :

```text
Windows PASS
Linux/WSL PASS
same exact SHA
version 1.1.0 coherent
upgrade PASS
packaging PASS
SBOM/provenance PASS
post-gate executable delta NONE
validation evidence complete
```

### Autoriser le merge

Uniquement si les gates précédents restent vrais et qu'aucun thread de review n'est bloquant.

### Autoriser le tag et la publication

Uniquement après merge/stabilisation sur `main`, compare exact du SHA autorisé, puis builds exact-tag réussis sur Windows et Linux.

## 8. État courant

```text
R2-S0  🚧 presque terminé
R2-S1  ⏳ à implémenter
R2-S2  ⏳ à implémenter
R2-S3  ⏳ non exécuté
R2-S4  ⏳ non exécuté
R2-S5  ⏳ bloqué par qualification
R2-S6  ⏳ bloqué par qualification
R2-S7  ⏳ bloqué par merge/tag
R2-S8  ⏳ bloqué par publication
```

**Aucun PASS R2, merge, tag ou release 1.1.0 n'est déclaré à ce stade.**