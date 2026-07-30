# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.1.0 PUBLIÉ — R3 / 1.2.0 PLANIFIÉ**

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

R2 est terminé. MORPHEUS 1.1.0 est la release stable publiée. Le chantier suivant est R3 / 1.2.0, suivi dans l’issue GitHub #115.

## Contrats exposés

Ordre d’autorité :

```text
code + tests
contracts/public-surfaces.tsv
docs/openapi/
docs/reference/
docs/developer/
docs/user/
```

Pour une décision d’architecture, l’ADR acceptée fait autorité tant qu’elle n’est pas remplacée. Pour une release, le code qualifié, le tag exact, les manifestes et la preuve de validation font autorité sur les textes promotionnels.

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

## Release stable publiée

```text
Version                1.1.0
Tag                    v1.1.0
Tag target             31506029ded1101f0571edeb0d79c59bbf3f68c6
Qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
PR                     #114 MERGED
Issue                  #113 CLOSED / completed
GitHub Release         stable
Assets                 8/8 uploaded
Published parity       8/8 PASS
Published at           2026-07-30T14:13:17Z
```

La release 1.1.0 couvre M21 à M27, notamment :

```text
provider plugins
portfolio multi-projets
Query DSL / saved views / reporting
Policy Packs
serveur remote HTTPS optionnel
RBAC READ / WRITE / ADMIN
backup / restore SQLite
assisted reasoning fondé sur preuves
CLI / MCP STDIO / HTTP
packaging portable Windows/Linux
setup Windows per-user
CycloneDX + provenance
```

## Preuves R2

```text
Windows tests          603 PASS
Linux/WSL tests        603 PASS
Windows architecture   238 PASS
Linux/WSL architecture 238 PASS
same SHA               PASS
post-gate executable   NONE
exact-tag Windows      PASS
exact-tag Linux        PASS
published assets       8/8 PASS
```

Références :

```text
docs/roadmap/R2_EXECUTION.md
docs/validation/VALIDATION_R2.md
docs/release/RELEASE_NOTES_1.1.0.md
docs/user/UPGRADE_1_1.md
```

## Chantier actif suivant

```text
R3                     MORPHEUS 1.2.0
Issue                  #115
Objet                  MCP Client Integration & Installer Wiring
Clients                Copilot JetBrains / Copilot CLI
                       Claude Code / Claude Desktop
                       OpenAI Codex
Transport              MCP STDIO natif
Docker requis          NON
```

Le câblage client doit rester opt-in, sauvegarder les configurations tierces, préserver les entrées étrangères et permettre une désinstallation conservatrice.

## Baseline fonctionnelle

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
R2             ✅ MORPHEUS 1.1.0 publié
R3             ⏭ MORPHEUS 1.2.0 planifié
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate avant août 2026. Les preuves autoritatives restent les sorties locales Windows et Linux/WSL exact-head sur le même SHA.

Aucun document ne doit déplacer ni réécrire le tag `v1.1.0`. Toute évolution fonctionnelle postérieure appartient à une nouvelle version.
