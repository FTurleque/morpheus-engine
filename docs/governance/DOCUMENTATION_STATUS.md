# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.x post-M26**

Dernière mise à jour : 29 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
plan d’exécution du jalon actif
```

M26 est intégré. **M27 — Evidence-backed Assisted Reasoning** est le prochain jalon actif.

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

Points d’entrée M26 :

```text
docs/roadmap/M26_EXECUTION.md
docs/validation/VALIDATION_M26.md
docs/adr/0094-optional-team-remote-server-mode.md
docs/user/TEAM_REMOTE_SERVER.md
docs/developer/REMOTE_SERVER_PLATFORM.md
docs/openapi/morpheus-v1-remote-m26.yaml
contracts/public-surfaces.tsv
```

## Preuves historiques

Les fichiers `docs/validation/VALIDATION_*.md` conservent les SHA, nombres de tests, couvertures et résultats réellement observés. Une réconciliation documentaire ne réécrit jamais ces faits.

Pour M26 :

```text
Exact-head qualifié  bf481b24054c4577144b4cb2ede2bdbc4d9974a2
PR docs head         36378842e3ef41e379ade17f869b0939d052bbbc
Merge develop        49016a18c844a78ec864235c544d82d487da7c8a
Tests                579 PASS Windows + Linux
Architecture         234 PASS Windows + Linux
Windows coverage     44.3507% / 37.8842%
Linux coverage       44.3527% / 37.8842%
Local-first          PASS
TLS/auth/RBAC        PASS
Bounded concurrency  PASS / HTTP 429
Secret disclosure    NONE
Backup/restore       PASS
SQLite               V015
Portable             PASS Windows + Linux
Executable delta     NONE après qualification
ADR-0094             Acceptée — M26
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
M27            ⏭ NOW
```

La version officiellement publiée reste `v1.0.0`. M21→M26 sont des évolutions 1.x intégrées sur cette baseline.

Aucune GitHub Actions / CI n’a servi de gate M26 en juillet 2026 ; la preuve de référence est la double qualification locale Windows + Linux/WSL exact-head.
