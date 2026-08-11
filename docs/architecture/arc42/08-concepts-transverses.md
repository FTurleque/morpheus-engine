# §8 — Concepts transverses

> **Sources** : `docs/developer/ARCHITECTURE.md`, `docs/developer/OPERABILITY.md`,
> `docs/developer/PRODUCTION_INTEGRITY.md`, `docs/adr/README.md` (invariants),
> ADR-0004, ADR-0009, ADR-0012, ADR-0015, ADR-0018, ADR-0021, ADR-0062,
> `morpheus-store-sqlite/` (SqliteDatabaseSecurity, SqliteServerMaintenance),
> `morpheus-api/` (MorpheusRemoteRole, MorpheusRemoteIdentityFile).

---

## 8.1 Identité et accès

### Identité de domaine

Chaque entité MORPHEUS possède une identité stable de type `DomainIdentity`
(UUIDv7, opaque). Elle est indépendante de la version (`EntityVersionId`), du
locator (`SourceLocator`) et de la référence externe (`ExternalReference`).
Ce principe est enforced par les tests `DomainIdentityTest` et par l'invariant
`DomainIdentity != EntityVersionId != SourceLocator != ExternalReference`.

### Authentification et RBAC (mode remote uniquement)

En mode local, aucune authentification n'est requise (accès loopback `127.0.0.1`).

En mode remote (opt-in — ADR-0094) :
- TLS 1.3/1.2 obligatoire.
- Authentification Bearer token (`MorpheusRemoteIdentityFile`).
- Trois rôles : `READ`, `WRITE`, `ADMIN` (classes `MorpheusRemoteRole`).
- Règle : `READ != WRITE != ADMIN` et `authentication != authorization`.

---

## 8.2 Sécurité

| Aspect | Mécanisme | Preuve |
|--------|-----------|--------|
| Isolation de la base de données | `SqliteDatabaseSecurity` applique les permissions de fichier OS | Classe `SqliteDatabaseSecurity` |
| Protection des mutations | Opérations lifecycle controlées — traçabilité obligatoire | `V011__controlled_lifecycle_mutations.sql` ; ADR-0083 |
| Pas d'injection SQL | Requêtes paramétrées uniquement dans les stores SQLite | Convention — Hypothèse à valider par revue de code |
| Aucune donnée en URL | L'API REST ne place pas de données sensibles en query string | Convention — Hypothèse à valider |
| SBOM | CycloneDX 1.6 généré à chaque build | `cyclonedx-maven-plugin 2.9.2` |

---

## 8.3 Données

### Modèle de données central

| Concept | Description | Table(s) SQLite |
|---------|-------------|-----------------|
| `Project` | Unité de gestion d'un workspace | `projects` (V001) |
| `KnowledgeSnapshot` | Vue versionnée et immuable des faits d'un projet | `knowledge_snapshots` (V001) |
| `Requirement` | Exigence versionnée | `versioned_requirements` (V004) |
| `TraceabilityLink` | Lien traçabilité typé (taxonomie contrôlée) | `snapshot_traceability` (V005) |
| `ExternalReference` | Référence externe (potentiellement non résolue) | `snapshot_external_references` (V006) |
| `PolicyPack` | Ensemble de règles de gouvernance | `policy_packs` (V015) |
| `Portfolio` | Agrégat multi-projets | `portfolios` (V013) |
| `SavedView` | Query DSL sauvegardée | `saved_views` (V014) |

### Migrations de schéma

15 migrations versionnées (`V001` à `V015`) exécutées séquentiellement par
`SqliteSchemaManager`. Chaque migration est vérifiée par checksum SHA-256 au
démarrage. Un mismatch est une erreur fatale.

### Invariants de données critiques

- `PROPOSED never leaks into CURRENT`
- `SpecificationVersion != KnowledgeSnapshot`
- Activation de snapshot = atomique (pas de vue partielle possible)
- `conflict != silent last-write-wins`
- `cross-project identity != source path`

---

## 8.4 Interfaces et versionnement

| Surface | Version | Stratégie |
|---------|---------|-----------|
| HTTP API | v1 (1.8.0) | URI versionnée `/api/v1/` ; pas de breaking changes sans montée de version majeure |
| MCP STDIO | 2.0.0 (SDK) | Protocol MCP 2.0.0 ; tools nommés avec préfixe `morpheus_` |
| CLI | Stable depuis M9 (ADR-0059) | Commandes verbales ; rétrocompatibilité assurée |
| SQLite Schema | V015 (courant) | Migrations forward-only ; jamais de modification d'une migration publiée |
| Provider SDK | Versionnée dans `morpheus-provider-sdk/pom.xml` | Semver ; breaking changes = nouveau majeur |

---

## 8.5 Gestion des erreurs

| Type d'erreur | Comportement | Classe / Convention |
|---------------|-------------|---------------------|
| Échec d'adaptateur externe (MINOS, NEXUS) | Mode dégradé explicite ; réponse avec `warning` | `MinosIntegrationException`, `NexusIntegrationException` |
| Migration SQLite — checksum mismatch | Erreur fatale au démarrage ; pas de tentative de réparation | `SqliteSchemaManager` |
| Provider — résultats partiels | Résultats partiels explicites (ADR-0028) ; jamais de silence | Contrat `UnifiedProviderReadContract` |
| Transition lifecycle invalide | Exception de domaine ; pas d'état incohérent | Machine d'état `ChangeLifecycleStateMachine` |
| Erreur HTTP | `ApiFailure` structuré avec code et message | `ApiFailure` dans `morpheus-api` |
| Conflit de composition | `conflict != silent last-write-wins` — rapporté explicitement | `morpheus-application/composition` |

---

## 8.6 Résilience

| Scénario | Mécanisme |
|----------|-----------|
| MINOS indisponible | Pas d'échec MORPHEUS ; résultat sans code intelligence, warning explicite |
| NEXUS indisponible | Idem — adapter absence != MORPHEUS failure |
| Crash du processus | SQLite WAL garantit la cohérence (pas de write partiel) |
| Démarrage avec schema obsolète | `SqliteSchemaManager` applique les migrations manquantes |
| Snapshot corrompu | L'état `FAILED` est persisté ; le snapshot précédent reste `ACTIVE` |

---

## 8.7 Configuration

La configuration runtime est résolue dans l'ordre de priorité :
1. Argument CLI explicite.
2. Variable d'environnement (`MORPHEUS_DATA_DIR`, etc.).
3. Valeur par défaut selon la plateforme (`%LOCALAPPDATA%` / `$XDG_DATA_HOME`).

Pas de fichier de configuration YAML/TOML embarqué dans le processus —
la configuration est déclarative via l'environnement.

---

## 8.8 Observabilité

| Mécanisme | Description | Preuve |
|-----------|-------------|--------|
| Health endpoint | `GET /api/v1/health` | `docs/openapi/morpheus-v1.yaml` |
| Readiness endpoint | `GET /api/v1/readiness` | Idem |
| Metrics endpoint | `GET /api/v1/metrics` | Idem |
| Version endpoint | `GET /api/v1/version` | Idem |
| Status intégrations | `GET /api/v1/integrations/minos/status`, `.../nexus/status` | Idem |
| Logging | `slf4j-nop` en production (aucune sortie log sur stdout MCP) | `pom.xml` ; ADR-0062 |
| JaCoCo | Couverture agrégée générée à `mvnw verify` | `.github/workflows/ci.yml` |
| SBOM | CycloneDX 1.6 | `mvnw verify` |
| Backups | `SqliteServerMaintenance` — `POST /api/v1/server/backups` | Classe `SqliteServerMaintenance` |
| Provenance de build | `write-build-provenance.ps1/.sh` — manifest SHA Git | `distribution/` |

---

## 8.9 Persistance

Le stockage est un fichier SQLite unique, en mode WAL (Write-Ahead Logging)
pour la concurrence lecture/écriture. Pas de pool de connexions — un processus
JVM accède à un seul fichier.

L'abstraction `SpecificationKnowledgeStore` (ADR-0003) permet la substitution
du backend (ex. PostgreSQL) sans modifier le domaine ni les services
applicatifs.

---

## 8.10 Messaging

Pas de broker de messages asynchrones. La communication inter-composants est
synchrone (appels Java directs). La communication avec les systèmes externes
(MINOS, NEXUS) est pseudo-synchrone via MCP STDIO (request/response dans le
processus fils).

---

## 8.11 Performance

| Gate | Cible | Vérification |
|------|-------|-------------|
| Latence sync M19 | Non déterminé — Hypothèse à valider via tests de performance | `morpheus-architecture-tests/m19/` |
| Taille base de données | SQLite 1 fichier par workspace ; pas de limite documentée | — |
| Démarrage JVM | Mode portable : JVM embarquée, démarrage attendu < 2 s — Hypothèse à valider | `MorpheusCliTest` |

---

## 8.12 Concurrence

- Un seul processus JVM accède à la base SQLite (mode WAL).
- En mode remote, des requêtes HTTP concurrentes sont possibles ; le modèle
  de concurrence SQLite est le verrou au niveau connexion.
- Tests `SqliteConcurrencyHardeningTest` valident les scénarios multi-threads.

---

## 8.13 Tests

| Niveau | Framework | Modules |
|--------|-----------|---------|
| Tests unitaires domaine | JUnit Jupiter 6.1.0 | `morpheus-domain` |
| Tests d'intégration applicatifs | JUnit Jupiter + mémoire store | `morpheus-application` |
| Tests de contrat API | JUnit Jupiter + serveur HTTP embarqué | `morpheus-api` |
| Tests de contrat MCP | JUnit Jupiter + STDIO fixture | `morpheus-mcp` |
| Tests de schéma SQLite | JUnit Jupiter + SQLite en fichier temporaire | `morpheus-store-sqlite` |
| Tests d'architecture | ArchUnit 1.4.2 | `morpheus-architecture-tests` |
| Tests provider | Provider testkit JUnit | `morpheus-provider-testkit` |
| Couverture | JaCoCo agrégé | Rapport dans `target/` |

---

## 8.14 Déploiement et rollback

| Opération | Mécanisme |
|-----------|-----------|
| Mise à jour | Remplacement du binaire portable ; migrations SQLite automatiques au prochain démarrage |
| Rollback applicatif | Revenir au binaire précédent ; les migrations SQLite sont forward-only — pas de rollback de schéma |
| Rollback logique | Snapshot précédent peut être réactivé (ADR-0036) |
| Backup avant mise à jour | Recommandé via `POST /api/v1/server/backups` avant installation |
| Gate CI | `validate-mN.sh/.ps1` obligatoire avant fusion sur `main` |
