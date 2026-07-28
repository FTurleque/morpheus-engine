# Guide utilisateur MORPHEUS

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il transforme une ou plusieurs sources de spécification en un modèle normalisé, versionné, composable et interrogeable, puis expose ce modèle par CLI, MCP STDIO et API HTTP locale.

Baseline documentée : **M24 validé et intégré**, avec qualification Windows + Linux sur MORPHEUS `1.0.0`.

```text
M24 executable  be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 merge       2b483ded10c783fff22c25035db89475c5c9fdaf
Tests           543 PASS Windows + Linux
Architecture    221 PASS Windows + Linux
```

## 1. À quoi sert MORPHEUS ?

MORPHEUS permet notamment de savoir :

- quelles exigences sont actuellement publiées ;
- quels changements restent proposés ;
- quelles contraintes, décisions, critères d’acceptation et tâches sont liés ;
- d’où vient une information et à quoi elle est reliée ;
- quels providers ont contribué à une vue et selon quelle priorité ;
- quels conflits de composition existent entre sources ;
- quels plugins provider externes sont découvrables et compatibles ;
- quels projets appartiennent à un portfolio sans confondre identité et localisation ;
- quelles références relient des entités de projets différents ;
- jusqu’où une traversal inter-projets bornée peut aller ;
- comment interroger un projet ou portfolio avec un DSL provider-neutral ;
- comment sauvegarder une requête sous forme de saved view versionnée ;
- comment exporter un résultat en JSON canonique, CSV ou Markdown ;
- si une transition lifecycle est autorisée compte tenu des faits disponibles ;
- quelles références MINOS ou quel contexte NEXUS sont disponibles en complément.

MORPHEUS ne remplace ni Git, ni un tracker, ni MINOS, ni NEXUS, ni JARVIS.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
           + provider-neutral query/view/reporting contracts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 2. Les trois surfaces

| Surface | Usage principal | Transport | Écriture contrôlée |
|---|---|---|---|
| CLI | humain, scripts, administration locale | processus local | projet/sync + portfolio registry + saved-view config + lifecycle write explicite |
| MCP | IDE, agents, orchestrateurs | STDIO / JSON-RPC | portfolio registry + saved-view config + lifecycle write explicite |
| API HTTP | intégration locale | HTTP `/api/v1` | projet/sync + portfolio registry + saved-view config + lifecycle write explicite |

Les trois surfaces utilisent les mêmes services applicatifs ; elles ne réimplémentent pas les règles métier.

M24 ajoute notamment :

```text
CLI   query / views / export
MCP   execute_query + saved-view + export tools
HTTP  /api/v1/queries/execute
HTTP  /api/v1/saved-views
HTTP  /api/v1/exports
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

Un projet temporairement absent peut être marqué `MISSING` sans perdre son identité ni ses références historiques.

Guide complet : [Portfolios multi-projets](PORTFOLIOS.md).

## 4. Query DSL, Saved Views & Reporting

M24 introduit un langage de requête métier fermé et borné.

```text
DSL != SQL passthrough
saved view != materialized truth
export != mutation
```

Scopes :

```text
project   -> ProjectSpecificationId
portfolio -> PortfolioId
```

Opérateurs :

```text
eq
neq
contains
starts-with
ends-with
in
exists
and(...)
or(...)
not(...)
```

Exemple :

```bash
morpheus query execute \
  --project <projectId> \
  --entity requirement \
  --filter 'title contains "session"' \
  --sort title:asc \
  --limit 50
```

Saved view :

```bash
morpheus views create \
  --name "Current requirements" \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT'
```

Export :

```bash
morpheus export view --id <savedViewId> --format csv
```

Guide complet : [Query DSL, Saved Views & Reporting](QUERY_VIEWS_REPORTING.md).

## 5. Providers et plugins externes

MORPHEUS prend en charge les providers intégrés OpenSpec et Structured Markdown, ainsi qu’un mécanisme pour charger des providers externes compatibles via le Provider SDK.

```text
discover != activate
probe != read
metadata != trust
classloader isolation != security sandbox
```

Voir [Plugins provider](PROVIDER_PLUGINS.md).

## 6. Temporalité et lifecycle

```text
CURRENT     état publié de référence
PROPOSED    intention non encore publiée
HISTORICAL  état publié antérieur
```

`SpecificationVersion != KnowledgeSnapshot`.

Une évaluation lifecycle peut être `ALLOWED`, `BLOCKED`, `UNKNOWN` ou `REQUIRES_INPUT`.

**Évaluer une transition ne l’applique jamais.**

## 7. Parcours recommandé

1. installer ou extraire MORPHEUS ;
2. `projects add` ;
3. `sync` ;
4. vérifier `sync-status` ;
5. composer les providers si nécessaire ;
6. examiner les conflits ;
7. interroger requirements, changes, traçabilité et qualité ;
8. créer un portfolio pour raisonner sur plusieurs projets ;
9. utiliser `query execute` pour les vues ad hoc ;
10. créer une saved view lorsque la requête doit être réutilisée ;
11. exporter en JSON/CSV/Markdown lorsque nécessaire ;
12. utiliser HTTP/MCP si un outil consomme MORPHEUS ;
13. appliquer un lifecycle uniquement via la commande write explicite et ses garde-fous.

Voir [Démarrage rapide](QUICKSTART.md).

## 8. Commandes principales

| Besoin | Commande |
|---|---|
| enregistrer un workspace | `projects add` |
| lister les projets | `projects list` |
| reconstruire/publier | `sync` |
| contrôler la fraîcheur | `sync-status` |
| composer les providers | `composition sync` |
| voir les conflits provider | `composition conflicts` |
| découvrir des plugins | `provider-plugins discover` |
| créer un portfolio | `portfolio create` |
| vue portfolio | `portfolio overview` |
| traversal inter-projets | `portfolio traverse` |
| requête générique | `query execute` |
| créer/lire/versionner une vue | `views create/get/versions/update` |
| archiver/exécuter une vue | `views archive/execute` |
| exporter une requête | `export query` |
| exporter une vue | `export view` |
| chercher une exigence | `requirements find` |
| traçabilité | `trace-requirement` |
| analyser un changement | `analyze-change` |
| qualité | `quality` |
| appliquer transition | `lifecycle apply` |

Référence détaillée : [CLI](CLI.md).

## 9. Garanties structurantes

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
stale revision != overwrite
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
portfolio result preserves ProjectSpecificationId
```

MORPHEUS préfère `UNAVAILABLE`/`UNKNOWN` à un fait inventé.

## 10. Stockage local

SQLite est le store persistant par défaut.

```text
--data-dir PATH       MORPHEUS_DATA_DIR
--config-dir PATH     MORPHEUS_CONFIG_DIR
--db PATH             MORPHEUS_DB
```

M23 ajoute SQLite V013 pour le portfolio. M24 ajoute SQLite V014 pour les saved views.

## 11. JSON et automatisation

Pour automatiser :

- vérifier le code de sortie ;
- parser le JSON de `stdout` ;
- ne pas dépendre du texte humain de `stderr` ;
- ne pas utiliser `--json` avec `morpheus mcp --stdio`.

Les vues publiques convertissent explicitement identités et timestamps en valeurs transport-safe avant sérialisation.

## 12. Intégrations optionnelles

| Intégration | Apport | Si absente |
|---|---|---|
| MINOS | résolution vers le code | seule la résolution code est indisponible |
| NEXUS | contexte technique sous budget | seul le contexte augmenté est indisponible |
| JARVIS | séquencement/orchestration | MORPHEUS reste autonome |

## 13. Baseline M24 intégrée

```text
code M24 qualifié     be69e47da0ae209d2246df9c67bc08caeafb2bb0
PR head docs-only     863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
merge M24             2b483ded10c783fff22c25035db89475c5c9fdaf
version               1.0.0
tests                 543 PASS Windows + Linux
architecture          221 PASS Windows + Linux
Query DSL             PASS
Saved views           PASS
SQLite V014           PASS
JSON/CSV/Markdown     PASS
packaging Win/Linux   PASS
SBOM/provenance       PASS Windows + Linux
executable delta      NONE Windows + Linux
```

## 14. Documentation associée

- [Démarrage rapide](QUICKSTART.md)
- [Référence CLI](CLI.md)
- [Portfolios multi-projets](PORTFOLIOS.md)
- [Query DSL, Saved Views & Reporting](QUERY_VIEWS_REPORTING.md)
- [Plugins provider](PROVIDER_PLUGINS.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Query Platform — développeur](../developer/QUERY_PLATFORM.md)
- [API HTTP](../developer/API.md)
- [Serveur MCP](../developer/MCP.md)
- [Validation M24](../validation/VALIDATION_M24.md)
- [Portail de documentation](../README.md)