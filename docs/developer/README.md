# Guide développeur MORPHEUS

Cette documentation décrit l’état du code après intégration de M14.

## Prérequis

```text
Java   >= 21
Maven  3.9.16+ via Maven Wrapper
Git
```

Le build parent compile avec `release=21`.

## Modules Maven

```text
morpheus-domain
morpheus-application
morpheus-provider-openspec
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
```

Responsabilités principales :

| Module | Responsabilité |
|---|---|
| `morpheus-domain` | modèle métier et invariants de domaine |
| `morpheus-application` | use cases, ports et services applicatifs |
| `morpheus-provider-*` | lecture/normalisation des sources |
| `morpheus-store-*` | implémentations de persistance |
| `morpheus-integration-minos` | résolution code MINOS via MCP STDIO |
| `morpheus-integration-nexus` | contexte technique NEXUS via MCP STDIO |
| `morpheus-mcp` | adapter serveur MCP read-only |
| `morpheus-api` | adapter HTTP `/api/v1` |
| `morpheus-cli` | composition root et launcher officiel |
| `morpheus-architecture-tests` | contrats cross-module et règles ArchUnit |

## Lire ensuite

- [Architecture](ARCHITECTURE.md)
- [Build, tests et validation](BUILD_AND_TEST.md)
- [API HTTP](API.md)
- [MCP](MCP.md)
- [Intégrations cross-engine](INTEGRATIONS.md)

## Principes de contribution

```text
document first
then decide
then implement
prove before validate
merge after explicit authorization
```

Lorsqu’une décision dépend d’une hypothèse technique, l’ADR reste proposée jusqu’à obtention d’une preuve reproductible.

## Invariants à préserver

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
optional engine absence != MORPHEUS failure
live external observation != snapshot mutation
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
MORPHEUS facts/rules != JARVIS action sequencing
```

## Sources de vérité

- [`docs/governance/ROADMAP.md`](../governance/ROADMAP.md) : état des jalons ;
- [`docs/adr/`](../adr/) : décisions d’architecture ;
- [`docs/validation/`](../validation/) : preuves de gate C0 et M0 à M14 ;
- [`docs/openapi/morpheus-v1.yaml`](../openapi/morpheus-v1.yaml) : contrat API machine-readable ;
- tests d’architecture : règles exécutables de dépendance.