# §9 — Décisions architecturales

> Index de navigation vers les 96 ADR existants dans [`../../adr/`](../../adr/).
> Ce fichier ne recopie pas les ADR — il fournit un index structuré avec
> identifiant, titre, statut, date estimée de milestone et relations de
> remplacement.
>
> **Source** : `docs/adr/README.md`.

---

## 9.1 Convention de statuts

| Statut | Signification |
|--------|--------------|
| **Acceptée** | Décision validée par preuve et tests |
| **Acceptée avec contraintes** | Validée sous conditions explicites |
| **Remplacée** | Supersédée par un ADR ultérieur (lien vers le remplaçant) |
| **Proposée** | Candidate en attente de validation |
| **Rejetée** | Option étudiée puis non retenue |

---

## 9.2 Index des ADR — Fondations de domaine (ADR-0001 à ADR-0015)

| ADR | Titre | Statut | Milestone |
|-----|-------|--------|-----------|
| [ADR-0001](../../../adr/0001-morpheus-owned-domain.md) | Domaine MORPHEUS indépendant des formats/providers | Acceptée avec contraintes | M0 |
| [ADR-0002](../../../adr/0002-openspec-reference-provider.md) | OpenSpec premier provider, sans verrouillage | Acceptée avec contraintes | M0 |
| [ADR-0003](../../../adr/0003-specification-knowledge-store.md) | Persistance derrière `SpecificationKnowledgeStore` | Acceptée | M0 |
| [ADR-0004](../../../adr/0004-local-first-no-llm-core.md) | Cœur local-first sans LLM obligatoire | Acceptée | M0 |
| [ADR-0005](../../../adr/0005-traceability-first-class.md) | Traçabilité first-class | Acceptée | M0 |
| [ADR-0006](../../../adr/0006-current-vs-proposed-state.md) | CURRENT / PROPOSED / HISTORICAL séparés | Acceptée | M0 |
| [ADR-0007](../../../adr/0007-cross-engine-integration.md) | Intégrations cross-engine découplées | Acceptée | M0 |
| [ADR-0008](../../../adr/0008-read-first-write-capability.md) | Providers read-first ; écriture optionnelle | Acceptée | M0 |
| [ADR-0009](../../../adr/0009-stable-domain-identity.md) | Identity != version != locator != external ref | Acceptée | M0 |
| [ADR-0010](../../../adr/0010-traceability-relation-taxonomy.md) | Taxonomie contrôlée de traçabilité | Acceptée avec contraintes | M0 |
| [ADR-0011](../../../adr/0011-provider-capability-negotiation.md) | Sélection par capacités effectives | Acceptée | M0 |
| [ADR-0012](../../../adr/0012-snapshot-versioning-strategy.md) | Snapshots versionnés et activation atomique | Acceptée | M0 |
| [ADR-0013](../../../adr/0013-change-lifecycle-state-machine.md) | Machine d'état des changements | Acceptée avec contraintes | M0 |
| [ADR-0014](../../../adr/0014-defer-production-technology-stack.md) | Stack différée jusqu'aux preuves | Acceptée | C0 |
| [ADR-0015](../../../adr/0015-domain-identity-uuidv7.md) | UUIDv7 canonique opaque | Acceptée | M0 |

---

## 9.3 Index des ADR — Stack technique (ADR-0016 à ADR-0030)

| ADR | Titre | Statut | Milestone |
|-----|-------|--------|-----------|
| [ADR-0016](../../../adr/0016-java-21-production-baseline.md) | Java 21 production baseline | Acceptée | M0 |
| [ADR-0017](../../../adr/0017-maven-build-foundation.md) | Maven + Wrapper, `release=21` | Acceptée avec contraintes | M0/M1 |
| [ADR-0018](../../../adr/0018-sqlite-initial-persistent-store.md) | SQLite backend initial derrière port | Acceptée avec contraintes | M0 |
| [ADR-0019](../../../adr/0019-maven-coordinates-java-namespace.md) | Maven coordinates + namespace Java | Acceptée | M1 |
| [ADR-0020](../../../adr/0020-workspace-root-resolution.md) | Workspace discovery explicit-first | Acceptée | M1 |
| [ADR-0021](../../../adr/0021-sqlite-schema-migrations-foundation.md) | Migrations SQLite explicites/versionnées | Acceptée | M1 |
| [ADR-0022](../../../adr/0022-m2-normalized-content-before-temporal-projection.md) | Normaliser avant projection temporelle | Acceptée | M2 |
| [ADR-0023](../../../adr/0023-persistent-provider-scoped-entity-identity.md) | Identités provider-scoped persistantes | Acceptée | M2 |
| [ADR-0024](../../../adr/0024-m2-change-metadata-normalization.md) | Métadonnées de changement normalisées | Acceptée | M2 |
| [ADR-0025](../../../adr/0025-m2-requirement-delta-normalization.md) | Requirement deltas sans application temporelle | Acceptée | M2 |
| [ADR-0026](../../../adr/0026-optional-external-reference-resolution.md) | Références externes via resolvers optionnels | Acceptée | M2 |
| [ADR-0027](../../../adr/0027-native-first-container-supported-distribution.md) | Distribution native-first et container-supported | Acceptée avec contraintes | Distribution |
| [ADR-0028](../../../adr/0028-unified-provider-read-contract.md) | Contrat de lecture unifié et résultats partiels explicites | Acceptée | M2 |
| [ADR-0029](../../../adr/0029-second-provider-anti-lockin-proof.md) | Second provider synthétique — preuve anti-lock-in | Acceptée | M2 |
| [ADR-0030](../../../adr/0030-defer-normalized-business-persistence-to-m3.md) | Persistance métier complète introduite en M3 | Acceptée | M2 |

---

## 9.4 Index des ADR — Snapshots, lifecycle, traçabilité (ADR-0031 à ADR-0060)

| ADR | Titre | Statut | Milestone |
|-----|-------|--------|-----------|
| [ADR-0031](../../../adr/0031-explicit-temporal-projection-and-entity-version.md) | Projection temporelle explicite | Acceptée | M3 |
| [ADR-0032](../../../adr/0032-explicit-change-lifecycle-state-machine.md) | Machine d'état lifecycle des changements | Acceptée | M3 |
| [ADR-0033](../../../adr/0033-knowledge-snapshot-lifecycle-and-atomic-activation.md) | Lifecycle complet des KnowledgeSnapshot | Acceptée | M3 |
| [ADR-0034](../../../adr/0034-versioned-requirement-persistence.md) | Première persistance métier versionnée | Acceptée | M3 |
| [ADR-0035](../../../adr/0035-explicit-requirement-delta-application-and-promotion.md) | Application, promotion et activation des RequirementDelta | Acceptée | M3 |
| [ADR-0036](../../../adr/0036-published-history-comparison-and-logical-rollback.md) | Historique publié, comparaison et rollback logique | Acceptée | M3 |
| [ADR-0037](../../../adr/0037-traceability-domain-and-controlled-taxonomy.md) | Domaine TraceabilityLink et taxonomie contrôlée | Acceptée | M4 |
| [ADR-0038](../../../adr/0038-snapshot-scoped-traceability-persistence.md) | Persistance traçabilité snapshot-scoped | Acceptée | M4 |
| [ADR-0039](../../../adr/0039-deterministic-traceability-derivation.md) | Dérivation déterministe depuis le modèle normalisé | Acceptée | M4 |
| [ADR-0040](../../../adr/0040-bounded-deterministic-traceability-traversal.md) | Traversée bornée, déterministe et explicable | Acceptée | M4 |
| [ADR-0041](../../../adr/0041-snapshot-external-reference-traceability.md) | Références externes snapshot-scoped | Acceptée | M4 |
| [ADR-0042](../../../adr/0042-final-trace-requirement-query.md) | Porte finale `trace(requirement)` | Acceptée | M4 |
| [ADR-0043](../../../adr/0043-deterministic-requirement-lexical-query.md) | Recherche lexicale déterministe et pagination | Acceptée | M5 |
| [ADR-0044](../../../adr/0044-snapshot-business-content-projection.md) | Projection métier snapshot-scoped | Acceptée | M5 |
| [ADR-0045](../../../adr/0045-deterministic-business-content-queries.md) | Getters/listes métier déterministes | Acceptée | M5 |
| [ADR-0046](../../../adr/0046-change-context-query-aggregation.md) | Agrégation trace/context | Acceptée | M5 |
| [ADR-0047](../../../adr/0047-compact-query-views-and-canonical-json.md) | Vues compactes et JSON canonique | Acceptée | M5 |
| [ADR-0048](../../../adr/0048-quality-findings-and-requirement-coverage.md) | Findings qualité et couverture requirements | Acceptée | M6 |
| [ADR-0049](../../../adr/0049-task-requirement-coverage-and-acceptance-gap.md) | Couverture task→requirement et acceptance gap | Acceptée | M6 |
| [ADR-0050](../../../adr/0050-change-completeness-and-lifecycle-quality.md) | Complétude changement/lifecycle | Acceptée | M6 |
| [ADR-0051](../../../adr/0051-decision-and-external-reference-quality.md) | Qualité décisions/références externes | Acceptée | M6 |
| [ADR-0052](../../../adr/0052-aggregate-quality-report-and-compact-view.md) | Rapport qualité agrégé | Acceptée | M6 |
| [ADR-0053](../../../adr/0053-deterministic-source-inventory-and-incremental-diff.md) | Inventaire SHA-256 et diff incrémental | Acceptée | M7 |
| [ADR-0054](../../../adr/0054-persisted-sync-state-archives-and-freshness.md) | Sync state persisté et fraîcheur | Acceptée | M7 |
| [ADR-0055](../../../adr/0055-local-watcher-and-full-rebuild-fallback.md) | Watcher local + fallback rebuild | Acceptée | M7 |
| [ADR-0056](../../../adr/0056-deterministic-current-proposed-change-analysis.md) | Analyse CURRENT/proposé | Acceptée | M8 |
| [ADR-0057](../../../adr/0057-explicit-bounded-dependency-impact-paths.md) | Chemins d'impact bornés | Acceptée | M8 |
| [ADR-0058](../../../adr/0058-compact-change-analysis-and-canonical-json.md) | Vue compacte d'analyse | Acceptée | M8 |
| [ADR-0059](../../../adr/0059-stable-local-cli-contract.md) | CLI stable | Acceptée | M9 |
| [ADR-0060](../../../adr/0060-conservative-full-snapshot-cli-sync.md) | Sync CLI full snapshot conservateur | Acceptée | M9 |

---

## 9.5 Index des ADR — Distribution, MCP, surfaces (ADR-0061 à ADR-0080)

| ADR | Titre | Statut | Milestone |
|-----|-------|--------|-----------|
| [ADR-0061](../../../adr/0061-self-contained-jpackage-portable-distribution.md) | Distribution portable autonome (jpackage) | Acceptée | M9 |
| [ADR-0062](../../../adr/0062-official-java-mcp-sdk-and-native-stdio.md) | Java MCP SDK 2.0.0 + STDIO | Acceptée | M10 |
| [ADR-0063 à 0082](../../../adr/) | ADR M11–M18 (surfaces, sync incrémental, tests CLI MCP, sécurité locale, composition, portfolio) | Acceptées | M11–M18 |

*Note : les ADR-0063 à ADR-0082 couvrent les milestones M11 à M18. Leurs titres
exacts sont disponibles dans `docs/adr/README.md`. Ils ne sont pas répertoriés
individuellement ici pour des raisons de concision ; la convention de liens
vers `../../adr/` s'applique.*

---

## 9.6 Index des ADR — Plateformes fonctionnelles (ADR-0083 à ADR-0096)

| ADR | Titre | Statut | Milestone |
|-----|-------|--------|-----------|
| [ADR-0083](../../../adr/0083-controlled-lifecycle-write-operations.md) | Lifecycle write controlé | Acceptée | M20 |
| [ADR-0084](../../../adr/0084-provider-neutral-multi-provider-composition.md) | Composition multi-provider neutre | Acceptée | M21 |
| [ADR-0085 à 0089](../../../adr/) | ADR M22 (composition, operability, maintenance SQLite) | Acceptées | M22 |
| [ADR-0090](../../../adr/0090-provider-sdk-plugin-discovery-platform.md) | Provider SDK + plugin discovery platform | Acceptée | M23 |
| [ADR-0091](../../../adr/0091-multi-project-portfolio-intelligence.md) | Multi-project portfolio intelligence | Acceptée | M23 |
| [ADR-0092](../../../adr/0092-query-dsl-saved-views-reporting.md) | Query DSL + saved views + reporting | Acceptée | M24 |
| [ADR-0093](../../../adr/0093-policy-packs-governance-automation.md) | Policy packs + gouvernance automatisée | Acceptée | M25 |
| [ADR-0094](../../../adr/0094-optional-team-remote-server-mode.md) | Mode serveur distant optionnel (remote) | Acceptée | M26 |
| [ADR-0095](../../../adr/0095-evidence-backed-assisted-reasoning.md) | Raisonnement assisté basé sur preuves | Acceptée | M27 |
| [ADR-0096](../../../adr/0096-conservative-native-mcp-client-integration.md) | Intégration MCP client native conservative | Acceptée | M28 |

---

## 9.7 Décisions à anticiper (post-M28)

Ces sujets n'ont pas encore de décision formalisée et méritent un ADR :

| Sujet | Raison |
|-------|--------|
| ADR-0097 : Backend de stockage alternatif (PostgreSQL/DuckDB) | ADR-0018 « avec contraintes » — la substitution est architecturalement prévue mais non implémentée |
| ADR-0098 : Authentification SSO/LDAP en mode remote | Mode remote actuel limité à Bearer tokens ; hypothèse à valider si déploiement entreprise |
| ADR-0099 : Migration vers Gradle | Mentionné comme évolution post-M28 dans la roadmap |
| ADR-0100 : API v2 | Si des breaking changes s'accumulent post-M28 |
