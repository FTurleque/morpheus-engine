# ADR — MORPHEUS

Ce répertoire contient les **Architecture Decision Records** de MORPHEUS.

Une ADR n'est acceptée qu'après preuve lorsqu'elle dépend d'une hypothèse technique.

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

---

# État final des preuves M2

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

M2 est validée. Preuve : [`../VALIDATION_M2.md`](../VALIDATION_M2.md).

---

# État final des preuves M3

| Slice | ADR | Preuve |
|---|---|---|
| M3-S1 temporalité + versions | ADR-0031 | `103/103 PASS` |
| M3-S2 lifecycle des changements | ADR-0032 | `119/119 PASS` |
| M3-S3 KnowledgeSnapshot / activation atomique | ADR-0033 | `127/127 PASS` |
| M3-S4 persistance métier versionnée | ADR-0034 | `134/134 PASS` |
| M3-S5 application / promotion des deltas | ADR-0035 | `142/142 PASS` |
| M3-S6 historique / comparaison / rollback / rétention | ADR-0036 | `147/147 PASS` |

M3 est validée et intégrée. Preuve : [`../VALIDATION_M3.md`](../VALIDATION_M3.md).

---

# Preuves M4 en cours

| Slice | ADR | Preuve |
|---|---|---|
| M4-S1 domaine `TraceabilityLink` + taxonomie contrôlée | ADR-0037 | `155/155 PASS` |
| M4-S2 persistance snapshot-scoped Memory + SQLite | ADR-0038 | `160/160 PASS` |
| M4-S3 dérivation déterministe depuis modèle normalisé | ADR-0039 | `167/167 PASS` |
| M4-S4 traversal / path snapshot-scoped | ADR-0040 | `174/174 PASS` |

La vue opérationnelle M4 est [`../roadmap/M4_EXECUTION.md`](../roadmap/M4_EXECUTION.md).
La trajectoire de packaging/déploiement est [`../roadmap/DEPLOYMENT.md`](../roadmap/DEPLOYMENT.md).

---

# Contraintes actives principales

## Frontière domaine / providers / adapters

```text
com.morpheus.domain      -X-> com.morpheus.provider..
com.morpheus.application -X-> com.morpheus.provider..
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> SQLite
com.morpheus.domain      -X-> CLI/MCP/API adapters
```

OpenSpec et Synthetic dépendent vers l'intérieur de `domain + application` ; jamais l'inverse.

## Identité et temporalité

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
DomainIdentity != SourceLocator != ExternalReference
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
plusieurs PROPOSED concurrents restent permis
```

## Lifecycle métier

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

## KnowledgeSnapshot

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
```

Seul `ACTIVE` est observable comme snapshot courant. L'activation est atomique et un predecessor stale est rejeté.

## Persistance métier versionnée

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

```text
SpecificationVersion 1 <--- N KnowledgeSnapshot
snapshot/version ownership explicite
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents autorisés
vue courante = ACTIVE snapshot + CURRENT
reopen SQLite conserve CURRENT / PROPOSED séparés
aucune payload JSON générique
```

## Application / promotion des RequirementDelta

```text
normalized delta != applied delta
APPLY != PROMOTE
PROMOTE != ACTIVATE
COMPLETED != PROMOTE
COMPLETED != ACTIVATE
CURRENT inchangé avant activation
```

## Historique / comparaison / rollback

```text
published history = RETIRED* -> ACTIVE
historical query = snapshot explicite ACTIVE/RETIRED + CURRENT
comparison = ADDED / MODIFIED / REMOVED / UNCHANGED
logical rollback != reactivate RETIRED
retention = KEEP_ALL_PUBLISHED
```

## Traçabilité M4-S1

```text
TraceabilityLinkId explicite
TraceabilityLinkId != hash(source,type,target)
endpoint = EntityKind + DomainIdentity
relation type fermé
origin / resolution / confidence orthogonaux au type
evidence obligatoire
inverse view != persisted duplicate link
```

## Persistance de traçabilité M4-S2

```text
TraceabilityLink definition != KnowledgeSnapshot membership
snapshot membership obligatoire
same TraceabilityLinkId + different definition = collision
snapshot A links != snapshot B links
Memory == SQLite contract
V005 = traceability_links + traceability_link_evidence + snapshot_traceability_links
outgoing/incoming snapshot-scoped et déterministes
aucun JSON générique
SQLite reopen conserve liens/evidence/memberships
```

## Dérivation de traçabilité M4-S3

```text
faits structurels uniquement
TraceabilityDerivationKey typée
TraceabilityLinkIdentityResolver explicite
aucun TraceabilityLinkId.generate() caché
aucun hash d'arête comme identité
origin = DERIVED
resolution = RESOLVED
confidence = empty
evidence du fait source conservée
ordre déterministe
aucun fuzzy matching
aucun Task -> Requirement implicite
```

## Traversée de traçabilité M4-S4

```text
OUTGOING / INCOMING / BIDIRECTIONAL explicites
maxDepth > 0
snapshot-scoped
BFS borné et cycle-safe
ordre déterministe
relation filters explicites
inverse query != persisted inverse edge
shortest path déterministe
path = persisted link + sens de parcours
traversal != transitivity
A -> B -> C != arête synthétique A -> C
Memory == SQLite observable semantics
aucune migration SQLite S4
```

## Build

Gate obligatoire :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Baseline : Maven 3.9.16, Java source/bytecode `release 21`.

Dernier gate M4-S4 :

```text
TraceabilityTraversalContractTest 7/7 PASS
174/174 PASS
Failures 0
Errors   0
Skipped  0
BUILD SUCCESS
```

GitHub Actions n'est pas une porte obligatoire.

Les warnings JDK native-access Xerial SQLite et SLF4J NOP ArchUnit restent connus et non bloquants.

## Distribution

```text
Native-first
Container-supported
```

CLI locale sans Docker obligatoire ; runtime Java embarqué à prouver en M9 ; Docker officiel pour les modes headless/MCP réseau/API lorsque justifié.

---

# Documents de validation

- [`../VALIDATION_C0.md`](../VALIDATION_C0.md)
- [`../VALIDATION_M0.md`](../VALIDATION_M0.md)
- [`../VALIDATION_M1.md`](../VALIDATION_M1.md)
- [`../VALIDATION_M2.md`](../VALIDATION_M2.md) — **M2 validée**.
- [`../VALIDATION_M3.md`](../VALIDATION_M3.md) — **M3 validée et intégrée**.

---

# Principe de validation

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter le Maven Wrapper
5. accepter l'ADR uniquement après preuve
6. merger seulement après signal explicite
7. mettre à jour roadmap + issue de milestone
```
