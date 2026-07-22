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
| [ADR-0030](0030-defer-normalized-business-persistence-to-m3.md) | Persistance métier complète introduite avec versions/snapshots M3 | **Proposée — validation M2-S8 requise** |

---

# État des preuves M2

| Slice | ADR | Preuve |
|---|---|---|
| M2-S1 domaine courant | ADR-0022 | `48/48 PASS` |
| M2-S2 identité persistante | ADR-0023 | `58/58 PASS` |
| M2-S3 changement normalisé | ADR-0024 | `64/64 PASS` |
| M2-S4 requirement deltas | ADR-0025 | `70/70 PASS` |
| M2-S5 ExternalReference | ADR-0026 | `76/76 PASS` |
| M2-S6 lecture unifiée / partiel / diagnostics | ADR-0028 | `84/84 PASS` |
| M2-S7 second provider anti-lock-in | ADR-0029 | `94/94 PASS` |
| M2-S8 validation finale / persistance | ADR-0030 | **gate final 94 attendu** |

La vue opérationnelle détaillée est : [`../roadmap/M2_EXECUTION.md`](../roadmap/M2_EXECUTION.md).

La trajectoire de packaging/déploiement est : [`../roadmap/DEPLOYMENT.md`](../roadmap/DEPLOYMENT.md).

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

`AcceptanceCriterion` n'est jamais dérivé automatiquement d'un `Scenario`.

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

## SQLite

SQLite reste derrière les ports applicatifs.

Persisté aujourd'hui :

```text
schema_migrations
projects
knowledge_snapshots metadata
entity_identity_bindings
```

Validé :

```text
checksum immutability
atomic snapshot metadata activation
persistence across reopen
```

Le schéma JSON du spike E08 reste rejeté comme modèle de production.

### Frontière candidate ADR-0030

```text
M2 : identité + métadonnées persistantes
M3 : premières tables métier complètes avec version/snapshot membership
```

La raison est que le schéma métier doit être conçu avec `TemporalState`, `SpecificationVersion` et `KnowledgeSnapshot`, plutôt que créé une première fois sans temporalité puis remodelé immédiatement.

Le warning JDK 24 `--enable-native-access=ALL-UNNAMED` reste non bloquant et devra être traité avant stabilisation runtime/CLI.

## Identité

```text
DomainIdentity != EntityVersion != SourceLocator != ExternalReference
externalId != DomainIdentity
provider namespace fait partie de la résolution
continuité d'identité explicite uniquement
aucune fusion par titre/chemin/contenu
```

## M2 / M3

```text
content normalization != temporal projection
RequirementDeltaKind != TemporalState
normalized delta != applied delta
change structure != ChangeLifecycleState
```

`CURRENT / PROPOSED / HISTORICAL`, versions complètes, promotion des deltas, lifecycle complet et persistance métier versionnée restent M3.

## ExternalReference

```text
ExternalReference peut exister sans resolver
NO_RESOLVER est explicite
resolver indisponible != panne MORPHEUS
cible supprimée -> STALE, référence conservée
historique de résolution conservé
```

Aucune intégration MINOS/GitHub/Jira concrète n'est requise par le cœur.

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
- [`../VALIDATION_M2.md`](../VALIDATION_M2.md) — candidate jusqu'au gate S8.

---

# Principe de validation

Pour chaque slice :

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter le Maven Wrapper
5. accepter l'ADR uniquement après preuve
6. merger
7. mettre à jour roadmap + issue de milestone
```
