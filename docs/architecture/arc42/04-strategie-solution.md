# §4 — Stratégie de solution

> **Sources actives** : `pom.xml`, `docs/adr/`, `contracts/public-surfaces.tsv`,
> `morpheus-architecture-tests/` et code du HEAD `develop`.

---

## 4.1 Principes architecturaux

| # | Principe | Formulation | Référence |
|---|----------|-------------|-----------|
| P-1 | **Local-first** | Le cœur fonctionne sans réseau, LLM ni service cloud obligatoire | ADR-0004 |
| P-2 | **Ports & Adapters** | Domaine et application possèdent leurs contrats ; les technologies restent aux frontières | ADR-0001, ADR-0003 |
| P-3 | **Read-first / controlled write** | Une capacité de lecture n'implique jamais une capacité d'écriture ; toute mutation est explicite et contrôlée | ADR-0008, ADR-0083 |
| P-4 | **Facts before inference** | Faits, inférences, heuristiques et suggestions ne sont jamais confondus | ADR-0004, ADR-0095 |
| P-5 | **Identité stable** | `DomainIdentity` est indépendante de la version, du locator et des références externes | ADR-0009, ADR-0015 |
| P-6 | **Snapshots atomiques** | Un snapshot publié est cohérent ; une activation partielle n'est pas observable | ADR-0012, ADR-0033 |
| P-7 | **Convergence des surfaces** | Les capacités publiques sont suivies entre CLI, MCP et HTTP sans imposer une forme de transport identique | `contracts/public-surfaces.tsv` |
| P-8 | **Migrations vérifiables** | Les migrations SQLite sont versionnées, checksummées et appliquées explicitement | ADR-0021 |
| P-9 | **Intégrations optionnelles** | MINOS et NEXUS sont isolés derrière des ports et ne sont pas requis pour les faits locaux | ADR-0007 |
| P-10 | **Native MCP conservateur** | Le câblage des clients MCP est opt-in et n'écrase pas une configuration étrangère | ADR-0096 |

---

## 4.2 Décomposition

MORPHEUS 1.2.0 est un reactor Maven de **16 modules** :

```text
Adapters / surfaces
  morpheus-cli
  morpheus-api
  morpheus-mcp

Adapters / providers
  morpheus-provider-sdk
  morpheus-provider-testkit
  morpheus-provider-reference
  morpheus-provider-openspec
  morpheus-provider-markdown
  morpheus-provider-synthetic

Adapters / stores
  morpheus-store-memory
  morpheus-store-sqlite

Adapters / integrations
  morpheus-integration-minos
  morpheus-integration-nexus

Application
  morpheus-application

Domain
  morpheus-domain

Architecture qualification
  morpheus-architecture-tests
```

Sens de dépendance essentiel :

```text
adapters -> application -> domain
```

Les frontières sont vérifiées par les tests d'architecture ; les types
provider-specific et infrastructure-specific ne doivent pas contaminer les
contrats métier.

---

## 4.3 Technologies structurantes — baseline 1.2.0

| Technologie | Rôle | Version / baseline |
|-------------|------|--------------------|
| Java | Runtime et langage | 21 |
| Maven Wrapper | Build multi-module | 3.9.16 |
| SQLite JDBC | Persistance embarquée | 3.53.2.0 |
| Jackson | Sérialisation / parsing JSON | BOM 3.1.5 |
| MCP SDK Java | MCP STDIO | 2.0.0 |
| JUnit Jupiter | Tests | 6.1.0 |
| ArchUnit | Tests d'architecture | 1.4.2 |
| JaCoCo | Couverture | 0.8.15 |
| CycloneDX | SBOM | 2.9.2 |
| OWASP Dependency-Check | SCA locale | 12.2.2 |
| `jdk.httpserver` | HTTP local/remote | fourni par le JDK |
| jpackage | Distribution avec runtime embarqué | fourni par le JDK |

Les versions autoritatives restent celles de `pom.xml` ; cette table décrit la
baseline au moment de la réconciliation documentaire.

---

## 4.4 Mécanismes liés aux objectifs qualité

| Objectif | Mécanismes | Vérification |
|----------|------------|--------------|
| Exactitude | états temporels séparés, provenance, evidence, snapshots atomiques | tests domaine/application/architecture |
| Sécurité | validation bornée, workspace confinement, SCA, contrôle des JAR externes | D2 + tests de régression sécurité |
| Maintenabilité | modules explicites, ADR, ArchUnit, dependency hygiene | build Maven + tests d'architecture |
| Portabilité | Java 21, runtime embarqué, SQLite local | qualification Windows + Linux |
| Extensibilité | Provider SDK, ports d'intégration, capability negotiation | provider testkit + tests d'intégration |
| Résilience | adaptateurs optionnels, isolation processus, transactions SQLite | tests de panne/concurrence |

---

## 4.5 ADR structurants

| Décision | ADR |
|----------|-----|
| Domaine MORPHEUS indépendant | [ADR-0001](../../adr/0001-morpheus-owned-domain.md) |
| Local-first sans LLM obligatoire | [ADR-0004](../../adr/0004-local-first-no-llm-core.md) |
| Store derrière port | [ADR-0003](../../adr/0003-specification-knowledge-store.md) |
| SQLite initial | [ADR-0018](../../adr/0018-sqlite-initial-persistent-store.md) |
| Distribution native-first | [ADR-0027](../../adr/0027-native-first-container-supported-distribution.md) |
| MCP STDIO officiel | [ADR-0062](../../adr/0062-official-java-mcp-sdk-and-native-stdio.md) |
| Provider SDK | [ADR-0090](../../adr/0090-provider-sdk-plugin-discovery-platform.md) |
| Remote server optionnel | [ADR-0094](../../adr/0094-optional-team-remote-server-mode.md) |
| Reasoning fondé sur preuves | [ADR-0095](../../adr/0095-evidence-backed-assisted-reasoning.md) |
| Intégration native des clients MCP | [ADR-0096](../../adr/0096-conservative-native-mcp-client-integration.md) |
