# Documentation MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first : il normalise des spécifications, compose des providers sans effacer provenance ni conflits, publie des snapshots versionnés et les expose par CLI, MCP STDIO et API HTTP.

Version stable publiée : **MORPHEUS 1.2.1** (tag `v1.2.1`, 16 octobre 2026). La précédente, 1.2.0, reste disponible sous son tag.

La documentation est rangée en trois espaces, un par lecteur, plus le dossier d'architecture, qui porte le registre des ADR. Chaque page n'appartient qu'à un seul endroit.

| Vous voulez… | Espace | Entrée |
|---|---|---|
| installer, configurer et utiliser MORPHEUS | [`user/`](user/) | [Guide utilisateur](user/README.md) |
| compiler, modifier, intégrer ou étendre MORPHEUS | [`developer/`](developer/) | [Guide développeur](developer/README.md) |
| suivre le projet : état, plans, preuves, audits | [`project/`](project/) | [Suivi du projet](project/README.md) |
| lire le dossier d'architecture (arc42) | [`architecture/`](architecture/) | [Dossier d'architecture](architecture/README.md) |
| retrouver une décision d'architecture | [`architecture/adr/`](architecture/adr/) | [Registre des ADR](architecture/adr/README.md) |

## Utilisateur — [`user/`](user/)

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
| vérifier la version et les mises à jour | [Intégrité produit](user/PRODUCT_INTEGRITY.md) |
| migrer depuis une version antérieure | [Upgrade 1.1](user/UPGRADE_1_1.md) · [Upgrade 1.2](user/UPGRADE_1_2.md) |
| lire ce qui change dans une version | [Notes de version](user/release-notes/) |

Les distributions Windows/Linux embarquent leur runtime Java.

## Développeur — [`developer/`](developer/)

| Besoin | Document |
|---|---|
| **reprendre le dépôt après une longue interruption** | [**Reprise à froid**](developer/COLD_START_RECOVERY.md) |
| comprendre les modules et les gates | [Guide développeur](developer/README.md) |
| compiler / tester / qualifier | [Build, tests et validation](developer/BUILD_AND_TEST.md) |
| comprendre les couches | [Architecture](developer/ARCHITECTURE.md) |
| lire un contrat machine | [Références](developer/reference/README.md) · [OpenAPI](developer/reference/openapi/) |
| analyse statique et tests de mutation | [Audit de code](developer/CODE_AUDIT.md) · [SpotBugs](developer/SPOTBUGS.md) |
| écrire un provider | [Provider SDK](developer/PROVIDER_SDK.md) |
| HTTP / MCP / cross-engine | [API](developer/API.md) · [MCP](developer/MCP.md) · [Intégrations](developer/INTEGRATIONS.md) |
| sous-plateformes | [Portfolio](developer/PORTFOLIO_INTELLIGENCE.md) · [Query](developer/QUERY_PLATFORM.md) · [Policy](developer/POLICY_PLATFORM.md) · [Remote](developer/REMOTE_SERVER_PLATFORM.md) · [Reasoning](developer/ASSISTED_REASONING.md) |
| observabilité, version, intégrité de production | [Operability](developer/OPERABILITY.md) · [Version produit](developer/PRODUCT_VERSION.md) · [Production integrity](developer/PRODUCTION_INTEGRITY.md) |

Les seuils de qualité actifs (tests, couverture, gates) sont énoncés dans le [guide développeur](developer/README.md) et [Build, tests et validation](developer/BUILD_AND_TEST.md) ; leur source normative est `config/m21-quality-ratchets.properties`.

## Architecture — [`architecture/`](architecture/)

| Besoin | Document |
|---|---|
| retrouver ou écrire une décision | [Registre des ADR](architecture/adr/README.md) · [gabarit](architecture/adr/template.md) |
| vue d'ensemble du système | [Vue d'ensemble](architecture/overview.md) |
| dossier arc42 complet | [Index arc42](architecture/README.md) |
| scénarios qualité, risques et dette | [Scénarios qualité](architecture/quality/scenarios.md) · [Registre des risques](architecture/risks/register.md) |

## Projet — [`project/`](project/)

| Besoin | Document |
|---|---|
| connaître l'état courant et les priorités | [Roadmap](project/governance/ROADMAP.md) |
| savoir quel document fait autorité | [Statut documentaire](project/governance/DOCUMENTATION_STATUS.md) |
| lire le plan d'un jalon | [Plans d'exécution](project/roadmap/README.md) |
| lire la preuve d'un jalon ou d'une release | [Validations](project/validation/README.md) |
| lire un audit daté | [Audits](project/audits/README.md) |
| cadrage produit, études, écosystème | [Produit](project/product/README.md) · [Études](project/research/) · [Écosystème](project/ECOSYSTEME.md) |

Les plans, preuves et audits sont des relevés datés : ils ne sont pas réécrits pour leur faire revendiquer un résultat postérieur à leur SHA. Les pages de `user/` et `developer/` décrivent, elles, la baseline courante.

## Hors de `docs/`

- [`../contracts/public-surfaces.tsv`](../contracts/public-surfaces.tsv) — manifeste de convergence CLI/MCP/HTTP ;
- [`../distribution/README.md`](../distribution/README.md) — construction des distributions ;
- [`../scripts/README.md`](../scripts/README.md) — validateurs.
