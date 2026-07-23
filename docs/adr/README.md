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

---

# Preuves M2

| Slice | ADR | Preuve |
|---|---|---|
| M2-S1 domaine courant | ADR-0022 | `48/48 PASS` |
| M2-S2 identité persistante | ADR-0023 | `58/58 PASS` |
| M2-S3 changement normalisé | ADR-0024 | `64/64 PASS` |
| M2-S4 requirement deltas | ADR-0025 | `70/70 PASS` |
| M2-S5 ExternalReference | ADR-0026 | `76/76 PASS` |
| M2-S6 lecture unifiée / partiel / diagnostics | ADR-0028 | `84/84 PASS` |
| M2-S7 second provider anti-lock-in | ADR-0029 | `94/94 PASS` |
| M2-S8 validation finale / persistance | ADR-0030 | `94/94 PASS` |

Validation : [`../VALIDATION_M2.md`](../VALIDATION_M2.md).

---

# Preuves M3

| Slice | ADR | Preuve |
|---|---|---|
| M3-S1 temporalité + versions | ADR-0031 | `103/103 PASS` |
| M3-S2 lifecycle des changements | ADR-0032 | `119/119 PASS` |
| M3-S3 KnowledgeSnapshot / activation atomique | ADR-0033 | `127/127 PASS` |
| M3-S4 persistance métier versionnée | ADR-0034 | `134/134 PASS` |
| M3-S5 application / promotion des deltas | ADR-0035 | `142/142 PASS` |
| M3-S6 historique / comparaison / rollback / rétention | ADR-0036 | `147/147 PASS` |

Validation : [`../VALIDATION_M3.md`](../VALIDATION_M3.md).

---

# Preuves M4

| Slice | ADR | Preuve |
|---|---|---|
| M4-S1 domaine `TraceabilityLink` + taxonomie | ADR-0037 | `155/155 PASS` |
| M4-S2 persistance snapshot-scoped | ADR-0038 | `160/160 PASS` |
| M4-S3 dérivation déterministe | ADR-0039 | `167/167 PASS` |
| M4-S4 traversal / path | ADR-0040 | `174/174 PASS` |
| M4-S5 références externes / unresolved / broken | ADR-0041 | `184/184 PASS` |
| M4-S6 `trace(requirement)` | ADR-0042 | `189/189 PASS` |

Validation : [`../VALIDATION_M4.md`](../VALIDATION_M4.md).

---

# Preuves M5

| Slice | ADR | Preuve |
|---|---|---|
| M5-S1 recherche lexicale + pagination | ADR-0043 | `196/196 PASS` |
| M5-S2 projection métier snapshot-scoped | ADR-0044 | `202/202 PASS` |
| M5-S3 getters/listes déterministes | ADR-0045 | `210/210 PASS` |
| M5-S4 trace query view + change context | ADR-0046 | `217/217 PASS` |
| M5-S5 vues compactes + warnings + JSON canonique | ADR-0047 | `227/227 PASS` |
| M5-S6 validation finale | — | `227/227 PASS` |

Validation : [`../VALIDATION_M5.md`](../VALIDATION_M5.md).

---

# Preuves M6

| Slice | ADR | Preuve |
|---|---|---|
| M6-S1 couverture requirements + orphelins | ADR-0048 | `234/234 PASS` |
| M6-S2 couverture tasks + acceptance gap | ADR-0049 | `241/241 PASS` |
| M6-S3 change completeness + lifecycle blockers | ADR-0050 | `248/248 PASS` |
| M6-S4 decisions + références externes | ADR-0051 | `254/254 PASS` |
| M6-S5 rapport agrégé + vue compacte | ADR-0052 | `261/261 PASS` |
| M6-S6 validation finale | — | `261/261 PASS` |

Validation : [`../VALIDATION_M6.md`](../VALIDATION_M6.md).  
Vue d'exécution : [`../roadmap/M6_EXECUTION.md`](../roadmap/M6_EXECUTION.md).

---

# Preuves M7

| Incrément | ADR | Preuve |
|---|---|---|
| Inventaire SHA-256 + diff conservateur | ADR-0053 | `282/282 PASS` |
| Persistance sync + archives + fraîcheur + V008 | ADR-0054 | `282/282 PASS` |
| Watcher local + fallback full rebuild | ADR-0055 | `282/282 PASS` |
| Validation finale M7 | — | `282/282 PASS`, architecture `139/139` |

Validation : [`../VALIDATION_M7.md`](../VALIDATION_M7.md).  
Vue d'exécution : [`../roadmap/M7_EXECUTION.md`](../roadmap/M7_EXECUTION.md).  
Reçu d'intégration : [`../roadmap/M7_INTEGRATION.md`](../roadmap/M7_INTEGRATION.md).

---

# Contraintes actives principales

## Architecture

```text
com.morpheus.domain      -X-> com.morpheus.provider..
com.morpheus.application -X-> com.morpheus.provider..
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> SQLite
com.morpheus.domain      -X-> CLI/MCP/API adapters
```

## Identité / temporalité

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
DomainIdentity != SourceLocator != ExternalReference
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
```

## Qualité M6

```text
QualityFinding != diagnostic d'ingestion
finding = dérivé, non persisté
DETERMINISTIC != HEURISTIC
DETERMINISTIC => confidence interdite
HEURISTIC => confidence obligatoire [0,1]
Scenario != AcceptanceCriterion
DesignDecision.decision != justification
lifecycle non inféré depuis snapshot
absence de lien != lien inventé
Memory == SQLite
SQLite reopen
compact quality JSON déterministe
```

## Synchronisation M7

```text
reliability > incremental speed
fingerprint = SHA-256(content)
sourceRevision opaque
move ambigu => FULL_REBUILD
watcher != source of truth
OVERFLOW => FULL_REBUILD
baseline persisted only after success
archive != suppression historique publié
invalidation != suppression snapshot
freshness uses explicit now/maxAge
Memory == SQLite
SQLite reopen
```

## Build

Gate obligatoire :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Baseline : Java `release 21`.

Dernier gate M7 :

```text
MORPHEUS Application  82/82 PASS
Architecture Tests   139/139 PASS
TOTAL                282/282 PASS
Failures               0
Errors                 0
Skipped                0
BUILD SUCCESS
Total time           21.141 s
Finished 2026-07-24T00:22:11+02:00
```

GitHub Actions n'est pas la porte obligatoire. Les warnings JDK native-access Xerial SQLite et SLF4J NOP restent connus et non bloquants.

---

# Principe de validation

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter le Maven Wrapper
5. accepter l'ADR uniquement après preuve
6. merger sous autorisation explicite
7. mettre à jour roadmap + issue de milestone
```
