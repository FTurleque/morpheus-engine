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

M2 est validée. La preuve de sortie est [`../VALIDATION_M2.md`](../VALIDATION_M2.md).

---

# Preuves M3 en cours

| Slice | ADR | Preuve |
|---|---|---|
| M3-S1 temporalité + versions | ADR-0031 | `103/103 PASS` |
| M3-S2 lifecycle des changements | ADR-0032 | `119/119 PASS` |
| M3-S3 KnowledgeSnapshot / activation atomique | ADR-0033 | `127/127 PASS` |

La vue opérationnelle M3 est [`../roadmap/M3_EXECUTION.md`](../roadmap/M3_EXECUTION.md).
La trajectoire de packaging/déploiement est [`../roadmap/DEPLOYMENT.md`](../roadmap/DEPLOYMENT.md).

---

# Contraintes actives principales

## Frontière domaine et providers

```text
com.morpheus.domain      -X-> com.morpheus.provider..
com.morpheus.application -X-> com.morpheus.provider..
com.morpheus.domain      -X-> SQLite
com.morpheus.domain      -X-> CLI/MCP/API adapters
com.morpheus.domain      -X-> MINOS/GitHub/Jira clients
```

OpenSpec et Synthetic dépendent vers l'intérieur de `domain + application` ; jamais l'inverse.

## OpenSpec

```text
schema = spec-driven
unknown schema -> UNSUPPORTED_PROVIDER_SCHEMA
Scenario != AcceptanceCriterion
```

## Lecture provider

```text
SpecificationProvider.probe() != SpecificationContentReader.read()
empty collection != ambiguous success
```

Statuts :

```text
READ
ABSENT
UNSUPPORTED
FAILED
PARTIAL
```

## Anti-lock-in

```text
OpenSpec source  ─────┐
                      ├──> SpecificationContentReader -> ProviderReadResult
Synthetic JSON ───────┘
```

Garanties prouvées :

```text
même contrat applicatif
même domaine MORPHEUS
consumer sans branche provider-specific
même ReadCategory vocabulary
(providerId, entityType, externalId) namespace l'identité
aucun type provider dans domain/application
```

Le provider synthétique est `verification-only`.

## Build

Gate obligatoire :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Baseline :

```text
Maven 3.9.16
Java source/bytecode release 21
```

GitHub Actions n'est pas une porte obligatoire.

## SQLite et frontière M3

Persisté après M2 :

```text
schema_migrations
projects
knowledge_snapshots metadata
entity_identity_bindings
```

ADR-0030 fixe :

```text
M2 : identité + métadonnées persistantes
M3 : premières tables métier complètes avec TemporalState,
     SpecificationVersion et snapshot/version membership
```

M3-S3 complète le lifecycle technique de `knowledge_snapshots` sans migration SQL supplémentaire. Les tables métier versionnées restent M3-S4.

Le schéma JSON du spike E08 reste rejeté comme modèle de production.

Le warning JDK 24 `--enable-native-access=ALL-UNNAMED` reste non bloquant et devra être traité avant stabilisation runtime/CLI.

## Identité et temporalité

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
DomainIdentity != SourceLocator != ExternalReference
externalId != DomainIdentity
provider namespace fait partie de la résolution
continuité d'identité explicite uniquement
aucune fusion par titre/chemin/contenu
```

Projection M3 :

```text
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
une DomainIdentity -> au plus une occurrence CURRENT par projection
plusieurs PROPOSED concurrents restent permis
```

## Lifecycle des changements

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Le skip `SPECIFIED -> PLANNED` est conditionnel à `design_required=false` et à la présence d'un plan. Les retours arrière sont gouvernés par politique explicite. `ABANDONED` exige une raison structurée et peut revenir à `PROPOSED`; `ARCHIVED` n'est pas rouvert implicitement.

## Lifecycle KnowledgeSnapshot

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
```

Invariants :

```text
seul ACTIVE est observable comme snapshot courant
un projet possède au plus un ACTIVE
FAILED n'évince jamais l'ACTIVE existant
predecessor stale -> SnapshotConflictException
activation ACTIVE/RETIRED réservée à activateSnapshot
transitionSnapshotState = CAS explicite
```

Memory et SQLite respectent le même contrat. L'activation SQLite reste transactionnelle et l'état `ACTIVE/RETIRED` survit à fermeture/réouverture.

## ExternalReference

```text
ExternalReference peut exister sans resolver
NO_RESOLVER est explicite
resolver indisponible != panne MORPHEUS
cible supprimée -> STALE, référence conservée
historique de résolution conservé
```

## Distribution

```text
Native-first
Container-supported
```

Règles :

```text
CLI locale sans Docker obligatoire
runtime Java embarqué à prouver en M9
MCP stdio natif privilégié
Docker officiel pour headless/MCP réseau/API lorsque justifié
workspace montable read-only
données persistantes externalisées du conteneur
même core dans tous les modes
```

Les choix concrets `jlink/jpackage`, format installateur, image de base Docker, framework HTTP et transport MCP restent à prouver aux jalons M9/M10/M11.

---

# Documents de validation

- [`../VALIDATION_C0.md`](../VALIDATION_C0.md)
- [`../VALIDATION_M0.md`](../VALIDATION_M0.md)
- [`../VALIDATION_M1.md`](../VALIDATION_M1.md)
- [`../VALIDATION_M2.md`](../VALIDATION_M2.md) — **M2 validée, M3 autorisée**.

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
