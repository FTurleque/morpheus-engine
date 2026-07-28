# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il transforme une ou plusieurs sources de spécification en un modèle normalisé, versionné, composable et interrogeable, puis expose ce modèle par CLI, MCP STDIO et API HTTP locale.

Baseline documentée : **M22 techniquement qualifié Windows + Linux** sur MORPHEUS `1.0.0`.

## 1. À quoi sert MORPHEUS ?

MORPHEUS permet notamment de savoir :

- quelles exigences sont actuellement publiées ;
- quels changements restent proposés ;
- quelles contraintes, décisions, critères d’acceptation et tâches sont liés ;
- d’où vient une information et à quoi elle est reliée ;
- quels providers ont contribué à une vue et selon quelle priorité ;
- quels conflits de composition existent entre sources ;
- ce qui a changé entre snapshots ;
- si une transition lifecycle est autorisée compte tenu des faits disponibles ;
- quelles références MINOS ou quel contexte NEXUS sont disponibles en complément ;
- quels plugins provider externes sont découvrables et compatibles sans les charger automatiquement.

MORPHEUS ne remplace ni Git, ni un tracker, ni MINOS, ni NEXUS, ni JARVIS.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 2. Les trois surfaces

| Surface | Usage principal | Transport | Écriture contrôlée |
|---|---|---|---|
| CLI | humain, scripts, administration locale | processus local | projet/sync + lifecycle write explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | lecture + lifecycle write explicite |
| API HTTP | intégration locale JSON | HTTP `/api/v1` | projet/sync + lifecycle write explicite |

Les trois surfaces utilisent les mêmes services applicatifs ; elles ne réimplémentent pas les règles métier.

M22 ajoute des surfaces explicites pour les plugins provider :

```text
CLI   provider-plugins discover / probe
MCP   discover_provider_plugins / probe_provider_plugin
HTTP  GET /api/v1/provider-plugins/discover
HTTP  GET /api/v1/provider-plugins/probe
```

Aucun scan de plugin n’est effectué automatiquement au démarrage.

## 3. Projet, snapshot et providers

MORPHEUS prend en charge les providers intégrés OpenSpec et Structured Markdown, ainsi qu’un mécanisme M22 pour charger des providers externes compatibles via le Provider SDK.

```text
Sources intégrées       Plugins externes M22
      |                         |
      v                         v
SpecificationProvider     metadata discovery
      |                         |
      +------ probe/capabilities+
                  |
                  v
       SpecificationContentReader
                  |
                  v
        normalisation provider-neutral
                  |
                  v
           KnowledgeSnapshot
                  |
                  v
          Memory / SQLite
                  |
                  v
          CLI / MCP / HTTP
```

Une synchronisation normale construit un snapshot candidat. Une composition multi-provider peut agréger plusieurs contributions provider-neutral en conservant provenance, priorité et conflits explicites.

## 4. Plugins provider M22

Un plugin provider est un JAR externe déclaré par :

```text
META-INF/morpheus-provider.properties
META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin
```

M22 sépare strictement :

```text
discover != activate
probe != read
metadata != trust
classloader isolation != security sandbox
```

La découverte lit uniquement les métadonnées. L’activation est explicite et charge le JAR dans un classloader dédié. Le probe vérifie les capacités réelles du provider pour un workspace. La lecture normalisée passe ensuite par le contrat provider-neutral du SDK.

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

1. extraire la distribution ;
2. `projects add` ;
3. `sync` ;
4. vérifier `sync-status` ;
5. si plusieurs providers sont présents, exécuter `composition sync` ;
6. examiner `composition status` et `composition conflicts` ;
7. interroger requirements, changes, traçabilité et qualité ;
8. utiliser HTTP/MCP si un outil consomme MORPHEUS ;
9. utiliser `provider-plugins discover/probe` uniquement pour des plugins explicitement choisis ;
10. activer MINOS/NEXUS uniquement si nécessaire ;
11. appliquer un lifecycle uniquement via la commande write explicite et ses garde-fous.

Voir [Démarrage rapide](QUICKSTART.md).

## 7. Commandes principales

| Besoin | Commande |
|---|---|
| enregistrer un workspace | `projects add` |
| lister les projets | `projects list` |
| reconstruire/publier | `sync` |
| contrôler la fraîcheur | `sync-status` |
| composer les providers | `composition sync` |
| voir l’état de composition | `composition status` |
| voir les conflits | `composition conflicts` |
| découvrir des plugins | `provider-plugins discover` |
| sonder un plugin | `provider-plugins probe` |
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

## 10. JSON et automatisation

```bash
morpheus --json provider-plugins discover --directory ./plugins
```

Pour automatiser :

- vérifier le code de sortie ;
- parser le JSON de `stdout` ;
- ne pas dépendre du texte humain de `stderr` ;
- ne pas utiliser `--json` avec `morpheus mcp --stdio`.

## 11. Intégrations optionnelles

| Intégration | Apport | Si absente |
|---|---|---|
| MINOS | résolution vers le code | seule la résolution code est indisponible |
| NEXUS | contexte technique sous budget | seul le contexte augmenté est indisponible |
| JARVIS | séquencement/orchestration | MORPHEUS reste autonome |

MINOS, NEXUS et JARVIS ne sont pas embarqués comme moteurs dans MORPHEUS.

Les plugins provider externes ne sont pas non plus embarqués dans la distribution MORPHEUS ; ils restent des JARs explicitement fournis par l’utilisateur ou le host.

## 12. Baseline M22 qualifiée

```text
code M22 qualifié  e42bc31384831e56592b11a3509b49a3fdf61773
version             1.0.0
tests               494 PASS Windows + Linux
architecture        190 PASS Windows + Linux
SDK API             1
provider externe    PASS
packaging Win       PASS
packaging Linux     PASS
SBOM/provenance     PASS Windows + Linux
executable delta    NONE Windows + Linux
```

## 13. Documentation associée

- [Démarrage rapide](QUICKSTART.md)
- [Référence CLI](CLI.md)
- [Plugins provider](PROVIDER_PLUGINS.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Provider SDK](../developer/PROVIDER_SDK.md)
- [API HTTP](../developer/API.md)
- [Serveur MCP](../developer/MCP.md)
- [Validation M22](../validation/VALIDATION_M22.md)
- [Portail de documentation](../README.md)