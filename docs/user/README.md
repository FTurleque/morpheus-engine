# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il transforme une ou plusieurs sources de spécification en un modèle normalisé, versionné, composable et interrogeable, puis expose ce modèle par CLI, MCP STDIO et API HTTP locale.

Baseline documentée : **M23 validé et intégré**, avec qualification Windows + Linux sur MORPHEUS `1.0.0`.

```text
M23 executable  04a906e9d5858292ed0f0f1bec65246fef91ed63
M23 merge       88355b69c493677c8689eecad214fb00d283359b
Tests           507 PASS Windows + Linux
Architecture    195 PASS Windows + Linux
```

## 1. À quoi sert MORPHEUS ?

MORPHEUS permet notamment de savoir :

- quelles exigences sont actuellement publiées ;
- quels changements restent proposés ;
- quelles contraintes, décisions, critères d’acceptation et tâches sont liés ;
- d’où vient une information et à quoi elle est reliée ;
- quels providers ont contribué à une vue et selon quelle priorité ;
- quels conflits de composition existent entre sources ;
- quels plugins provider externes sont découvrables et compatibles sans chargement automatique ;
- quels projets appartiennent à un portfolio sans confondre identité, workspace, repository ou provider ;
- quelles références relient des entités de projets différents ;
- quels conflits inter-projets restent observables ;
- jusqu’où une traversal inter-projets bornée peut aller et pourquoi elle a éventuellement été tronquée ;
- ce qui a changé entre snapshots ;
- si une transition lifecycle est autorisée compte tenu des faits disponibles ;
- quelles références MINOS ou quel contexte NEXUS sont disponibles en complément.

MORPHEUS ne remplace ni Git, ni un tracker, ni MINOS, ni NEXUS, ni JARVIS.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 2. Les trois surfaces

| Surface | Usage principal | Transport | Écriture contrôlée |
|---|---|---|---|
| CLI | humain, scripts, administration locale | processus local | projet/sync + portfolio registry + lifecycle write explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | portfolio registry + lecture + lifecycle write explicite |
| API HTTP | intégration locale JSON | HTTP `/api/v1` | projet/sync + portfolio registry + lifecycle write explicite |

Les trois surfaces utilisent les mêmes services applicatifs ; elles ne réimplémentent pas les règles métier.

M22 ajoute les plugins provider :

```text
CLI   provider-plugins discover / probe
MCP   discover_provider_plugins / probe_provider_plugin
HTTP  GET /api/v1/provider-plugins/discover
HTTP  GET /api/v1/provider-plugins/probe
```

M23 ajoute l’intelligence portfolio :

```text
CLI   portfolio ...
MCP   create/register/freshness/reference/overview/traverse portfolio tools
HTTP  /api/v1/portfolios
```

## 3. Projet, snapshot, providers et portfolio

Un `ProjectSpecificationId` est l’identité métier stable d’un projet MORPHEUS. Il n’est pas dérivé du workspace, du repository ou d’un identifiant provider.

Un portfolio ajoute une frontière multi-projets :

```text
PortfolioId
  |
  +-- ProjectSpecificationId A
  +-- ProjectSpecificationId B
  +-- ProjectSpecificationId C
  |
  +-- cross-project references
  +-- project-scoped freshness
```

Une adhésion de portfolio peut mémoriser workspace, repository et providers observés, mais ces localisations ne deviennent jamais l’identité du projet.

Un projet temporairement absent peut être marqué `MISSING` sans perdre son identité ni ses références historiques.

Guide complet : [Portfolios multi-projets](PORTFOLIOS.md).

## 4. Providers et plugins externes

MORPHEUS prend en charge les providers intégrés OpenSpec et Structured Markdown, ainsi qu’un mécanisme pour charger des providers externes compatibles via le Provider SDK.

```text
Sources intégrées       Plugins externes
       |                       |
       v                       v
SpecificationProvider   metadata discovery
       |                       |
       +---- probe/capabilities+
                 |
                 v
      SpecificationContentReader
                 |
                 v
       normalisation provider-neutral
                 |
                 v
          KnowledgeSnapshot
```

Un plugin provider est un JAR externe déclaré par :

```text
META-INF/morpheus-provider.properties
META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin
```

MORPHEUS sépare strictement :

```text
discover != activate
probe != read
metadata != trust
classloader isolation != security sandbox
```

Voir [Plugins provider](PROVIDER_PLUGINS.md).

## 5. Temporalité et lifecycle

```text
CURRENT     état publié de référence
PROPOSED    intention non encore publiée
HISTORICAL  état publié antérieur
```

`SpecificationVersion != KnowledgeSnapshot`.

Le lifecycle métier d’un changement est une dimension distincte :

```text
DRAFT -> PROPOSED -> SPECIFIED -> DESIGNED/PLANNED
      -> IMPLEMENTING -> VERIFYING -> COMPLETED -> ARCHIVED
      -> ABANDONED selon transitions autorisées
```

Une évaluation peut être :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

**Évaluer une transition ne l’applique jamais.**

## 6. Parcours recommandé

1. installer ou extraire MORPHEUS ;
2. `projects add` ;
3. `sync` ;
4. vérifier `sync-status` ;
5. si plusieurs providers sont présents, exécuter `composition sync` ;
6. examiner `composition status` et `composition conflicts` ;
7. interroger requirements, changes, traçabilité et qualité ;
8. créer un portfolio lorsque plusieurs projets doivent être raisonnés ensemble ;
9. ajouter les projets au portfolio puis examiner overview/references/conflicts/traversal ;
10. utiliser HTTP/MCP si un outil consomme MORPHEUS ;
11. utiliser `provider-plugins discover/probe` uniquement pour des plugins explicitement choisis ;
12. activer MINOS/NEXUS uniquement si nécessaire ;
13. appliquer un lifecycle uniquement via la commande write explicite et ses garde-fous.

Voir [Démarrage rapide](QUICKSTART.md).

## 7. Commandes principales

| Besoin | Commande |
|---|---|
| enregistrer un workspace | `projects add` |
| lister les projets | `projects list` |
| reconstruire/publier | `sync` |
| contrôler la fraîcheur | `sync-status` |
| composer les providers | `composition sync` |
| voir les conflits provider | `composition conflicts` |
| découvrir des plugins | `provider-plugins discover` |
| sonder un plugin | `provider-plugins probe` |
| créer un portfolio | `portfolio create` |
| enregistrer un projet dans un portfolio | `portfolio add-project` |
| vue portfolio | `portfolio overview` |
| références inter-projets | `portfolio references` |
| conflits inter-projets | `portfolio conflicts` |
| traversal inter-projets | `portfolio traverse` |
| chercher une exigence | `requirements find` |
| lister/lire les changements | `changes list`, `changes get` |
| critères d’acceptation | `acceptance-criteria list` |
| contraintes | `constraints list/evaluate` |
| traçabilité | `trace-requirement` |
| contexte d’un changement | `change-context` |
| analyser un changement | `analyze-change` |
| qualité | `quality` |
| référence code | `external-references resolve` |
| contexte NEXUS | `augmented-context` |
| observer lifecycle | `change-orchestration state` |
| évaluer transition | `change-orchestration transition-check` |
| appliquer transition | `lifecycle apply` |

Référence détaillée : [CLI](CLI.md).

## 8. Garanties structurantes

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
applicable != blocking
warning != blocker
severity != blocking policy
transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
capability declaration != capability implementation proof
probe != read
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
freshness != full destructive rescan
optional engine absence != MORPHEUS failure
```

MORPHEUS préfère `UNAVAILABLE`/`UNKNOWN` à un fait inventé.

## 9. Stockage local

SQLite est le store persistant par défaut.

```text
--data-dir PATH       MORPHEUS_DATA_DIR
--config-dir PATH     MORPHEUS_CONFIG_DIR
--db PATH             MORPHEUS_DB
```

Utiliser `morpheus paths` pour afficher les chemins résolus.

CLI, API et MCP partagent les mêmes données seulement s’ils utilisent le même layout ou `--db`.

M23 persiste le portfolio en mémoire ou SQLite via la migration additive V013.

## 10. JSON et automatisation

```bash
morpheus --json portfolio overview --portfolio <portfolioId>
```

Pour automatiser :

- vérifier le code de sortie ;
- parser le JSON de `stdout` ;
- ne pas dépendre du texte humain de `stderr` ;
- ne pas utiliser `--json` avec `morpheus mcp --stdio`.

Les vues M23 convertissent explicitement identités et timestamps en chaînes transport-safe avant sérialisation JSON.

## 11. Intégrations optionnelles

| Intégration | Apport | Si absente |
|---|---|---|
| MINOS | résolution vers le code | seule la résolution code est indisponible |
| NEXUS | contexte technique sous budget | seul le contexte augmenté est indisponible |
| JARVIS | séquencement/orchestration | MORPHEUS reste autonome |

MINOS, NEXUS et JARVIS ne sont pas embarqués comme moteurs dans MORPHEUS.

Les plugins provider externes ne sont pas non plus embarqués dans la distribution MORPHEUS ; ils restent des JARs explicitement fournis par l’utilisateur ou le host.

## 12. Baseline M23 intégrée

```text
code M23 qualifié   04a906e9d5858292ed0f0f1bec65246fef91ed63
merge M23           88355b69c493677c8689eecad214fb00d283359b
version             1.0.0
tests               507 PASS Windows + Linux
architecture        195 PASS Windows + Linux
portfolio identity  PASS
cross-project refs  PASS
bounded traversal   PASS
SQLite V013         PASS
packaging Win       PASS
packaging Linux     PASS
SBOM/provenance     PASS Windows + Linux
executable delta    NONE Windows + Linux
```

## 13. Documentation associée

- [Démarrage rapide](QUICKSTART.md)
- [Référence CLI](CLI.md)
- [Portfolios multi-projets](PORTFOLIOS.md)
- [Plugins provider](PROVIDER_PLUGINS.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Portfolio Intelligence — développeur](../developer/PORTFOLIO_INTELLIGENCE.md)
- [Provider SDK](../developer/PROVIDER_SDK.md)
- [API HTTP](../developer/API.md)
- [Serveur MCP](../developer/MCP.md)
- [Validation M23](../validation/VALIDATION_M23.md)
- [Portail de documentation](../README.md)
