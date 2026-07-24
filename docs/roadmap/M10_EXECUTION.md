# M10 — Plan d'exécution détaillé

Statut : **EN COURS — implémentation MCP**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M9 validés et intégrés
M9 merge = 2533f325c6ef55070857a8bf75808648d99da5a2
M9 gate  = Windows 298/298 + Linux 298/298 PASS
```

Issue : **#57 — M10 — Serveur MCP stdio natif**  
Branche : `m10/mcp-server`

## Question de sortie

> **MORPHEUS peut-il exposer ses capacités de lecture d'intention/specification à des agents via un serveur MCP local stdio natif, avec des tools déterministes, des JSON Schemas stricts, des erreurs explicites et aucune logique métier essentielle dans les handlers MCP, tout en restant utilisable sans serveur HTTP, Docker, MINOS, NEXUS ou JARVIS ?**

Réponse actuelle : **implémentation en cours ; gate pending**.

## M10-S1 — SDK et transport

Décision candidate : Java MCP SDK officiel `2.0.0`, transport STDIO natif.

```text
morpheus mcp --stdio
stdout = protocole MCP uniquement
stderr = diagnostics runtime uniquement
HTTP = hors périmètre M10
Docker = non requis
```

## M10-S2 — Catalogue de tools

Catalogue M10 exact :

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

Tous les tools M10 sont **read-only**.

## M10-S3 — Sémantique stricte

Les handlers MCP ne doivent pas recréer les règles métier. Ils valident les arguments, appellent les services/ports MORPHEUS existants puis traduisent le résultat en réponse MCP.

Invariants :

```text
Scenario != AcceptanceCriterion
lifecycle non inféré depuis snapshot
absence != fait inventé
CURRENT/ACTIVE policy preserved
SQLite state shared with CLI
no promotion / activation / write tool
```

`get_acceptance_criteria` doit exposer explicitement `UNAVAILABLE_IN_NORMALIZED_MODEL` lorsque la source ne fournit pas cette sémantique.

`get_change_status` doit exposer explicitement l'absence de lifecycle persisté au lieu d'inférer un état.

## M10-S4 — Schemas MCP

Chaque tool possède un JSON Schema d'entrée strict :

- `additionalProperties=false` ;
- identifiants requis selon le tool ;
- pagination bornée ;
- profondeur bornée `1..20` ;
- max-age borné ;
- validation SDK active avant handler.

## M10-S5 — Requêtes agent-friendly

Les résultats conservent les identités, snapshot IDs, provenance utile et warnings existants. Les vues complexes M5/M6/M8 restent réutilisées plutôt que dupliquées.

`get_specification_context` agrège uniquement des faits du snapshot ACTIVE : spécifications, requirements CURRENT, changements et scénarios associés.

`get_blocking_conditions` expose des findings de qualité déterministes existants pour le changement et signale séparément toute information lifecycle indisponible.

## M10-S6 — Launcher / distribution

Le launcher natif M9 devient le point d'entrée commun :

```text
morpheus <CLI command>
morpheus mcp --stdio
```

Les archives Windows/Linux doivent continuer à embarquer le runtime Java et désormais les dépendances MCP nécessaires dans l'uber-JAR.

## M10-S7 — Tests

Preuves minimales :

```text
MorpheusMcpToolCatalogTest
MorpheusMcpToolServiceTest
MorpheusMcpServerContractTest
MorpheusMain MCP routing test
Architecture dependency test
```

Le gate final doit également inclure un échange MCP STDIO réel :

```text
initialize
notifications/initialized
tools/list
tools/call
```

Un argument hors schema (ex. `depth=99`) doit être rejeté avant exécution du handler.

## M10-S8 — Gate final

Gate local obligatoire :

```powershell
.\mvnw.cmd clean test
```

Puis smoke MCP sur le launcher packagé ou le JAR ombré.

M10 ne sera marqué VALIDÉ qu'après preuve reproductible. Les ADR M10 restent Proposées jusque-là.

## ADR M10

```text
ADR-0062 — Proposée — Java MCP SDK officiel + STDIO natif
ADR-0063 — Proposée — catalogue MCP read-only et sémantique explicite
ADR-0064 — Proposée — intégration au launcher natif et stdout protocol-clean
```

## Hors périmètre M10

```text
Streamable HTTP
SSE
OAuth réseau
Docker obligatoire
write tools
sync mutation via MCP
RequirementDelta apply/promote/activate via MCP
code intelligence MINOS
context ranking/compression NEXUS
orchestration JARVIS
```
