# Guide développeur MORPHEUS

Cette documentation décrit la baseline **M24 validée, intégrée et qualifiée Windows + Linux** de MORPHEUS `1.0.0`. Elle sert de point d’entrée pour importer le projet, comprendre le découpage Maven, préserver les frontières d’architecture et exécuter les gates de validation.

```text
M24 executable  be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 PR head     863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
M24 merge       2b483ded10c783fff22c25035db89475c5c9fdaf
Tests           543 PASS Windows + Linux
Architecture    221 PASS Windows + Linux
```

## 1. Prérequis

```text
Java   >= 21
Maven  3.9.16+ via Maven Wrapper
Git
```

Le build parent compile avec `release=21`.

Windows :

```powershell
.\mvnw.cmd --version
```

Linux/macOS :

```bash
./mvnw --version
```

## 2. Import IntelliJ IDEA

MORPHEUS est un projet Maven multi-module. Le `pom.xml` racine doit être chargé comme projet Maven.

Ne pas créer les sous-modules manuellement : ils sont définis par le reactor Maven.

## 3. Vue du dépôt

```text
morpheus-engine/
├── morpheus-domain/
├── morpheus-application/
├── morpheus-provider-sdk/
├── morpheus-provider-testkit/
├── morpheus-provider-reference/
├── morpheus-provider-openspec/
├── morpheus-provider-markdown/
├── morpheus-provider-synthetic/
├── morpheus-store-memory/
├── morpheus-store-sqlite/
├── morpheus-integration-minos/
├── morpheus-integration-nexus/
├── morpheus-mcp/
├── morpheus-api/
├── morpheus-cli/
├── morpheus-architecture-tests/
├── distribution/
├── docs/
├── experiments/
└── pom.xml
```

Le gate M24 qualifié parcourt **17 modules reactor SUCCESS**.

## 4. Architecture des modules

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne connaissent aucun type provider-specific, transport-specific ou plugin externe.

M24 ne crée pas un nouveau module Maven : le Query DSL, les saved views et l’export vivent dans `morpheus-application`; Memory/SQLite implémentent les ports ; CLI/MCP/API restent des adapters.

| Module | Responsabilité |
|---|---|
| `morpheus-domain` | modèle métier, value objects, invariants purs |
| `morpheus-application` | use cases, ports, lifecycle, composition, portfolio, Query DSL/saved views/export |
| `morpheus-provider-sdk` | SPI public plugin, metadata, discovery, compatibility, activation |
| `morpheus-provider-testkit` | assertions contractuelles pour auteurs de plugins |
| `morpheus-provider-reference` | plugin externe de référence |
| `morpheus-provider-openspec` | découverte/lecture/normalisation OpenSpec |
| `morpheus-provider-markdown` | lecture Markdown structuré |
| `morpheus-provider-synthetic` | provider contrôlé pour tests |
| `morpheus-store-memory` | implémentations mémoire des ports |
| `morpheus-store-sqlite` | persistance versionnée et migrations V001→V014 |
| `morpheus-integration-minos` | client MINOS via MCP STDIO |
| `morpheus-integration-nexus` | client NEXUS via MCP STDIO |
| `morpheus-mcp` | adapter serveur MCP |
| `morpheus-api` | adapter HTTP `/api/v1` |
| `morpheus-cli` | composition root, launcher et UX |
| `morpheus-architecture-tests` | contrats ArchUnit/cross-module |

## 5. Provider SDK

Le contrat plugin reste :

```java
public interface MorpheusProviderPlugin {
    ProviderPluginMetadata metadata();
    SpecificationProvider createProvider();
    SpecificationContentReader createContentReader();
}
```

Invariants :

```text
provider plugin != domain dependency
plugin discovery != plugin activation
metadata != executable trust
capability declaration != capability implementation proof
probe != read
classloader isolation != security sandbox
```

Voir [Provider SDK](PROVIDER_SDK.md).

## 6. Portfolio Specification Intelligence M23

Le modèle multi-projets repose notamment sur :

```text
PortfolioId
PortfolioMembership
PortfolioFreshness
PortfolioEntityRef
CrossProjectReference
```

`PortfolioEntityRef` conserve toujours :

```text
ProjectSpecificationId + entityType + DomainIdentity
```

Le traversal est une BFS déterministe et bornée.

Documentation détaillée : [Portfolio Specification Intelligence](PORTFOLIO_INTELLIGENCE.md).

## 7. Query Platform M24

M24 ajoute dans `morpheus-application` :

```text
query.dsl
query.saved
query.export
```

Contrats principaux :

```text
QueryDefinition
QueryScope
QueryEntityType
QueryFilter
QueryOperator
QuerySort
QueryProjection
QueryPage
QueryResult
SavedViewId
SavedViewDefinition
SavedViewVersion
SavedViewStore
QueryExportService
```

Invariants :

```text
DSL != SQL passthrough
provider-specific types -X-> query model
transport types -X-> query model
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
portfolio result preserves ProjectSpecificationId
stale saved-view revision != silent overwrite
```

`QueryDslParser` et `QueryValidator` centralisent parsing, champs/opérateurs/types et budgets.

`QueryExecutionService` fournit filter/sort/projection/pagination déterministes avec tie-break `projectId` puis `entityId`.

`SavedViewService` gère identité stable, historique, archive et CAS `expectedRevision`.

Persistence :

```text
MemorySavedViewStore
SqliteSavedViewStore
V014__saved_views.sql
```

Exports :

```text
JSON      CanonicalJsonSerializer
CSV       UTF-8 + escaping déterministe
Markdown  table stable
```

Documentation détaillée : [Query Platform](QUERY_PLATFORM.md).

## 8. Chemin d’une requête

```text
CLI / MCP / HTTP
       |
       v
parser + validation application
       |
       v
QueryExecutionService / SavedViewService / QueryExportService
       |
       v
application store ports
       |
       v
Memory / SQLite
```

Une règle qui change le sens métier appartient au domaine/application, pas à la surface.

Les objets domaine ne sont pas sérialisés directement ; les vues publiques sont transport-safe avant sérialisation.

## 9. Surfaces M24

CLI :

```text
query execute
views create/list/get/versions/update/archive/execute
export query/view
```

MCP :

```text
execute_query
create_saved_view
list_saved_views
get_saved_view
list_saved_view_versions
update_saved_view
archive_saved_view
execute_saved_view
export_query
export_saved_view
```

HTTP :

```text
POST /api/v1/queries/execute
GET/POST /api/v1/saved-views
GET/PUT /api/v1/saved-views/{id}
GET  /api/v1/saved-views/{id}/versions
POST /api/v1/saved-views/{id}/execute
POST /api/v1/saved-views/{id}/archive
POST /api/v1/saved-views/{id}/export
POST /api/v1/exports
```

OpenAPI : [`../openapi/morpheus-v1-query-m24.yaml`](../openapi/morpheus-v1-query-m24.yaml).

## 10. Budgets M24

```text
encoded expression     <= 16 KiB
AST nodes              <= 128
boolean depth          <= 8
predicates             <= 64
sort fields            <= 8
projection fields      <= 32
page size              <= 500
export rows            <= 10,000
export bytes           <= 10 MiB
saved views / scope    <= 250
saved-view name        <= 160 chars
```

Un dépassement produit une erreur explicite, jamais une troncature présentée comme complète.

## 11. Invariants globaux

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
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
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
optional engine absence != MORPHEUS failure
MORPHEUS facts/rules != JARVIS action sequencing
```

## 12. Workflow de contribution

```text
1. identifier l’invariant et la source de vérité
2. documenter/ADR si nécessaire
3. implémenter le vertical slice
4. ajouter les tests ciblés
5. exécuter les tests module + dépendances
6. exécuter le reactor complet
7. packager/smoker lorsque concerné
8. enregistrer le SHA réellement testé
9. accepter l’ADR seulement après preuve
10. merger uniquement après respect des gates actifs
11. réconcilier l’état documentaire après merge
```

## 13. Commandes essentielles

Gate développeur :

```powershell
.\mvnw.cmd clean test
```

Gate M24 Windows :

```powershell
.\validate-m24.cmd 1.0.0
```

Gate M24 Linux :

```bash
bash ./scripts/validate-m24.sh 1.0.0
```

## 14. Gate M24 de référence

```text
Head exécutable       be69e47da0ae209d2246df9c67bc08caeafb2bb0
PR head docs-only     863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
Merge                 2b483ded10c783fff22c25035db89475c5c9fdaf
Windows               PASS
Linux WSL2            PASS
Tests                 543 PASS
Architecture          221 PASS
Windows coverage      44.2936% line / 38.1166% branch
Linux coverage        44.3037% line / 38.1166% branch
Query DSL             PASS
Saved views           PASS
SQLite V014           PASS
JSON/CSV/Markdown     PASS
Packaging Windows     PASS
Packaging Linux       PASS
SBOM/provenance       PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0092              Acceptée — M24
```

## 15. Où documenter une modification ?

| Modification | Documentation attendue |
|---|---|
| invariant métier | architecture + tests + éventuellement ADR |
| nouveau provider | architecture + provider contract + tests |
| nouveau contrat portfolio | `PORTFOLIO_INTELLIGENCE.md` + tests + surfaces |
| nouveau contrat Query DSL/view/export | `QUERY_PLATFORM.md` + tests + surfaces |
| nouveau contrat HTTP | `API.md` + OpenAPI + tests de contrat |
| nouveau tool MCP | `MCP.md` + JSON Schema + tests |
| packaging | `BUILD_AND_TEST.md` + `distribution/README.md` |
| nouveau jalon | roadmap + validation + ADR/index |

## 16. Sources de vérité

- [`../governance/ROADMAP.md`](../governance/ROADMAP.md) — état courant ;
- [`../roadmap/POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md) — trajectoire active 1.x ;
- [`../roadmap/M24_EXECUTION.md`](../roadmap/M24_EXECUTION.md) — plan final M24 ;
- [`../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md`](../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md) — décision M24 ;
- [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md) — preuve exacte ;
- [`QUERY_PLATFORM.md`](QUERY_PLATFORM.md) — architecture M24.