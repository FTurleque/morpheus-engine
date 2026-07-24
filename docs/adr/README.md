# ADR — MORPHEUS

Ce répertoire contient les **Architecture Decision Records** de MORPHEUS.

Une ADR dépendante d'une hypothèse technique n'est acceptée qu'après preuve.

## Statuts

- **Proposée** : décision candidate ;
- **Acceptée** : décision validée ;
- **Acceptée avec contraintes** : décision validée sous conditions ;
- **Remplacée** : supersédée ;
- **Rejetée** : option étudiée puis non retenue.

---

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
| [ADR-0043](0043-deterministic-requirement-lexical-query.md) | Recherche lexicale déterministe et pagination des requirements | **Acceptée — M5** |
| [ADR-0044](0044-snapshot-business-content-projection.md) | Projection métier snapshot-scoped des familles hors `Requirement` | **Acceptée — M5** |
| [ADR-0045](0045-deterministic-business-content-queries.md) | Getters et listes métier déterministes sur snapshots publiés | **Acceptée — M5** |
| [ADR-0046](0046-change-context-query-aggregation.md) | Agrégation déterministe de `trace_requirement` et `get_change_context` | **Acceptée — M5** |
| [ADR-0047](0047-compact-query-views-and-canonical-json.md) | Vues compactes, warnings structurés, provenance/evidence et JSON canonique | **Acceptée — M5** |
| [ADR-0048](0048-quality-findings-and-requirement-coverage.md) | Findings de qualité explicables et couverture de traçabilité des requirements | **Acceptée — M6** |
| [ADR-0049](0049-task-requirement-coverage-and-acceptance-gap.md) | Couverture task → requirement et gap explicite d'acceptance coverage | **Acceptée — M6** |
| [ADR-0050](0050-change-completeness-and-lifecycle-quality.md) | Complétude des changements et qualité lifecycle sans faits inventés | **Acceptée — M6** |
| [ADR-0051](0051-decision-and-external-reference-quality.md) | Qualité des décisions et références externes sans justification inventée | **Acceptée — M6** |
| [ADR-0052](0052-aggregate-quality-report-and-compact-view.md) | Rapport qualité agrégé et vue compacte déterministe | **Acceptée — M6** |
| [ADR-0053](0053-deterministic-source-inventory-and-incremental-diff.md) | Inventaire de sources SHA-256 et diff incrémental conservateur | **Acceptée — M7** |
| [ADR-0054](0054-persisted-sync-state-archives-and-freshness.md) | État de synchronisation persisté, archives et fraîcheur | **Acceptée — M7** |
| [ADR-0055](0055-local-watcher-and-full-rebuild-fallback.md) | Watcher local conservateur et fallback full rebuild | **Acceptée — M7** |
| [ADR-0056](0056-deterministic-current-proposed-change-analysis.md) | Analyse déterministe CURRENT / proposé sans promotion | **Acceptée — M8** |
| [ADR-0057](0057-explicit-bounded-dependency-impact-paths.md) | Impacts de dépendance explicites par chemins bornés | **Acceptée — M8** |
| [ADR-0058](0058-compact-change-analysis-and-canonical-json.md) | Vue compacte d'analyse et JSON canonique | **Acceptée — M8** |
| [ADR-0059](0059-stable-local-cli-contract.md) | CLI locale stable, scriptable, stdout/stderr et exit codes | **Acceptée — M9** |
| [ADR-0060](0060-conservative-full-snapshot-cli-sync.md) | Sync CLI conservateur par full snapshot publié | **Acceptée — M9** |
| [ADR-0061](0061-self-contained-jpackage-portable-distribution.md) | Distribution portable autonome via jpackage/jlink | **Acceptée — M9** |
| [ADR-0062](0062-official-java-mcp-sdk-and-native-stdio.md) | Java MCP SDK officiel + transport STDIO natif | **Acceptée — M10** |
| [ADR-0063](0063-read-only-mcp-tool-contract.md) | Catalogue MCP read-only et sémantique explicite | **Acceptée — M10** |
| [ADR-0064](0064-native-launcher-mcp-routing.md) | Routage MCP dans le launcher natif et stdout protocol-clean | **Acceptée — M10** |

---

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
M10 307/307 PASS Windows | Architecture 149/149 | MCP STDIO + packaging PASS
```

## M10

| Incrément | ADR | Preuve |
|---|---|---|
| SDK officiel + STDIO | ADR-0062 | `MorpheusMcpServerContractTest 1/1`, intégration STDIO réelle |
| 14 tools read-only + sémantique explicite | ADR-0063 | `MorpheusMcpToolCatalogTest 3/3`, `MorpheusMcpToolServiceTest 1/1` |
| launcher + distribution MCP | ADR-0064 | `MorpheusMainTest 5/5`, `MorpheusMcpStdioIntegrationTest 1/1`, MCP packaging proof + ZIP |
| validation finale | — | `307/307 PASS`, architecture `149/149`, ZIP `77275075` octets |

Validation : [`../VALIDATION_M10.md`](../VALIDATION_M10.md).  
Vue d'exécution : [`../roadmap/M10_EXECUTION.md`](../roadmap/M10_EXECUTION.md).  
Documentation MCP : [`../MCP.md`](../MCP.md).

---

# Contraintes actives principales

## Architecture

```text
com.morpheus.domain      -X-> com.morpheus.provider..
com.morpheus.application -X-> com.morpheus.provider..
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> SQLite
com.morpheus.domain      -X-> CLI/MCP/API adapters
com.morpheus.application -X-> CLI/MCP/API adapters
CLI/MCP/API = adapters
logique métier essentielle = application/domain
```

## Identité / temporalité

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
DomainIdentity != SourceLocator != ExternalReference
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
```

## Qualité / analyse

```text
QualityFinding = dérivé, non persisté
DETERMINISTIC != HEURISTIC
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
absence de lien != lien inventé
lifecycle non inféré depuis snapshot
DEPENDS_ON persisted only
bounded shortest paths
code impact analysis = MINOS
```

## Synchronisation

```text
reliability > incremental speed
fingerprint = SHA-256(content)
move ambigu => FULL_REBUILD
watcher != source of truth
OVERFLOW => FULL_REBUILD
baseline persisted only after success
```

## CLI / MCP / distribution

```text
stdout=result pour CLI
stdout=MCP JSON-RPC uniquement en mode MCP
stable CLI exit codes
option > MORPHEUS_* > OS default
single SQLite path per invocation
CLI sync = conservative FULL_REBUILD
MCP tools = read-only
no MCP write/promote/activate
portable app-image contains Java runtime
MCP SDK embedded in portable artifact
installation != user data/config
mvnw/*.sh = LF ; Windows scripts = CRLF
```

## Build

Gate obligatoire :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Baseline : Java `release 21`.

Dernier gate **validé** : M10.

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
MORPHEUS MCP              5/5 PASS
MORPHEUS CLI             10/10 PASS
Architecture Tests      149/149 PASS
TOTAL                   307/307 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
```

Warnings connus non bloquants : Xerial SQLite native-access, SLF4J NOP et warnings Maven Shade sur ressources/module-info du SDK MCP.

---

# Principe de validation

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. prouver le comportement par tests reproductibles
4. accepter l'ADR seulement après la preuve
5. exécuter le gate Maven complet
6. mettre à jour roadmap + issue après validation
7. fusionner uniquement après autorisation explicite
```
