# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.x post-M24**

Dernière mise à jour : 28 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
plan d’exécution du jalon actif
```

M24 est intégré. **M25 — Policy Packs & Governance Automation** est le jalon actif.

Pour les contrats exposés :

```text
code + tests
contracts/public-surfaces.tsv
docs/openapi/
docs/reference/
docs/developer/
docs/user/
```

Pour une décision d’architecture, l’ADR acceptée fait autorité tant qu’elle n’est pas remplacée.

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
```

Points d’entrée M24 :

```text
docs/roadmap/M24_EXECUTION.md
docs/validation/VALIDATION_M24.md
docs/adr/0092-provider-neutral-query-dsl-saved-views-reporting.md
docs/user/QUERY_VIEWS_REPORTING.md
docs/developer/QUERY_PLATFORM.md
docs/openapi/morpheus-v1-query-m24.yaml
contracts/public-surfaces.tsv
```

## Preuves historiques

Les fichiers `docs/validation/VALIDATION_*.md` conservent les SHA, nombres de tests, couvertures et résultats réellement observés. Une réconciliation documentaire ne réécrit jamais ces faits.

Pour M24 :

```text
Executable SHA   be69e47da0ae209d2246df9c67bc08caeafb2bb0
PR head          863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
Merge            2b483ded10c783fff22c25035db89475c5c9fdaf
Tests            543 PASS Windows + Linux
Architecture     221 PASS Windows + Linux
ADR-0092         Acceptée — M24
```

Le compare entre le SHA exécutable qualifié et le head PR a confirmé que tous les commits post-gate étaient exclusivement documentaires.

## Baseline actuelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21            ✅ validé et intégré
M22            ✅ validé et intégré
M23            ✅ validé et intégré
M24            ✅ validé et intégré
M25            ⏭ NOW
M26            ⏳ LATER
M27            ⏳ LATER
```

La version officiellement publiée reste `v1.0.0`. M21→M24 sont des évolutions 1.x intégrées sur cette baseline.

Aucune GitHub Actions / CI n’a servi de gate M24 en juillet 2026 ; la preuve de référence est la double qualification locale Windows + Linux exact-head.