# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il normalise, versionne, compose et expose les faits de spécification et d’intention via CLI, MCP STDIO et API HTTP.

Baseline stable publiée : **MORPHEUS 1.2.0**.

```text
stable tag             v1.2.0
release commit         3ad9ebf030b58df97482e21e272c24feae6b9d86
R3 qualification       608 tests / 243 architecture Windows + Linux/WSL
published assets       8/8 vérifiés
M28                    livré dans 1.2.0
D2                     hardening post-R3 en cours
```

## Capacités principales

MORPHEUS permet notamment de :

- publier et interroger requirements, changes, contraintes, décisions, critères et tâches ;
- conserver identité, versions, snapshots et provenance ;
- composer plusieurs providers sans masquer les conflits ;
- corréler plusieurs projets dans un portfolio ;
- exécuter un Query DSL provider-neutral ;
- gérer des saved views et exporter JSON/CSV/Markdown ;
- appliquer des Policy Packs versionnés et auditables ;
- évaluer des policies en dry-run sans mutation ;
- exposer un serveur HTTPS d’équipe explicitement opt-in ;
- produire des analyses assistées séparant faits, inférences et suggestions ;
- connecter le MCP natif à Copilot, Claude et Codex.

## Surfaces

| Surface | Usage | Transport | Écriture |
|---|---|---|---|
| CLI | humain, scripts, administration | processus local | explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | selon tool et capability |
| API locale | intégration locale | HTTP `/api/v1`, loopback | selon route |
| API remote | équipe | HTTPS `/api/v1` | RBAC READ/WRITE/ADMIN |

Les surfaces reposent sur les mêmes services applicatifs et les omissions sont explicites.

## Démarrage rapide

```bash
morpheus projects add --workspace /path/to/project
morpheus projects list
morpheus sync --project <projectId>
morpheus sync-status --project <projectId>
```

Voir [QUICKSTART.md](QUICKSTART.md) et [CLI.md](CLI.md).

## MCP et clients IA

Serveur local :

```text
morpheus mcp --stdio
```

Clients pris en charge par le gestionnaire Windows opt-in :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Le gestionnaire sauvegarde avant écriture, conserve les autres serveurs, refuse d’écraser une entrée `morpheus` étrangère, conserve les modifications manuelles et retire uniquement les entrées qu’il gère encore. Docker n’est pas requis.

Guide : [MCP_CLIENTS.md](MCP_CLIENTS.md).

## Données persistantes

```text
--data-dir PATH       MORPHEUS_DATA_DIR
--config-dir PATH     MORPHEUS_CONFIG_DIR
--db PATH             MORPHEUS_DB
```

Windows :

```text
data    %LOCALAPPDATA%\MORPHEUS\data
config  %LOCALAPPDATA%\MORPHEUS\config
db      %LOCALAPPDATA%\MORPHEUS\data\morpheus.db
```

Linux :

```text
data    $XDG_DATA_HOME/morpheus ou ~/.local/share/morpheus
config  $XDG_CONFIG_HOME/morpheus ou ~/.config/morpheus
```

## Garanties structurantes

```text
DomainIdentity != EntityVersionId != SourceLocator
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
UNKNOWN != FAILED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
saved view != materialized truth
export != mutation
local mode remains first-class
remote mode is opt-in
authentication != authorization
facts != inference
reasoning != mutation
MCP client integration is opt-in
foreign MCP entry is preserved
Docker is not required for native MCP
```

MORPHEUS préfère `UNAVAILABLE` ou `UNKNOWN` à un fait inventé.

## Documentation associée

- [Installation](INSTALLATION.md)
- [Démarrage rapide](QUICKSTART.md)
- [Référence CLI](CLI.md)
- [Clients MCP](MCP_CLIENTS.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [Portfolios](PORTFOLIOS.md)
- [Query DSL / Saved Views / Reporting](QUERY_VIEWS_REPORTING.md)
- [Policy Packs](POLICY_PACKS.md)
- [Team / Remote Server](TEAM_REMOTE_SERVER.md)
- [Assisted Reasoning](ASSISTED_REASONING.md)
- [Plugins provider](PROVIDER_PLUGINS.md)
- [Upgrade 1.2](UPGRADE_1_2.md)
- [Guide développeur](../developer/README.md)
- [Validation R3](../validation/VALIDATION_R3.md)
- [D2](../roadmap/D2_EXECUTION.md)
