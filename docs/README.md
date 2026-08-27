# Documentation MORPHEUS

Cette page est le point d’entrée de la documentation active de MORPHEUS.

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il normalise des spécifications, compose des providers sans effacer provenance ni conflits, publie des snapshots versionnés, expose query/traceability/quality/lifecycle, raisonne sur des portfolios, fournit Query DSL/saved views/exports, Policy Packs, un serveur remote HTTPS opt-in et une analyse assistée qui garde faits, inférences et suggestions séparés.

## Baseline actuelle

```text
Stable version          MORPHEUS 1.2.0
Stable tag              v1.2.0
R3                      COMPLETE / VALIDATED / PUBLISHED
M28                     livré dans 1.2.0
Development baseline    1.2.1
D2                      COMPLETE / QUALIFIED / MERGED
D2 issue                #120 CLOSED / completed
```

R3 reste la preuve de la release stable publiée. D2 a été qualifié localement au même SHA sur Windows et Linux/WSL puis intégré dans `develop`; cette preuve historique n’utilisait pas la CI comme gate. La baseline corrective `1.2.1` utilise désormais les workflows exact-head `MORPHEUS CI`, `MORPHEUS Security` et `MORPHEUS CodeQL` comme protections continues complémentaires.

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

Baseline technique courante : Java 21, Maven Wrapper 3.9.16, SQLite JDBC 3.53.2.0, Jackson 3.1.5 LTS, Java MCP SDK 2.0.1, `jdk.httpserver`, `jpackage` et Inno Setup.

## Gouvernance

- [`governance/ROADMAP.md`](governance/ROADMAP.md) — état global et priorités post-D2 ;
- [`governance/DOCUMENTATION_STATUS.md`](governance/DOCUMENTATION_STATUS.md) — autorité documentaire courante ;
- [`roadmap/D2_EXECUTION.md`](roadmap/D2_EXECUTION.md) — plan historique D2 ;
- [`validation/VALIDATION_D2.md`](validation/VALIDATION_D2.md) — preuve historique D2, complétée par les sorties exact-head publiées sur la PR #121 ;
- [`validation/VALIDATION_R3.md`](validation/VALIDATION_R3.md) — preuve de publication 1.2.0 ;
- [`release/RELEASE_NOTES_1.2.0.md`](release/RELEASE_NOTES_1.2.0.md) — release publiée.

Les documents de preuve historiques ne sont pas réécrits pour leur faire revendiquer des résultats postérieurs à leur SHA. Les pages actives décrivent, elles, la baseline courante.

## Validation active

Gate Maven développeur :

```text
mvnw clean verify
```

Gate durable exact-head :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

Contrats actifs :

```text
Surefire total                 >= 820
architecture tests             >= 258
JaCoCo global lines            >= 50.6%
JaCoCo global branches         >= 43.0%
PR changed executable lines    >= 80%
PR changed branches            >= 70%
dependency hygiene             blocking
SBOM / provenance              required
CI exact-head                  Windows + Ubuntu
security                       Dependency-Check + CodeQL
```

La qualification historique D2 reste distincte : Windows et Linux/WSL avaient validé le même SHA exact sans CI, conformément à son contrat de sortie.

## Références machine

- [`reference/`](reference/) — contrats machine ;
- [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml) — contrat v1 historique/cumulatif ;
- [`openapi/morpheus-v1-portfolio-m23.yaml`](openapi/morpheus-v1-portfolio-m23.yaml) ;
- [`openapi/morpheus-v1-query-m24.yaml`](openapi/morpheus-v1-query-m24.yaml) ;
- [`openapi/morpheus-v1-policy-m25.yaml`](openapi/morpheus-v1-policy-m25.yaml) ;
- [`openapi/morpheus-v1-remote-m26.yaml`](openapi/morpheus-v1-remote-m26.yaml) ;
- [`openapi/morpheus-v1-reasoning-m27.yaml`](openapi/morpheus-v1-reasoning-m27.yaml) ;
- [`../contracts/public-surfaces.tsv`](../contracts/public-surfaces.tsv) — convergence CLI/MCP/HTTP ;
- [`../distribution/README.md`](../distribution/README.md) — distribution stable 1.2.0.

## État livré / actif

```text
C0 → M28       ✅ VALIDÉS / INTÉGRÉS
D0 + D1        ✅ VALIDÉS / INTÉGRÉS
R1             ✅ 1.0.0 publié
R2             ✅ 1.1.0 publié
R3             ✅ 1.2.0 publié
D2             ✅ QUALIFIÉ / INTÉGRÉ
1.2.1          🔧 BASELINE DE DÉVELOPPEMENT / HARDENING CONTINU
```
