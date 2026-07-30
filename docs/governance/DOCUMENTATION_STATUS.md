# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — R2 / MORPHEUS 1.1.0 EN PRÉPARATION**

Dernière mise à jour : 30 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
docs/roadmap/R2_EXECUTION.md
        ↓
docs/validation/VALIDATION_R2.md
```

M27 est validé et intégré dans `develop`. R2 est la consolidation de release active sur `r2-release-1.1.0`, issue #113 et PR draft #114 vers `main`.

La seule release stable publiée reste `v1.0.0`. MORPHEUS 1.1.0 est une candidate non publiée tant que `VALIDATION_R2.md` ne contient pas les preuves exact-head réelles et que la GitHub Release n'existe pas.

## Contrats exposés

Ordre d'autorité :

```text
code + tests
contracts/public-surfaces.tsv
docs/openapi/
docs/reference/
docs/developer/
docs/user/
```

Pour une décision d'architecture, l'ADR acceptée fait autorité tant qu'elle n'est pas remplacée. Pour une release, le code qualifié, le tag exact, les manifestes et la preuve de validation font autorité sur les textes promotionnels.

## Documentation active

```text
README.md
docs/README.md
docs/user/
docs/developer/
docs/reference/
docs/openapi/
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/POST_M20_EVOLUTION.md
docs/roadmap/R2_EXECUTION.md
docs/validation/VALIDATION_R2.md
docs/release/RELEASE_NOTES_1.1.0.md
```

## Points d’entrée R2

```text
Issue GitHub                            #113
PR draft                               #114
Branche                                r2-release-1.1.0
docs/roadmap/R2_EXECUTION.md
docs/validation/VALIDATION_R2.md
docs/release/RELEASE_NOTES_1.1.0.md
docs/user/UPGRADE_1_1.md
validate-r2.cmd
scripts/validate-r2.ps1
scripts/validate-r2.sh
```

## Preuves historiques

Les fichiers `docs/validation/VALIDATION_*.md` conservent les SHA, nombres de tests, couvertures et résultats réellement observés. Une réconciliation documentaire ne réécrit jamais ces faits.

Dernier jalon qualifié :

```text
M27 exact-head         f97307c878125550693699124ca717f64f305a3a
M27 PR docs head       026c1d5f8671cd7b879fa89d51af8e83a5f06272
M27 merge develop      f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Develop réconcilié     bccc118dda6fd818cf801750187afa4ad10b96e4
Tests                  602 PASS Windows + Linux
Architecture           238 PASS Windows + Linux
Windows coverage       45.2226% / 38.4456%
Linux coverage         45.2246% / 38.4456%
Executable delta       NONE après qualification
ADR-0095               Acceptée — M27
```

Release stable publiée :

```text
Version                1.0.0
Tag                    v1.0.0
Release SHA            51f6a120f3461c8d8c24323f3db8211d28d6cb42
GitHub Release         stable / 8 assets
```

## Baseline R2

```text
main                   0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop                bccc118dda6fd818cf801750187afa4ad10b96e4
release branch base    bccc118dda6fd818cf801750187afa4ad10b96e4
target version         1.1.0
target tag             v1.1.0
qualification SHA      NOT SET
Windows gate           NOT RUN
Linux/WSL gate         NOT RUN
merge main             NOT AUTHORIZED
release publication    NOT AUTHORIZED
```

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21            ✅ validé et intégré
M22            ✅ validé et intégré
M23            ✅ validé et intégré
M24            ✅ validé et intégré
M25            ✅ validé et intégré dans develop
M26            ✅ validé et intégré dans develop
M27            ✅ validé et intégré dans develop
R2             🚧 release 1.1.0 en préparation
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate R2 avant août 2026. Les preuves autoritatives sont les sorties locales Windows + Linux/WSL exact-head sur le même SHA.

Aucun document ne doit annoncer `v1.1.0` comme publiée avant le tag exact, les builds exact-tag, les huit assets et la GitHub Release stable effectivement vérifiés.