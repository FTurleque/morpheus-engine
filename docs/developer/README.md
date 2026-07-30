# Guide développeur MORPHEUS

Cette documentation décrit la baseline **M26 validée, intégrée et qualifiée Windows + Linux/WSL** de MORPHEUS `1.0.0`. Elle sert de point d’entrée pour importer le projet, comprendre le découpage Maven, préserver les frontières d’architecture et exécuter les gates de validation.

```text
M26 qualified     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 PR head       36378842e3ef41e379ade17f869b0939d052bbbc
M26 merge         49016a18c844a78ec864235c544d82d487da7c8a
Tests             579 PASS Windows + Linux
Architecture      234 PASS Windows + Linux
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

Le gate M26 qualifié parcourt **17 modules reactor SUCCESS**.

## 4. Architecture des modules

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne connaissent aucun type provider-specific, transport-specific ou plugin externe.

M26 ne crée pas un nouveau module Maven : la façade remote vit dans `morpheus-api`, le launcher/admin dans `morpheus-cli` et la maintenance SQLite dans `morpheus-store-sqlite`.

| Module | Responsabilité |
|---|---|
| `morpheus-domain` | modèle métier, value objects, invariants purs |
| `morpheus-application` | use cases, ports, lifecycle, composition, portfolio, query, policies |
| `morpheus-provider-sdk` | SPI public plugin, metadata, discovery, compatibility, activation |
| `morpheus-provider-testkit` | assertions contractuelles pour auteurs de plugins |
| `morpheus-provider-reference` | plugin externe de référence |
| `morpheus-provider-openspec` | découverte/lecture/normalisation OpenSpec |
| `morpheus-provider-markdown` | lecture Markdown structuré |
| `morpheus-provider-synthetic` | provider contrôlé pour tests |
| `morpheus-store-memory` | implémentations mémoire des ports |
| `morpheus-store-sqlite` | persistance versionnée V001→V015 + maintenance M26 |
| `morpheus-integration-minos` | client MINOS via MCP STDIO |
| `morpheus-integration-nexus` | client NEXUS via MCP STDIO |
| `morpheus-mcp` | adapter serveur MCP |
| `morpheus-api` | API locale `/api/v1` + façade HTTPS remote M26 |
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

## 6. Portfolio Specification Intelligence

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

Documentation : [Portfolio Specification Intelligence](PORTFOLIO_INTELLIGENCE.md).

## 7. Query Platform

M24 ajoute dans `morpheus-application` :

```text
query.dsl
query.saved
query.export
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

Persistence :

```text
MemorySavedViewStore
SqliteSavedViewStore
V014__saved_views.sql
```

Documentation : [Query Platform](QUERY_PLATFORM.md).

## 8. Policy Platform

M25 ajoute des Policy Packs provider-neutral :

```text
stable pack/rule/version identity
immutable versions
project / portfolio scopes
APPLICABLE / NOT_APPLICABLE / UNKNOWN
PASS / WARN / BLOCK / UNKNOWN
overrides + CAS + provenance
read-only dry-run
append-only audit
```

Persistence :

```text
MemoryPolicyPackStore
SqlitePolicyPackStore
V015__policy_packs.sql
```

Documentation : [Policy Platform](POLICY_PLATFORM.md).

## 9. Remote Server Platform M26

M26 conserve `MorpheusHttpServer` comme serveur local interne et ajoute une façade `MorpheusRemoteHttpServer` explicite.

```text
REMOTE request
   |
   v
JDK HttpsServer
   |
   +-- TLS PKCS12
   +-- Bearer authentication
   +-- READ/WRITE/ADMIN authorization
   +-- security headers
   +-- bounded concurrency / 429
   +-- status / backup admin surface
   |
   v
loopback MorpheusHttpServer
```

Le header `Authorization` est consommé par la façade et **jamais forwardé** au hop loopback.

### Frontière TLS

```text
remote != plaintext HTTP
remote startup without TLS material => FAIL
TLS protocols = TLSv1.3 + TLSv1.2
```

Le mot de passe PKCS12 ne doit pas être fourni comme argument CLI ni apparaître dans status/metrics.

### Authentification

Le fichier d’identités persiste :

```text
principal|role|sha256(token)
```

Le token clair n’est jamais persisté. La comparaison de hash utilise `MessageDigest.isEqual`.

### Autorisation

```text
READ < WRITE < ADMIN
```

L’autorisation est fail-closed. Les POST read-only sont explicitement classifiés ; une route inconnue n’obtient jamais WRITE/ADMIN par défaut.

### Concurrence

```text
maxConcurrentRequests default 64
range 1..512
saturation -> HTTP 429
```

Le listen backlog HTTPS est distinct de la limite du sémaphore applicatif afin d’éviter qu’une saturation à faible limite ne se transforme en refus TCP avant traitement.

### Maintenance SQLite

Backup :

```text
VACUUM INTO
PRAGMA integrity_check
schema_migrations version check
SHA-256
```

Restore :

```text
offline only
explicit --confirm
server lease exclusion
staging + atomic move when supported
future schema > V015 rejected
```

M26 n’ajoute aucune V016 : TLS/auth/limites/backups restent de la configuration/opérabilité, pas de la vérité métier.

Documentation détaillée : [Remote Server Platform](REMOTE_SERVER_PLATFORM.md).

## 10. Surfaces M26

CLI locale :

```text
server identity create
server backup create
server backup verify
server restore --confirm
api --remote
```

HTTP remote :

```text
GET  /api/v1/server/status       READ
POST /api/v1/server/backups      ADMIN
GET  /api/v1/metrics             ADMIN
```

Le provisioning d’identité et le restore sont intentionnellement absents du HTTP/MCP.

OpenAPI : [`../openapi/morpheus-v1-remote-m26.yaml`](../openapi/morpheus-v1-remote-m26.yaml).

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
constraint text != executable policy
policy recommendation != applied mutation
dry-run != mutation
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
multi-client concurrency != unbounded concurrency
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

Gate M26 Windows :

```powershell
.\validate-m26.cmd 1.0.0
```

Gate M26 Linux :

```bash
bash ./scripts/validate-m26.sh 1.0.0
```

## 14. Gate M26 de référence

```text
Head exécutable       bf481b24054c4577144b4cb2ede2bdbc4d9974a2
PR head docs-only     36378842e3ef41e379ade17f869b0939d052bbbc
Merge                 49016a18c844a78ec864235c544d82d487da7c8a
Windows               PASS
Linux WSL             PASS
Tests                 579 PASS
Architecture          234 PASS
Windows coverage      44.3507% line / 37.8842% branch
Linux coverage        44.3527% line / 37.8842% branch
Local-first           PASS
TLS/auth/RBAC         PASS
Bounded concurrency   PASS / HTTP 429
Secret disclosure     NONE
Backup/restore        PASS
SQLite                V015
Packaging Windows     PASS
Packaging Linux       PASS
SBOM/provenance       PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0094              Acceptée — M26
```

Preuve : [`../validation/VALIDATION_M26.md`](../validation/VALIDATION_M26.md).

## 15. Où documenter une modification ?

| Modification | Documentation attendue |
|---|---|
| invariant métier | architecture + tests + éventuellement ADR |
| nouveau provider | architecture + provider contract + tests |
| nouveau contrat portfolio | `PORTFOLIO_INTELLIGENCE.md` + tests + surfaces |
| nouveau contrat Query DSL/view/export | `QUERY_PLATFORM.md` + tests + surfaces |
| nouveau contrat policy | `POLICY_PLATFORM.md` + tests + surfaces |
| nouveau contrat remote/server | `REMOTE_SERVER_PLATFORM.md` + OpenAPI + security tests |
| nouveau contrat HTTP | `API.md` + OpenAPI + tests de contrat |
| nouveau tool MCP | `MCP.md` + JSON Schema + tests |
| packaging | `BUILD_AND_TEST.md` + `distribution/README.md` |
| nouveau jalon | roadmap + validation + ADR/index |

## 16. Sources de vérité

- [`../governance/ROADMAP.md`](../governance/ROADMAP.md) — état courant ;
- [`../roadmap/POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md) — trajectoire active 1.x ;
- [`../roadmap/M26_EXECUTION.md`](../roadmap/M26_EXECUTION.md) — plan M26 final ;
- [`../validation/VALIDATION_M26.md`](../validation/VALIDATION_M26.md) — preuve exacte ;
- [`../adr/0094-optional-team-remote-server-mode.md`](../adr/0094-optional-team-remote-server-mode.md) — décision remote/server ;
- [`REMOTE_SERVER_PLATFORM.md`](REMOTE_SERVER_PLATFORM.md) — architecture M26.
