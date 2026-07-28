# Portfolio Specification Intelligence — M23

M23 introduit une couche d'intelligence multi-projets provider-neutral sans modifier les invariants d'identité MORPHEUS.

SHA exécutable qualifié Windows + Linux : `04a906e9d5858292ed0f0f1bec65246fef91ed63`.

## Modèle

```text
PortfolioId
  |
  +-- PortfolioMembership(ProjectSpecificationId, observations, status)
  +-- PortfolioFreshness(ProjectSpecificationId, state, revision, observedAt)
  +-- CrossProjectReference(PortfolioEntityRef -> PortfolioEntityRef)

PortfolioEntityRef = ProjectSpecificationId + entityType + DomainIdentity
```

Les identités ne sont jamais dérivées de `SourceLocator`, d'un workspace, d'une URL de repository ou d'un identifiant provider.

## Domain

Types M23 principaux :

```text
PortfolioId
PortfolioDefinition
PortfolioMembership
PortfolioMembershipStatus
PortfolioFreshness
PortfolioFreshnessState
PortfolioEntityRef
CrossProjectReference
CrossProjectReferenceId
```

Les UUID suivent le contrat `DomainIdentity`/UUIDv7 déjà établi.

## Application

Services :

```text
PortfolioRegistryService
PortfolioQueryService
PortfolioTraversalService
PortfolioPublicViews
```

`PortfolioRegistryService` gère création, adhésion, état `MISSING`, fraîcheur et observations de références.

`PortfolioQueryService` sépare les requêtes project-scoped et portfolio-scoped et expose les conflits sans écrasement silencieux.

`PortfolioTraversalService` implémente une BFS déterministe avec budgets explicites.

## Ordre de traversal

Contrat M23 : l'ordre observable de `depthByNode` est l'ordre de découverte BFS.

La déterminisation se fait avant exploration par tri des voisins. Le résultat ne doit pas réordonner les nœuds par UUID après la BFS.

```text
queue                 ArrayDeque
visited/depth order   LinkedHashMap
neighbor order        deterministic comparator
result order          BFS discovery order preserved
```

La régression détectée pendant la qualification Windows venait d'une copie finale `TreeMap` qui détruisait cet ordre. Les tests utilisent désormais des UUIDv7 volontairement hors ordre lexical afin de verrouiller ce contrat.

## Budgets

```text
MAX_DEPTH       8
MAX_NODES       1,000
MAX_LINKS       5,000
MAX_PAGE_SIZE   500
```

La réponse de traversal expose `truncated` et `truncationReason`.

## Persistence

Le port applicatif est `PortfolioStore`.

Implémentations :

```text
MemoryPortfolioStore
SqlitePortfolioStore
```

SQLite utilise la migration additive :

```text
db/migration/V013__portfolio_intelligence.sql
```

Le comportement `MISSING` est non destructif : l'adhésion change d'état, mais l'identité et les références historiques sont conservées.

## Projection transport-safe

CLI JSON, MCP et HTTP ne sérialisent jamais directement les objets domaine M23.

`PortfolioPublicViews` projette :

```text
DomainIdentity / PortfolioId / ProjectSpecificationId -> String
Instant                                           -> String
Optional<T>                                       -> Optional transport-safe
```

Cette règle reste cohérente avec ADR-0047 ; il n'est pas nécessaire d'élargir `CanonicalJsonSerializer` pour sérialiser directement des UUID ou des objets domaine.

Vues publiques :

```text
PortfolioView
MembershipView
FreshnessView
EntityRefView
ReferenceView
ConflictView
OverviewView
TraversalNodeView
TraversalView
```

## CLI

Racine :

```text
morpheus portfolio
```

Actions :

```text
create
add-project
missing
freshness
add-reference
list
overview
members
references
conflicts
traverse
```

Voir [`../user/PORTFOLIOS.md`](../user/PORTFOLIOS.md).

## MCP

Outils synchrones M23 :

```text
create_portfolio
register_portfolio_project
mark_portfolio_project_missing
observe_portfolio_freshness
add_cross_project_reference
get_portfolio_overview
list_portfolio_references
traverse_portfolio
```

Les schémas sont stricts (`additionalProperties=false`). Les limites de traversal et pagination sont bornées côté outil.

## HTTP

Le routage est sous :

```text
/api/v1/portfolios
```

Routes :

```text
GET  /portfolios
POST /portfolios
GET  /portfolios/{portfolioId}
GET  /portfolios/{portfolioId}/members
POST /portfolios/{portfolioId}/projects
POST /portfolios/{portfolioId}/projects/{projectId}/missing
POST /portfolios/{portfolioId}/projects/{projectId}/freshness
GET  /portfolios/{portfolioId}/references
POST /portfolios/{portfolioId}/references
GET  /portfolios/{portfolioId}/conflicts
POST /portfolios/{portfolioId}/traverse
```

Le service HTTP `MorpheusPortfolioApiService` reste un adaptateur de transport : parsing/validation transport d'un côté, règles portfolio dans les services applicatifs de l'autre.

OpenAPI M23 : [`../openapi/morpheus-v1-portfolio-m23.yaml`](../openapi/morpheus-v1-portfolio-m23.yaml).

## Conflits et provenance

Une `CrossProjectReference` conserve :

```text
source PortfolioEntityRef
target PortfolioEntityRef
relation
providerId
sourceLocator optional
evidenceId optional
observedAt
```

Des observations contradictoires restent présentes. La précédence éventuelle ne supprime jamais la provenance.

## Freshness

La fraîcheur est enregistrée individuellement par projet. Une observation de fraîcheur ne déclenche pas de rescan destructif du portfolio et ne réécrit pas les autres adhésions.

## Contrats d'architecture prouvés

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
precedence != provenance erasure
traversal is bounded and explainable
freshness != full destructive rescan
local-first remains default
```

## Validation

```text
Executable SHA       04a906e9d5858292ed0f0f1bec65246fef91ed63
Windows              PASS
Linux WSL2           PASS
Tests                507 PASS
Architecture         195 PASS
Windows coverage     46.7034% line / 40.9099% branch
Linux coverage       46.6979% line / 40.9099% branch
SBOM/provenance      PASS Windows + Linux
Portable             PASS Windows + Linux
Executable delta     NONE Windows + Linux
```

Preuve : [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md).
ADR : [`../adr/0091-multi-project-portfolio-intelligence.md`](../adr/0091-multi-project-portfolio-intelligence.md).
