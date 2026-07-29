# ADR — MORPHEUS

Ce répertoire contient les **Architecture Decision Records** de MORPHEUS.

Une ADR dépendante d'une hypothèse technique n'est acceptée qu'après preuve.

## Statuts

- **Proposée** : décision candidate ;
- **Acceptée** : décision validée ;
- **Acceptée avec contraintes** : décision validée sous conditions ;
- **Remplacée** : supersédée ;
- **Rejetée** : option étudiée puis non retenue.

# Index

| ADR | Décision | Statut |
|---|---|---|
| [ADR-0001](0001-morpheus-owned-domain.md) | Domaine MORPHEUS indépendant des formats/providers | **Acceptée avec contraintes — M0** |
| [ADR-0002](0002-openspec-reference-provider.md) | OpenSpec premier provider, sans verrouillage | **Acceptée avec contraintes — M0** |
| [ADR-0003](0003-specification-knowledge-store.md) | Persistance derrière `SpecificationKnowledgeStore` | **Acceptée — M0** |
| [ADR-0004](0004-local-first-no-llm-core.md) | Cœur local-first sans LLM obligatoire | **Acceptée — M0** |
| [ADR-0005](0005-traceability-first-class.md) | Traçabilité first-class | **Acceptée — M0** |
| [ADR-0006](0006-current-vs-proposed-state.md) | Current / proposed / historical séparés | **Acceptée — M0** |
| [ADR-0007](0007-cross-engine-integration.md) | Intégrations cross-engine découplées | **Acceptée — M0** |
| [ADR-0008](0008-read-first-write-capability.md) | Providers read-first ; écriture optionnelle | **Acceptée — M0** |
| [ADR-0009](0009-stable-domain-identity.md) | Identity != version != locator != external ref | **Acceptée — M0** |
| [ADR-0010](0010-traceability-relation-taxonomy.md) | Taxonomie contrôlée de traçabilité | **Acceptée avec contraintes — M0** |
| [ADR-0011](0011-provider-capability-negotiation.md) | Sélection par capacités effectives | **Acceptée — M0** |
| [ADR-0012](0012-snapshot-versioning-strategy.md) | Snapshots versionnés et activation atomique | **Acceptée — M0** |
| [ADR-0013](0013-change-lifecycle-state-machine.md) | Machine d'état des changements | **Acceptée avec contraintes — M0** |
| [ADR-0014](0014-defer-production-technology-stack.md) | Stack différée jusqu'aux preuves | **Acceptée — C0** |
| [ADR-0015](0015-domain-identity-uuidv7.md) | UUIDv7 canonique opaque | **Acceptée — M0** |
| [ADR-0016](0016-java-21-production-baseline.md) | Java baseline 21 | **Acceptée — M0** |
| [ADR-0017](0017-maven-build-foundation.md) | Maven + Wrapper, `release=21` | **Acceptée avec contraintes — M0/M1** |
| [ADR-0018](0018-sqlite-initial-persistent-store.md) | SQLite backend initial derrière port | **Acceptée avec contraintes — M0** |
| [ADR-0019](0019-maven-coordinates-java-namespace.md) | Maven coordinates + namespace Java | **Acceptée — M1** |
| [ADR-0020](0020-workspace-root-resolution.md) | Workspace discovery explicit-first | **Acceptée — M1** |
| [ADR-0021](0021-sqlite-schema-migrations-foundation.md) | Migrations SQLite explicites/versionnées | **Acceptée — M1** |
| [ADR-0022](0022-m2-normalized-content-before-temporal-projection.md) | Normaliser avant projection temporelle | **Acceptée — M2** |
| [ADR-0023](0023-persistent-provider-scoped-entity-identity.md) | Identités provider-scoped persistantes | **Acceptée — M2** |
| [ADR-0024](0024-m2-change-metadata-normalization.md) | Métadonnées de changement normalisées | **Acceptée — M2** |
| [ADR-0025](0025-m2-requirement-delta-normalization.md) | Requirement deltas sans application temporelle | **Acceptée — M2** |
| [ADR-0026](0026-optional-external-reference-resolution.md) | Références externes via resolvers optionnels | **Acceptée — M2** |
| [ADR-0027](0027-native-first-container-supported-distribution.md) | Distribution native-first et container-supported | **Acceptée avec contraintes — distribution** |
| [ADR-0028](0028-unified-provider-read-contract.md) | Contrat de lecture unifié et résultats partiels explicites | **Acceptée — M2** |
| [ADR-0029](0029-second-provider-anti-lockin-proof.md) | Second provider synthétique pour preuve anti-lock-in | **Acceptée — M2** |
| [ADR-0030](0030-defer-normalized-business-persistence-to-m3.md) | Persistance métier complète introduite avec versions/snapshots M3 | **Acceptée — M2** |
| [ADR-0031](0031-explicit-temporal-projection-and-entity-version.md) | Projection temporelle explicite sur occurrences versionnées | **Acceptée — M3** |
| [ADR-0032](0032-explicit-change-lifecycle-state-machine.md) | Machine d'état explicite du lifecycle des changements | **Acceptée — M3** |
| [ADR-0033](0033-knowledge-snapshot-lifecycle-and-atomic-activation.md) | Lifecycle complet des KnowledgeSnapshot et activation atomique | **Acceptée — M3** |
| [ADR-0034](0034-versioned-requirement-persistence.md) | Première persistance métier versionnée sur `Requirement` | **Acceptée — M3** |
| [ADR-0035](0035-explicit-requirement-delta-application-and-promotion.md) | Application, promotion et activation explicites des `RequirementDelta` | **Acceptée — M3** |
| [ADR-0036](0036-published-history-comparison-and-logical-rollback.md) | Historique publié, comparaison et rollback logique | **Acceptée — M3** |
| [ADR-0037](0037-traceability-domain-and-controlled-taxonomy.md) | Domaine `TraceabilityLink` et taxonomie contrôlée | **Acceptée — M4** |
| [ADR-0038](0038-snapshot-scoped-traceability-persistence.md) | Persistance de traçabilité snapshot-scoped Memory + SQLite | **Acceptée — M4** |
| [ADR-0039](0039-deterministic-traceability-derivation.md) | Dérivation déterministe depuis le modèle normalisé | **Acceptée — M4** |
| [ADR-0040](0040-bounded-deterministic-traceability-traversal.md) | Traversée bornée, déterministe et chemins explicables | **Acceptée — M4** |
| [ADR-0041](0041-snapshot-external-reference-traceability.md) | Références externes snapshot-scoped et liens unresolved/broken explicables | **Acceptée — M4** |
| [ADR-0042](0042-final-trace-requirement-query.md) | Porte finale `trace(requirement)` sur snapshots publiés | **Acceptée — M4** |
| [ADR-0043](0043-deterministic-requirement-lexical-query.md) | Recherche lexicale déterministe et pagination | **Acceptée — M5** |
| [ADR-0044](0044-snapshot-business-content-projection.md) | Projection métier snapshot-scoped | **Acceptée — M5** |
| [ADR-0045](0045-deterministic-business-content-queries.md) | Getters/listes métier déterministes | **Acceptée — M5** |
| [ADR-0046](0046-change-context-query-aggregation.md) | Agrégation trace/context | **Acceptée — M5** |
| [ADR-0047](0047-compact-query-views-and-canonical-json.md) | Vues compactes et JSON canonique | **Acceptée — M5** |
| [ADR-0048](0048-quality-findings-and-requirement-coverage.md) | Findings qualité et couverture requirements | **Acceptée — M6** |
| [ADR-0049](0049-task-requirement-coverage-and-acceptance-gap.md) | Couverture task→requirement et acceptance gap | **Acceptée — M6** |
| [ADR-0050](0050-change-completeness-and-lifecycle-quality.md) | Complétude changement/lifecycle sans faits inventés | **Acceptée — M6** |
| [ADR-0051](0051-decision-and-external-reference-quality.md) | Qualité décisions/références externes | **Acceptée — M6** |
| [ADR-0052](0052-aggregate-quality-report-and-compact-view.md) | Rapport qualité agrégé | **Acceptée — M6** |
| [ADR-0053](0053-deterministic-source-inventory-and-incremental-diff.md) | Inventaire SHA-256 et diff incrémental | **Acceptée — M7** |
| [ADR-0054](0054-persisted-sync-state-archives-and-freshness.md) | Sync state persisté et fraîcheur | **Acceptée — M7** |
| [ADR-0055](0055-local-watcher-and-full-rebuild-fallback.md) | Watcher local + fallback rebuild | **Acceptée — M7** |
| [ADR-0056](0056-deterministic-current-proposed-change-analysis.md) | Analyse CURRENT/proposé | **Acceptée — M8** |
| [ADR-0057](0057-explicit-bounded-dependency-impact-paths.md) | Chemins d'impact bornés | **Acceptée — M8** |
| [ADR-0058](0058-compact-change-analysis-and-canonical-json.md) | Vue compacte d'analyse | **Acceptée — M8** |
| [ADR-0059](0059-stable-local-cli-contract.md) | CLI stable | **Acceptée — M9** |
| [ADR-0060](0060-conservative-full-snapshot-cli-sync.md) | Sync CLI full snapshot conservateur | **Acceptée — M9** |
| [ADR-0061](0061-self-contained-jpackage-portable-distribution.md) | Distribution portable autonome | **Acceptée — M9** |
| [ADR-0062](0062-official-java-mcp-sdk-and-native-stdio.md) | Java MCP SDK + STDIO | **Acceptée — M10** |
| [ADR-0063](0063-read-only-mcp-tool-contract.md) | Catalogue MCP read-only | **Acceptée — M10** |
| [ADR-0064](0064-native-launcher-mcp-routing.md) | Routage MCP natif | **Acceptée — M10** |
| [ADR-0065](0065-jdk-httpserver-local-api.md) | JDK HttpServer pour API locale | **Acceptée — M11** |
| [ADR-0066](0066-versioned-http-api-contract.md) | Contrat `/api/v1` | **Acceptée — M11** |
| [ADR-0067](0067-explicit-conservative-http-sync.md) | Sync HTTP full snapshot conservateur | **Acceptée — M11** |
| [ADR-0068](0068-native-launcher-headless-api.md) | Launcher/distribution API headless | **Acceptée — M11** |
| [ADR-0069](0069-minos-mcp-stdio-integration.md) | MINOS via MCP STDIO inter-processus | **Acceptée — M12** |
| [ADR-0070](0070-exact-minos-symbol-reference-and-revision.md) | `symbolKey` exact + révision MINOS | **Acceptée — M12** |
| [ADR-0071](0071-live-external-resolution-without-snapshot-mutation.md) | Résolution live sans mutation de snapshot | **Acceptée — M12** |
| [ADR-0072](0072-optional-minos-runtime-configuration-and-surfaces.md) | Runtime/surfaces MINOS optionnels | **Acceptée — M12** |
| [ADR-0073](0073-nexus-mcp-stdio-integration.md) | NEXUS via MCP STDIO inter-processus | **Acceptée — M13** |
| [ADR-0074](0074-explicit-nexus-project-mapping.md) | Mapping projet NEXUS explicite | **Acceptée — M13** |
| [ADR-0075](0075-morpheus-intent-vs-nexus-technical-context.md) | Intention MORPHEUS distincte du contexte technique NEXUS | **Acceptée — M13** |
| [ADR-0076](0076-optional-nexus-runtime-and-surfaces.md) | Runtime/surfaces NEXUS optionnels | **Acceptée — M13** |
| [ADR-0077](0077-morpheus-jarvis-read-only-orchestration-boundary.md) | Frontière read-only MORPHEUS / JARVIS | **Acceptée — M14** |
| [ADR-0078](0078-explicit-lifecycle-and-tristate-transition-evaluation.md) | Lifecycle explicite et transition tri-state | **Acceptée — M14** |
| [ADR-0079](0079-nondestructive-change-orchestration-state.md) | État d'orchestration agrégé non destructif | **Acceptée — M14** |
| [ADR-0080](0080-jarvis-orchestration-surfaces-and-optional-client.md) | Surfaces M14 et client JARVIS optionnel | **Acceptée — M14** |
| [ADR-0081](0081-first-class-acceptance-verification-evidence.md) | `AcceptanceCriterion`, `VerificationStatus` et preuves de vérification first-class | **Acceptée — M15** |
| [ADR-0082](0082-explicit-constraint-semantics-and-blocking-policy.md) | Sémantique explicite des contraintes et politique de blocage | **Acceptée — M16** |
| [ADR-0083](0083-controlled-lifecycle-write-operations.md) | Mutations lifecycle contrôlées, CAS, idempotency et audit | **Acceptée — M17** |
| [ADR-0084](0084-provider-neutral-multi-provider-composition.md) | Composition multi-provider provider-neutral, déterministe et explicable | **Acceptée — M18** |
| [ADR-0085](0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) | Budgets pré-déclarés et fixtures larges déterministes | **Acceptée — M19** |
| [ADR-0086](0086-failure-atomic-snapshot-recovery-and-bounded-sqlite-concurrency.md) | Recovery failure-atomic et concurrence SQLite bornée | **Acceptée — M19** |
| [ADR-0087](0087-local-first-operability-and-secure-diagnostic-defaults.md) | Opérabilité local-first et diagnostics sûrs par défaut | **Acceptée — M19** |
| [ADR-0088](0088-product-release-installation-and-persistent-data-separation.md) | Release produit, installation et séparation programme/données persistantes | **Acceptée — M20** |
| [ADR-0089](0089-production-integrity-surface-convergence.md) | Intégrité de production, qualité/supply-chain et convergence des surfaces publiques | **Acceptée — M21** |
| [ADR-0090](0090-provider-sdk-plugin-discovery-platform.md) | Provider SDK v1, discovery sans exécution, activation isolée et lecture normalisée | **Acceptée — M22** |
| [ADR-0091](0091-multi-project-portfolio-intelligence.md) | Portfolio multi-projets provider-neutral, références explicables et traversal bornée | **Acceptée — M23** |
| [ADR-0092](0092-provider-neutral-query-dsl-saved-views-reporting.md) | Query DSL provider-neutral, saved views versionnées et reporting déterministe | **Acceptée — M24** |
| [ADR-0093](0093-provider-neutral-policy-packs-governance-automation.md) | Policy packs provider-neutral, versions immuables, overrides explicables et dry-run read-only | **Acceptée — M25** |

# Preuves par jalon

```text
M2   94/94 PASS
M3  147/147 PASS
M4  189/189 PASS
M5  227/227 PASS
M6  261/261 PASS
M7  282/282 PASS | Architecture 139/139
M8  289/289 PASS | Architecture 146/146
M9  298/298 PASS Windows + Linux | Architecture 149/149
M10 307/307 PASS Windows | Architecture 149/149
M11 314/314 PASS Windows | Architecture 150/150
M12 331/331 PASS Windows | Architecture 153/153
M13 346/346 PASS Windows | Architecture 154/154
M14 357/357 PASS Windows | Architecture 160/160 | JARVIS 536 tests
M15 371/371 PASS Windows | Architecture 157/157
M16 393/393 PASS Windows | Architecture 161/161
M17 410/410 PASS Windows | Architecture 167/167
M18 418/418 PASS Windows | Architecture 170/170
M19 449/449 PASS Windows + Linux | Architecture 178/178
M20 454/454 PASS Windows + Linux | Architecture 182/182
M21 473 PASS Windows + Linux | Architecture 187 | JaCoCo floors PASS | Portable PASS
M22 494 PASS Windows + Linux | Architecture 190 | SDK API 1 | External provider PASS | Portable PASS
M23 507 PASS Windows + Linux | Architecture 195 | Portfolio convergence PASS | Portable PASS
M24 543 PASS Windows + Linux | Architecture 221 | Query/view/export convergence PASS | Portable PASS
M25 565 PASS Windows + Linux | Architecture 231 | Policy governance convergence PASS | Portable PASS
```

## Baseline M25 qualifiée

ADR-0093 ajoute des policy packs provider-neutral, des versions immuables, des activations et overrides explicites avec CAS, un dry-run read-only, un audit append-only et une convergence CLI/MCP/HTTP.

```text
Code exact qualifié   a392604fc9e8d00f4021351ab5ba53f8488ab920
Version               1.0.0
Windows               PASS
Linux WSL             PASS
Tests                 565 PASS
Architecture          231 PASS
Policy packs          PASS
Versioning / CAS      PASS
Overrides             PASS
Dry-run               PASS
SQLite V015           PASS
CLI/MCP/HTTP          convergence PASS
Coverage              PASS Windows + Linux
SBOM/provenance       PASS Windows + Linux
Portable              PASS Windows + Linux
Executable delta      NONE Windows + Linux
```

Validation : [`../validation/VALIDATION_M25.md`](../validation/VALIDATION_M25.md).  
Plan : [`../roadmap/M25_EXECUTION.md`](../roadmap/M25_EXECUTION.md).  
Roadmap active 1.x : [`../roadmap/POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

# Contraintes actives principales

```text
domain/application -X-> provider/store/cli/mcp/api/integration
API -X-> CLI/MCP/integration
MINOS integration -X-> com.minos.*
NEXUS integration -X-> com.nexus.*
MORPHEUS -X-> com.jarvis.*
provider-specific types -X-> domain/application contracts
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
classloader isolation != security sandbox
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
UNKNOWN != BLOCKED
precedence != provenance erasure
conflict != silent last-write-wins
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
surface parity != same transport shape
update discovery != automatic update
checksum != signature
```

# Build

Gate développeur :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Baseline : Java `release 21`.

Dernier gate techniquement qualifié : **M25**.

# Principe de validation

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. prouver le comportement par tests reproductibles
4. accepter l'ADR seulement après la preuve
5. exécuter le gate complet
6. mettre à jour roadmap + issue après validation
7. fusionner uniquement après respect des gates
8. réconcilier roadmaps/index après merge
```