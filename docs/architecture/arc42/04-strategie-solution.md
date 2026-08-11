# §4 — Stratégie de solution

> **Sources** : `docs/developer/ARCHITECTURE.md`, `docs/architecture/overview.md`,
> `docs/adr/README.md`, ADR-0001 à ADR-0027, `morpheus-architecture-tests/`.

---

## 4.1 Principes architecturaux

| # | Principe | Formulation | ADR de référence |
|---|----------|-------------|-----------------|
| P-1 | **Local-first** | Le système est pleinement fonctionnel sans réseau, sans LLM, sans service cloud | ADR-0004 |
| P-2 | **Port-adapter** | Le domaine ne dépend d'aucune technologie d'infrastructure ; les stores et providers sont des adaptateurs derrière des ports | ADR-0001, ADR-0003 |
| P-3 | **Read-first** | Les providers lisent ; l'écriture est une capacité optionnelle déclarée et négociée | ADR-0008, ADR-0011 |
| P-4 | **Exactitude avant quantité** | Aucun fait n'est produit sans source vérifiable ; les inférences sont explicitement étiquetées | ADR-0004 |
| P-5 | **Identité stable** | `DomainIdentity` (UUIDv7 opaque) est indépendante de la version, du locator et de la référence externe | ADR-0009, ADR-0015 |
| P-6 | **Snapshots atomiques** | L'activation d'un snapshot est atomique ; pas de vue partielle | ADR-0012, ADR-0033 |
| P-7 | **Surface parity** | CLI, MCP et HTTP exposent les mêmes capacités (vérifiable via `contracts/public-surfaces.tsv`) | ADR-0059 |
| P-8 | **Validation avant acceptation** | Un ADR n'est accepté qu'après preuve par tests reproductibles | Convention ADR |
| P-9 | **Migrations explicites** | Le schéma SQLite évolue via migrations versionnées et vérifiées par SHA-256 ; jamais de ALTER implicite | ADR-0021 |
| P-10 | **Résilience aux intégrations** | La panne d'un adaptateur externe (MINOS, NEXUS) ne dégrade pas la disponibilité des faits locaux | ADR-0007, invariants ADR |

---

## 4.2 Style de décomposition

Le système est décomposé en **architecture en couches hexagonale** (Ports & Adapters) avec 16 modules Maven :

```
[ Couche Adaptateurs ]
    CLI        ← morpheus-cli
    HTTP API   ← morpheus-api
    MCP STDIO  ← morpheus-mcp
    Providers  ← morpheus-provider-*
    Stores     ← morpheus-store-*
    Intégrations ← morpheus-integration-*

[ Couche Application ]
    Services, orchestration, ports  ← morpheus-application

[ Couche Domaine ]
    Modèle pur, value objects, state machines  ← morpheus-domain
```

Les règles de dépendance sont **enforced automatiquement** par ArchUnit dans
`morpheus-architecture-tests` à chaque build.

---

## 4.3 Technologies structurantes

| Technologie | Rôle | Version | ADR |
|-------------|------|---------|-----|
| Java 21 | Langage et plateforme d'exécution | 21 (LTS) | ADR-0016 |
| Maven | Build, reactor multi-module, gestion des dépendances | 3.9.16 (Wrapper) | ADR-0017 |
| SQLite via `sqlite-jdbc` | Stockage persistant embarqué | 3.53.1.0 | ADR-0018 |
| `jdk.httpserver` | Serveur HTTP local | JDK built-in | ADR-0065 |
| `io.modelcontextprotocol.sdk:mcp` | Protocole MCP STDIO | 2.0.0 | ADR-0062 |
| Jackson (BOM 3.0.3) | Sérialisation JSON | 3.0.3 | — |
| JUnit Jupiter | Tests unitaires | 6.1.0 | — |
| ArchUnit | Tests d'architecture | 1.4.2 | — |
| JaCoCo | Couverture de code | 0.8.15 | — |
| CycloneDX | Génération de SBOM | 2.9.2 | — |
| jpackage | Distribution native portable | JDK built-in | ADR-0061 |
| Inno Setup | Installateur Windows | 7.0.2 | ADR-0027 |
| GitHub Actions | CI/CD | — | — |

---

## 4.4 Mécanismes liés aux objectifs qualité

| Objectif qualité | Mécanisme(s) | Vérification |
|-----------------|--------------|--------------|
| Exactitude | Séparation CURRENT/PROPOSED/HISTORICAL ; snapshots atomiques ; faits tracés | Tests de contrat `morpheus-architecture-tests` |
| Maintenabilité | Couches ArchUnit ; 96 ADR ; SBOM CycloneDX ; tests par milestone | Gate CI `validate-mN` |
| Portabilité | jpackage (JVM embarquée) ; SQLite local ; pas de dépendances cloud | CI matrix Ubuntu + Windows |
| Extensibilité | Port-adapter ; SDK provider externe (`morpheus-provider-sdk`) ; capability negotiation (ADR-0011) | `morpheus-provider-testkit` |
| Résilience | Adaptateurs optionnels ; séparation processus externe (STDIO) ; isolation SQLite WAL | Tests `SqliteConcurrencyHardeningTest` |

---

## 4.5 Liens vers les ADR structurants

| Décision | ADR |
|----------|-----|
| Domaine indépendant | [ADR-0001](../../../adr/0001-morpheus-owned-domain.md) |
| Local-first sans LLM | [ADR-0004](../../../adr/0004-local-first-no-llm-core.md) |
| Architecture hexagonale / port-adapter | [ADR-0001](../../../adr/0001-morpheus-owned-domain.md), [ADR-0003](../../../adr/0003-specification-knowledge-store.md) |
| SQLite derrière port | [ADR-0018](../../../adr/0018-sqlite-initial-persistent-store.md) |
| MCP STDIO natif | [ADR-0062](../../../adr/0062-official-java-mcp-sdk-and-native-stdio.md) |
| Distribution native-first | [ADR-0027](../../../adr/0027-native-first-container-supported-distribution.md) |
| SDK provider externe | [ADR-0090](../../../adr/0090-provider-sdk-plugin-discovery-platform.md) |
