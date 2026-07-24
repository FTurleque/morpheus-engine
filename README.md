# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception et tâches associées.

MORPHEUS ne remplace ni le code, ni les outils de gestion de projet, ni les agents IA. Il fournit une couche de connaissance dédiée à l'intention et aux spécifications.

## Question fondamentale

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

## Position dans l'écosystème

```text
                           JARVIS
                        Orchestration
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
      MORPHEUS              MINOS              NEXUS
  Spec & Intent             Code              Context
   Intelligence          Intelligence        Intelligence
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                     ALFRED / BRAINIAC
                       Agents / profils IA
```

Responsabilités :

- **MORPHEUS** comprend l'intention, les exigences, les changements, les contraintes, les scénarios et les décisions ;
- **MINOS** comprend le code, les symboles, les relations et les impacts de code ;
- **NEXUS** sélectionne, classe et compresse le contexte pertinent ;
- **JARVIS** orchestre les capacités de l'écosystème.

Chaque brique reste autonome.

## Architecture

```text
Sources / workspaces
        ↓
Specification providers
        ↓
Normalisation MORPHEUS
        ↓
NormalizedProjectContent
        ↓
KnowledgeSnapshot / SpecificationVersion
        ↓
Persistence snapshot-scoped
   ┌────────┴────────┐
   ↓                 ↓
 Memory            SQLite
        ↓
Query / Search / Traceability / Quality / Change Analysis
        ↓
   ┌────┴────┐
   ↓         ↓
  CLI       MCP STDIO
   ↓         ↓
 scripts   IDE / agents
```

**OpenSpec est le provider de référence initial, pas le domaine de MORPHEUS.**

Un second provider synthétique démontre l'absence de verrouillage du cœur applicatif sur OpenSpec.

## Invariants structurants

MORPHEUS :

- possède son propre modèle de domaine ;
- est local-first et fonctionne sans LLM obligatoire ;
- sépare identité, version, locator et référence externe ;
- utilise UUIDv7 comme format canonique opaque de `DomainIdentity` ;
- namespace les identités externes par provider ;
- distingue CURRENT, PROPOSED et HISTORICAL ;
- publie la connaissance par snapshots cohérents à activation atomique ;
- conserve `APPLY`, `PROMOTE` et `ACTIVATE` comme opérations distinctes ;
- ne convertit jamais automatiquement un `Scenario` en `AcceptanceCriterion` ;
- ne déduit pas un lifecycle métier absent d'un snapshot publié ;
- conserve les liens non résolus au lieu d'inventer des faits ;
- garde Memory comme backend de référence contractuelle et SQLite comme backend local persistant ;
- ne nécessite pas de graph database au MVP ;
- reste découplé de MINOS, NEXUS et JARVIS ;
- réserve l'analyse du code à MINOS.

## Fondation technique

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ avec --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite JDBC 3.53.1.0
Memory store         : référence des tests contractuels
DomainIdentity       : UUIDv7
MCP SDK              : Java MCP SDK officiel 2.0.0
MCP transport        : STDIO natif
Graph DB             : aucune au MVP
Server framework     : aucun pour CLI/MCP local
DI framework         : aucun obligatoire
LLM                  : aucun obligatoire
Distribution         : native-first / archive portable autonome
```

## État du projet

```text
C0  Cadrage fonctionnel et architectural       ✅ VALIDÉ
M0  Faisabilité technique                      ✅ VALIDÉ
M1  Discovery / providers / store              ✅ VALIDÉ
M2  Ingestion et modèle normalisé              ✅ VALIDÉ — 94/94
M3  Temporalité / lifecycle / snapshots        ✅ VALIDÉ / INTÉGRÉ — 147/147
M4  Traçabilité typée                          ✅ VALIDÉ / INTÉGRÉ — 189/189
M5  Requêtes et contexte compact               ✅ VALIDÉ / INTÉGRÉ — 227/227
M6  Qualité / couverture / diagnostics         ✅ VALIDÉ / INTÉGRÉ — 261/261
M7  Synchronisation incrémentale / fraîcheur   ✅ VALIDÉ / INTÉGRÉ — 282/282
M8  Analyse des changements                    ✅ VALIDÉ / INTÉGRÉ — 289/289
M9  CLI stabilisée / distribution locale       ✅ VALIDÉ / INTÉGRÉ — 298/298 Windows + Linux
M10 Serveur MCP STDIO natif                    ✅ VALIDÉ — 307/307 + packaging Windows
```

Dernier gate officiellement validé : **M10**.

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
MORPHEUS MCP              5/5 PASS
MORPHEUS CLI             10/10 PASS
Architecture Tests      149/149 PASS
TOTAL                   307/307 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
```

Packaging M10 :

```text
MCP packaging proof      PASS
jpackage app-image       PASS
morpheus.exe --version   PASS
morpheus.exe --json      PASS
Windows ZIP              PASS — 77275075 bytes
runtime Java embarqué    PASS
```

Références :

- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`docs/VALIDATION_M10.md`](docs/VALIDATION_M10.md)
- [`docs/roadmap/M10_EXECUTION.md`](docs/roadmap/M10_EXECUTION.md)
- [`docs/MCP.md`](docs/MCP.md)
- [`docs/VALIDATION_M9.md`](docs/VALIDATION_M9.md)
- [`docs/CLI.md`](docs/CLI.md)
- [`distribution/README.md`](distribution/README.md)

## CLI

### Options globales

```text
--json
--data-dir PATH
--config-dir PATH
--db PATH
```

### Commandes

```text
help
version
paths
projects list
projects add --workspace PATH
sync --project ID [--revision REV]
sync-status --project ID
requirements find --project ID [--query TEXT]
changes list --project ID
changes get --project ID --change ID
constraints list --project ID --change ID
decisions list --project ID --change ID
tasks list --project ID --change ID
trace-requirement --project ID --requirement ID
change-context --project ID --change ID
analyze-change --project ID --change ID
quality --project ID
```

Le launcher officiel exécute `sync` comme **FULL_REBUILD conservateur** tant qu'un exécuteur métier incrémental complet n'est pas disponible. Le planificateur incrémental M7 reste intact dans le cœur.

Documentation : [`docs/CLI.md`](docs/CLI.md).

## Serveur MCP M10

Lancement :

```text
morpheus mcp --stdio
```

Avec base explicite :

```text
morpheus --db /path/to/morpheus.db mcp --stdio
```

Le serveur utilise la même SQLite que la CLI.

Catalogue M10 :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Contrat :

```text
14 tools read-only
JSON Schemas stricts
additionalProperties=false
stdout = protocole MCP uniquement
Scenario != AcceptanceCriterion
lifecycle non inféré
ACTIVE/CURRENT préservés
no write/promote/activate
```

Documentation : [`docs/MCP.md`](docs/MCP.md).

## Quick start

Après construction ou extraction d'une distribution :

```text
morpheus projects add --workspace <workspace-openspec>
# récupérer projectId
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query session
morpheus changes list --project <projectId>
morpheus quality --project <projectId>
```

Pour un client MCP :

```json
{
  "command": "morpheus",
  "args": ["--db", "/path/to/morpheus.db", "mcp", "--stdio"]
}
```

## Distribution

Artefacts :

```text
JAR autonome : morpheus-cli-<version>-all.jar
Windows      : morpheus-<version>-windows-x64.zip
Linux        : morpheus-<version>-linux-x64.tar.gz
```

Les archives portables sont générées avec `jpackage --type app-image` et embarquent leur runtime Java. Depuis M10, l'uber-JAR embarque aussi le serveur MCP et ses dépendances.

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
distribution/build-windows-installer.ps1   # optionnel, WiX requis au build
```

Le répertoire d'installation est séparé des données/config utilisateur afin de permettre upgrade et uninstall sans effacement implicite de SQLite.

Voir [`distribution/README.md`](distribution/README.md) et [`docs/roadmap/DEPLOYMENT.md`](docs/roadmap/DEPLOYMENT.md).

## Validation M10

```text
Maven gate        ✅ 307/307 PASS
MCP contract      ✅ 5/5 module MCP
STDIO integration ✅ initialize / tools/list / tools/call / schema rejection
Architecture      ✅ 149/149 PASS
Packaging         ✅ MCP proof + app-image + ZIP
Runtime           ✅ Java embarqué
Smoke             ✅ human + JSON
```

ADR acceptées :

```text
ADR-0062 — Java MCP SDK officiel + STDIO natif
ADR-0063 — catalogue MCP read-only et sémantique explicite
ADR-0064 — routage MCP dans le launcher natif
```

Validation : [`docs/VALIDATION_M10.md`](docs/VALIDATION_M10.md).
