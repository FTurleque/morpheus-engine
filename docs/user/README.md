# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il normalise, versionne, compose et expose les faits de spécification et d’intention via CLI, MCP STDIO et API HTTP.

Baseline stable publiée : **MORPHEUS 1.1.0**.

```text
stable tag             v1.1.0
release commit         31506029ded1101f0571edeb0d79c59bbf3f68c6
R2 qualification       603 tests / 238 architecture Windows + Linux
published assets       8/8 vérifiés
M28 active             MCP Client Integration & Installer Wiring
```

## 1. Capacités principales

MORPHEUS permet notamment de :

- publier et interroger des requirements, changes, contraintes, décisions, critères et tâches ;
- conserver identité, versions, snapshots et provenance ;
- composer plusieurs providers sans masquer les conflits ;
- corréler plusieurs projets dans un portfolio ;
- exécuter un Query DSL provider-neutral ;
- gérer des saved views et exporter en JSON, CSV ou Markdown ;
- appliquer des Policy Packs versionnés et auditables ;
- évaluer des policies en dry-run sans mutation ;
- exposer un serveur HTTPS d’équipe explicitement opt-in ;
- produire des analyses assistées séparant faits, inférences et suggestions ;
- connecter le serveur MCP natif à Copilot, Claude et Codex.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio/query/policy contracts
           + optional remote/team boundary
           + evidence-backed assisted claims
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 2. Surfaces

| Surface | Usage | Transport | Écriture |
|---|---|---|---|
| CLI | humain, scripts, administration | processus local | explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | selon tool et capability |
| API locale | intégration locale | HTTP `/api/v1`, loopback | selon route |
| API remote | équipe | HTTPS `/api/v1` | RBAC READ/WRITE/ADMIN |

Les surfaces utilisent les mêmes services applicatifs.

## 3. Démarrage rapide

```bash
morpheus projects add --workspace /path/to/project
morpheus projects list
morpheus sync --project <projectId>
morpheus sync-status --project <projectId>
```

Voir [Démarrage rapide](QUICKSTART.md) et [Référence CLI](CLI.md).

## 4. MCP et clients IA

Le serveur local est :

```text
morpheus mcp --stdio
```

M28 ajoute une intégration opt-in pour :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Le setup Windows propose cinq cases décochées par défaut. Le gestionnaire :

- sauvegarde les fichiers JSON ;
- conserve les autres serveurs et propriétés ;
- refuse d’écraser une entrée étrangère `morpheus` ;
- conserve les modifications manuelles ;
- retire uniquement les entrées qu’il gère encore ;
- ne requiert pas Docker.

Guide complet : [Connecter MORPHEUS aux clients MCP](MCP_CLIENTS.md).

## 5. Données persistantes

```text
--data-dir PATH       MORPHEUS_DATA_DIR
--config-dir PATH     MORPHEUS_CONFIG_DIR
--db PATH             MORPHEUS_DB
```

Windows par défaut :

```text
data    %LOCALAPPDATA%\MORPHEUS\data
config  %LOCALAPPDATA%\MORPHEUS\config
db      %LOCALAPPDATA%\MORPHEUS\data\morpheus.db
```

Linux par défaut :

```text
data    $XDG_DATA_HOME/morpheus ou ~/.local/share/morpheus
config  $XDG_CONFIG_HOME/morpheus ou ~/.config/morpheus
```

Tous les clients MCP doivent utiliser les mêmes racines.

## 6. Composition et providers

```bash
morpheus composition sync --project <projectId>
morpheus composition status --project <projectId>
morpheus composition conflicts --project <projectId>
```

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
```

Voir [Plugins provider](PROVIDER_PLUGINS.md).

## 7. Portfolio

```bash
morpheus portfolio create --name "Platform"
morpheus portfolio overview --id <portfolioId>
morpheus portfolio traverse --id <portfolioId> --from <entityRef>
```

Un `ProjectSpecificationId` reste distinct du workspace et du repository.

Guide : [Portfolios multi-projets](PORTFOLIOS.md).

## 8. Query DSL, Saved Views et Reporting

```bash
morpheus query execute \
  --project <projectId> \
  --entity requirement \
  --filter 'title contains "session"' \
  --sort title:asc \
  --limit 50
```

```bash
morpheus views create \
  --name "Current requirements" \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT'
```

```bash
morpheus export view --id <savedViewId> --format csv
```

Guide : [Query DSL, Saved Views & Reporting](QUERY_VIEWS_REPORTING.md).

## 9. Policy Packs

```bash
morpheus policy pack create --name "Release governance" ...
morpheus policy activate --id <packId> --version <versionId> --project <projectId> ...
morpheus policy dry-run --id <packId> --version <versionId> --project <projectId>
```

```text
constraint text != executable policy
policy recommendation != applied mutation
dry-run != mutation
```

Guide : [Policy Packs](POLICY_PACKS.md).

## 10. Serveur d’équipe remote

Le mode local reste le comportement par défaut. Le mode remote exige :

```text
HTTPS
PKCS12
Bearer authentication
READ / WRITE / ADMIN
```

Le token clair n’est jamais persisté. Le restore SQLite reste offline.

Guide : [Team / Remote Server](TEAM_REMOTE_SERVER.md).

## 11. Assisted Reasoning

MORPHEUS sépare :

```text
PUBLISHED_FACT
OBSERVATION
INFERENCE
HEURISTIC
SUGGESTION
```

Une analyse de reasoning reste read-only et indique explicitement `mutated=false`.

Guide : [Assisted Reasoning](ASSISTED_REASONING.md).

## 12. Lifecycle contrôlé

```text
CURRENT     état publié
PROPOSED    intention non publiée
HISTORICAL  état publié antérieur
```

Évaluer une transition ne l’applique jamais.

Le write explicite exige :

```text
WRITE_CHANGE capability
confirmation
expectedRevision / CAS
idempotencyKey
audit
```

## 13. Garanties structurantes

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

## 14. Documentation associée

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
- [Architecture développeur](../developer/README.md)
- [Validation M28](../validation/VALIDATION_M28.md)
