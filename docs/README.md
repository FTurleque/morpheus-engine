# Documentation MORPHEUS

Cette page est le point d’entrée de la documentation active de MORPHEUS.

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il normalise des spécifications, compose des providers sans effacer provenance ni conflits, publie des snapshots versionnés, expose query/traceability/quality/lifecycle, raisonne sur des portfolios, fournit Query DSL/saved views/exports, Policy Packs, un serveur remote HTTPS opt-in et une analyse assistée qui garde faits, inférences et suggestions séparés.

## Baseline actuelle

```text
Stable version          MORPHEUS 1.2.0
Stable tag              v1.2.0
R3                      COMPLETE / VALIDATED / PUBLISHED
M28                     livré dans 1.2.0
Post-R3                 D2 — Repository Hardening en cours
D2 issue                #120
```

R3 a été qualifié localement Windows + Linux/WSL puis publié avec huit assets vérifiés. D2 est un jalon de hardening **sans CI**.

## Parcours utilisateur

| Besoin | Document |
|---|---|
| installer / mettre à jour / désinstaller | [Installation](user/INSTALLATION.md) |
| comprendre les concepts et garanties | [Guide utilisateur](user/README.md) |
| exécuter un premier scénario | [Démarrage rapide](user/QUICKSTART.md) |
| trouver une commande | [Référence CLI](user/CLI.md) |
| connecter Copilot / Claude / Codex | [Clients MCP](user/MCP_CLIENTS.md) |
| utiliser les plugins provider | [Plugins provider](user/PROVIDER_PLUGINS.md) |
| travailler en portfolio | [Portfolios](user/PORTFOLIOS.md) |
| requêtes, saved views et exports | [Query DSL / Saved Views / Reporting](user/QUERY_VIEWS_REPORTING.md) |
| utiliser la gouvernance | [Policy Packs](user/POLICY_PACKS.md) |
| exposer le serveur d’équipe | [Team / Remote Server](user/TEAM_REMOTE_SERVER.md) |
| produire du reasoning assisté | [Assisted Reasoning](user/ASSISTED_REASONING.md) |
| configurer MINOS/NEXUS | [Intégrations](user/INTEGRATIONS.md) |

Les distributions Windows/Linux embarquent leur runtime Java.

## Parcours développeur

| Besoin | Document |
|---|---|
| comprendre les modules | [Guide développeur](developer/README.md) |
| comprendre les couches | [Architecture](developer/ARCHITECTURE.md) |
| compiler / tester / qualifier | [Build, tests et validation](developer/BUILD_AND_TEST.md) |
| Provider SDK | [Provider SDK](developer/PROVIDER_SDK.md) |
| portfolio | [Portfolio Intelligence](developer/PORTFOLIO_INTELLIGENCE.md) |
| query | [Query Platform](developer/QUERY_PLATFORM.md) |
| policy | [Policy Platform](developer/POLICY_PLATFORM.md) |
| remote server | [Remote Server Platform](developer/REMOTE_SERVER_PLATFORM.md) |
| reasoning | [Assisted Reasoning](developer/ASSISTED_REASONING.md) |
| HTTP | [API](developer/API.md) |
| MCP | [MCP](developer/MCP.md) |
| cross-engine | [Intégrations](developer/INTEGRATIONS.md) |

Baseline technique courante : Java 21, Maven Wrapper 3.9.16, SQLite, Java MCP SDK 2.0.0, `jdk.httpserver`, `jpackage` et Inno Setup. La branche D2 met à jour Jackson vers 3.1.5 LTS et sqlite-jdbc vers 3.53.2.0.

## Gouvernance

- [`governance/ROADMAP.md`](governance/ROADMAP.md) — état global ;
- [`governance/DOCUMENTATION_STATUS.md`](governance/DOCUMENTATION_STATUS.md) — autorité documentaire ;
- [`roadmap/D2_EXECUTION.md`](roadmap/D2_EXECUTION.md) — plan actif D2 ;
- [`validation/VALIDATION_D2.md`](validation/VALIDATION_D2.md) — preuve D2 en attente de qualification ;
- [`validation/VALIDATION_R3.md`](validation/VALIDATION_R3.md) — preuve de publication 1.2.0 ;
- [`release/RELEASE_NOTES_1.2.0.md`](release/RELEASE_NOTES_1.2.0.md) — release publiée.

## Validation active

Le gate Maven développeur est :

```text
mvnw clean verify
```

Le gate D2 Windows est :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Le gate D2 Linux/WSL est :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

D2 exige le même SHA sur les deux plateformes et interdit tout delta `.github/workflows/**`. Aucun statut GitHub Actions n’est utilisé comme preuve D2.

## Références machine

- [`reference/`](reference/) — contrats machine ;
- [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml) — contrat v1 historique/cumulatif ;
- [`openapi/morpheus-v1-portfolio-m23.yaml`](openapi/morpheus-v1-portfolio-m23.yaml) ;
- [`openapi/morpheus-v1-query-m24.yaml`](openapi/morpheus-v1-query-m24.yaml) ;
- [`openapi/morpheus-v1-policy-m25.yaml`](openapi/morpheus-v1-policy-m25.yaml) ;
- [`openapi/morpheus-v1-remote-m26.yaml`](openapi/morpheus-v1-remote-m26.yaml) ;
- [`openapi/morpheus-v1-reasoning-m27.yaml`](openapi/morpheus-v1-reasoning-m27.yaml) ;
- [`../contracts/public-surfaces.tsv`](../contracts/public-surfaces.tsv) — convergence CLI/MCP/HTTP ;
- [`../distribution/README.md`](../distribution/README.md) — distribution 1.2.0.

## État livré / actif

```text
C0 → M28       ✅ VALIDÉS / INTÉGRÉS
D0 + D1        ✅ VALIDÉS / INTÉGRÉS
R1             ✅ 1.0.0 publié
R2             ✅ 1.1.0 publié
R3             ✅ 1.2.0 publié
D2             🚧 POST-R3 HARDENING / LOCAL QUALIFICATION PENDING
```
