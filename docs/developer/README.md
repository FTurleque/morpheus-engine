# Guide développeur MORPHEUS

Cette documentation décrit la baseline **M23 validée, intégrée et qualifiée Windows + Linux** de MORPHEUS `1.0.0`. Elle sert de point d’entrée pour importer le projet, comprendre le découpage Maven, préserver les frontières d’architecture et exécuter les gates de validation.

```text
M23 executable  04a906e9d5858292ed0f0f1bec65246fef91ed63
M23 merge       88355b69c493677c8689eecad214fb00d283359b
Tests           507 PASS Windows + Linux
Architecture    195 PASS Windows + Linux
```

## 1. Prérequis

```text
Java   >= 21
Maven  3.9.16+ via Maven Wrapper
Git
```

Le build parent compile avec `release=21`.

### Windows

```powershell
.\mvnw.cmd --version
```

### Linux/macOS

```bash
./mvnw --version
```

## 2. Import IntelliJ IDEA

MORPHEUS est un projet Maven multi-module. Le `pom.xml` racine doit être chargé comme projet Maven.

Ne pas créer les sous-modules manuellement : ils sont définis par le reactor Maven.

## 3. Vue du dépôt M23

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

Le gate M23 qualifié parcourt **17 modules reactor SUCCESS**.

## 4. Architecture des modules

```mermaid
flowchart TB
    DOMAIN[morpheus-domain]
    APP[morpheus-application]
    SDK[morpheus-provider-sdk]
    KIT[morpheus-provider-testkit]
    REF[morpheus-provider-reference]
    OPEN[morpheus-provider-openspec]
    MD[morpheus-provider-markdown]
    SYN[morpheus-provider-synthetic]
    MEM[morpheus-store-memory]
    SQL[morpheus-store-sqlite]
    MINOS[morpheus-integration-minos]
    NEXUS[morpheus-integration-nexus]
    MCP[morpheus-mcp]
    API[morpheus-api]
    CLI[morpheus-cli]
    ARCH[morpheus-architecture-tests]

    APP --> DOMAIN
    SDK --> APP
    KIT --> SDK
    REF --> SDK
    OPEN --> APP
    MD --> APP
    SYN --> APP
    MEM --> APP
    SQL --> APP
    MINOS --> APP
    NEXUS --> APP
    MCP --> APP
    API --> APP
    CLI --> APP
    CLI --> SDK
    CLI --> OPEN
    CLI --> MD
    CLI --> SQL
    ARCH -. vérifie .-> DOMAIN
    ARCH -. vérifie .-> APP
    ARCH -. vérifie .-> SDK
```

Principe :

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne connaissent aucun type provider-specific ni aucun plugin externe.

M23 ne crée pas un nouveau module Maven : le domaine portfolio vit dans `morpheus-domain`, les use cases et ports dans `morpheus-application`, les stores dans Memory/SQLite et les adapters dans CLI/MCP/API.

## 5. Responsabilités

| Module | Responsabilité | À ne pas y mettre |
|---|---|---|
| `morpheus-domain` | modèle métier, value objects, invariants purs, identités portfolio | SQLite, HTTP, CLI, MCP, provider-specific |
| `morpheus-application` | use cases, ports, lifecycle, composition et portfolio provider-neutral | dépendances vers adapters ou SDK |
| `morpheus-provider-sdk` | SPI public plugin, metadata, discovery, compatibility, activation | logique provider-specific |
| `morpheus-provider-testkit` | assertions contractuelles réutilisables pour auteurs de plugins | règles métier produit |
| `morpheus-provider-reference` | vrai plugin externe de référence | dépendance runtime du launcher |
| `morpheus-provider-openspec` | découverte/lecture/normalisation OpenSpec | règles métier provider-neutral |
| `morpheus-provider-markdown` | discovery/lecture Markdown structuré réel | types Markdown dans domain/application |
| `morpheus-provider-synthetic` | provider contrôlé pour tests | preuve de deuxième provider réel production |
| `morpheus-store-memory` | implémentation mémoire des ports, dont portfolio | règles métier |
| `morpheus-store-sqlite` | persistance versionnée, dont V013 portfolio | logique de décision métier |
| `morpheus-integration-minos` | client MINOS via MCP STDIO | dépendance `com.minos.*` |
| `morpheus-integration-nexus` | client NEXUS via MCP STDIO | ranking/fusion/compression NEXUS |
| `morpheus-mcp` | adapter serveur MCP | politique métier cachée |
| `morpheus-api` | adapter HTTP `/api/v1` | dépendance CLI/MCP |
| `morpheus-cli` | composition root, launcher et UX | règles métier nouvelles |
| `morpheus-architecture-tests` | contrats ArchUnit/cross-module | code de production |

## 6. Provider SDK

Le contrat plugin est :

```java
public interface MorpheusProviderPlugin {
    ProviderPluginMetadata metadata();
    SpecificationProvider createProvider();
    SpecificationContentReader createContentReader();
}
```

Les étapes restent séparées :

```text
metadata discovery
      ↓
compatibility
      ↓
explicit activation
      ↓
SpecificationProvider.probe()
      ↓
SpecificationContentReader.read()
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

## 7. Portfolio Specification Intelligence M23

Le modèle multi-projets repose sur :

```text
PortfolioId
PortfolioMembership
PortfolioFreshness
PortfolioEntityRef
CrossProjectReference
```

`PortfolioEntityRef` conserve toujours le scope projet :

```text
ProjectSpecificationId + entityType + DomainIdentity
```

Invariants principaux :

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
traversal is bounded and explainable
freshness != full destructive rescan
```

Services applicatifs :

```text
PortfolioRegistryService
PortfolioQueryService
PortfolioTraversalService
PortfolioPublicViews
```

Stores :

```text
MemoryPortfolioStore
SqlitePortfolioStore
V013__portfolio_intelligence.sql
```

Le traversal est une BFS déterministe. Le résultat conserve l’ordre de découverte BFS dans un `LinkedHashMap`; il ne doit pas être réordonné après parcours par l’ordre lexical des UUID.

Documentation détaillée : [Portfolio Specification Intelligence](PORTFOLIO_INTELLIGENCE.md).

## 8. Chemin d’une requête

```mermaid
sequenceDiagram
    participant X as CLI / MCP / HTTP
    participant A as Application
    participant P as Port
    participant S as Adapter/store
    participant D as Domain

    X->>A: requête normalisée
    A->>D: invariants / règles
    A->>P: port
    P->>S: implémentation technique
    S-->>A: données
    A-->>X: résultat
```

Une règle qui change le sens métier appartient au domaine/application, pas à la surface.

Les objets domaine M23 ne sont pas sérialisés directement. `PortfolioPublicViews` convertit identités et timestamps en projections transport-safe avant `CanonicalJsonSerializer`, conformément à ADR-0047.

## 9. Invariants globaux

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
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
probe != read
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
optional engine absence != MORPHEUS failure
MORPHEUS facts/rules != JARVIS action sequencing
```

## 10. Workflow de contribution

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

## 11. Commandes essentielles

Gate développeur :

```powershell
.\mvnw.cmd clean test
```

Gate M23 Windows :

```powershell
.\validate-m23.cmd
```

Gate M23 Linux :

```bash
bash ./scripts/validate-m23.sh 1.0.0
```

## 12. Gate M23 de référence

```text
Head exécutable       04a906e9d5858292ed0f0f1bec65246fef91ed63
Merge                 88355b69c493677c8689eecad214fb00d283359b
Windows               PASS
Linux WSL2            PASS
Tests                 507 PASS
Architecture          195 PASS
Windows coverage      46.7034% line / 40.9099% branch
Linux coverage        46.6979% line / 40.9099% branch
Portfolio identity    PASS
Cross-project refs    PASS
Bounded traversal     PASS
SQLite V013           PASS
Packaging Windows     PASS
Packaging Linux       PASS
SBOM/provenance       PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0091              Acceptée — M23
```

## 13. Où documenter une modification ?

| Modification | Documentation attendue |
|---|---|
| invariant métier | architecture + tests + éventuellement ADR |
| nouveau provider intégré | architecture + provider contract + tests + ADR si nécessaire |
| nouveau plugin provider | `PROVIDER_SDK.md` + test kit + manifest/service + tests externes |
| nouveau contrat portfolio | `PORTFOLIO_INTELLIGENCE.md` + tests + surfaces |
| nouveau contrat HTTP | `API.md` + OpenAPI + tests de contrat |
| nouveau tool MCP | `MCP.md` + JSON Schema + tests subprocess |
| nouvelle intégration | `INTEGRATIONS.md` + frontière + smoke |
| packaging | `BUILD_AND_TEST.md` + `distribution/README.md` |
| nouveau jalon | roadmap + validation + ADR/index |

## 14. Sources de vérité

- [`../governance/ROADMAP.md`](../governance/ROADMAP.md) — état courant ;
- [`../roadmap/POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md) — trajectoire active 1.x ;
- [`../roadmap/M23_EXECUTION.md`](../roadmap/M23_EXECUTION.md) — plan final M23 ;
- [`../adr/0091-multi-project-portfolio-intelligence.md`](../adr/0091-multi-project-portfolio-intelligence.md) — décision M23 ;
- [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md) — preuve M23 ;
- [`../openapi/morpheus-v1-portfolio-m23.yaml`](../openapi/morpheus-v1-portfolio-m23.yaml) — routes portfolio ;
- tests d’architecture — frontières exécutables.

## 15. Lire ensuite

- [Architecture détaillée](ARCHITECTURE.md)
- [Portfolio Specification Intelligence](PORTFOLIO_INTELLIGENCE.md)
- [Provider SDK](PROVIDER_SDK.md)
- [Build, tests et validation](BUILD_AND_TEST.md)
- [API HTTP](API.md)
- [Serveur MCP](MCP.md)
- [Intégrations cross-engine](INTEGRATIONS.md)
- [Guide utilisateur](../user/README.md)
- [Portfolios — utilisateur](../user/PORTFOLIOS.md)
