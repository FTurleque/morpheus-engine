# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.x post-M27**

Dernière mise à jour : 30 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
plan d’exécution du jalon actif ou dernier plan final intégré
```

M27 est validé et intégré. Aucun jalon post-M27 n’est actuellement déclaré actif.

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

Points d’entrée M27 :

```text
docs/roadmap/M27_EXECUTION.md
docs/validation/VALIDATION_M27.md
docs/adr/0095-evidence-backed-assisted-reasoning.md
docs/user/ASSISTED_REASONING.md
docs/developer/ASSISTED_REASONING.md
docs/openapi/morpheus-v1-reasoning-m27.yaml
contracts/public-surfaces.tsv
```

## Preuves historiques

Les fichiers `docs/validation/VALIDATION_*.md` conservent les SHA, nombres de tests, couvertures et résultats réellement observés. Une réconciliation documentaire ne réécrit jamais ces faits.

Pour M27 :

```text
Exact-head qualifié  f97307c878125550693699124ca717f64f305a3a
PR docs head         026c1d5f8671cd7b879fa89d51af8e83a5f06272
Merge develop        f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Tests                602 PASS Windows + Linux
Architecture         238 PASS Windows + Linux
Windows coverage     45.2226% / 38.4456%
Linux coverage       45.2246% / 38.4456%
Facts / claims       séparation PASS
Confidence           explicite et bornée PASS
Evidence             citations + provenance PASS
Adapters             optionnels + fault isolation PASS
No silent mutation   PASS / mutated=false
CLI/MCP/HTTP         convergence PASS
Remote READ RBAC     PASS
Portable             PASS Windows + Linux
SBOM/provenance      PASS Windows + Linux
Executable delta     NONE après qualification
ADR-0095             Acceptée — M27
```

Le compare entre le SHA exact qualifié et le head PR a confirmé que tous les commits post-gate avant merge étaient exclusivement documentaires.

## Baseline actuelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21            ✅ validé et intégré
M22            ✅ validé et intégré
M23            ✅ validé et intégré
M24            ✅ validé et intégré
M25            ✅ validé et intégré
M26            ✅ validé et intégré
M27            ✅ validé et intégré
Prochain jalon ⏳ non défini
```

La version officiellement publiée reste `v1.0.0`. M21→M27 sont des évolutions 1.x intégrées sur cette baseline.

Aucune GitHub Actions / CI n’a servi de gate M27 en juillet 2026 ; la preuve de référence est la double qualification locale Windows + Linux/WSL exact-head.