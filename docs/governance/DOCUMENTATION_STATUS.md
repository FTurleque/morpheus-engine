# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.x post-M25**

Dernière mise à jour : 29 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
plan d’exécution du jalon actif
```

M25 est intégré. **M26 — Optional Team/Remote Server Mode** est le prochain jalon actif.

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

Points d’entrée M25 :

```text
docs/roadmap/M25_EXECUTION.md
docs/validation/VALIDATION_M25.md
docs/adr/0093-provider-neutral-policy-packs-governance-automation.md
docs/user/POLICY_PACKS.md
docs/developer/POLICY_PLATFORM.md
docs/openapi/morpheus-v1-policy-m25.yaml
contracts/public-surfaces.tsv
```

## Preuves historiques

Les fichiers `docs/validation/VALIDATION_*.md` conservent les SHA, nombres de tests, couvertures et résultats réellement observés. Une réconciliation documentaire ne réécrit jamais ces faits.

Pour M25 :

```text
Exact-head qualifié  a392604fc9e8d00f4021351ab5ba53f8488ab920
PR docs head         9239be641992f40a46f228e09cf6b34ad1cbb1a4
Merge develop        62bf0ea37f732116e821df7d98ae89d36c6dd75d
Tests                565 PASS Windows + Linux
Architecture         231 PASS Windows + Linux
Windows coverage     42.9925% / 36.3983%
Linux coverage       42.9945% / 36.3983%
SQLite               V015 PASS
Policy convergence   CLI/MCP/HTTP PASS
Portable             PASS Windows + Linux
Executable delta     NONE après qualification
ADR-0093             Acceptée — M25
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
M26            ⏭ NOW
M27            ⏳ LATER
```

La version officiellement publiée reste `v1.0.0`. M21→M25 sont des évolutions 1.x intégrées sur cette baseline.

Aucune GitHub Actions / CI n’a servi de gate M25 en juillet 2026 ; la preuve de référence est la double qualification locale Windows + Linux/WSL exact-head.