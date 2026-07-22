# ADR — MORPHEUS

Ce répertoire contient les **Architecture Decision Records** de MORPHEUS.

Les ADR décrivent les décisions structurantes, leur contexte, les alternatives, les conséquences, les risques et les preuves attendues avant acceptation.

## Statuts

- **Proposée** : décision candidate en cours de cadrage ou d'expérimentation ;
- **Acceptée** : décision validée et applicable ;
- **Acceptée avec contraintes** : décision validée sous conditions explicites ;
- **Remplacée** : décision supersédée par une ADR plus récente ;
- **Rejetée** : option étudiée puis explicitement non retenue.

Une ADR proposée ne devient pas automatiquement une décision définitive parce qu'elle est documentée.

---

## Index

| ADR | Décision | Statut |
|---|---|---|
| [ADR-0001](0001-morpheus-owned-domain.md) | Domaine MORPHEUS indépendant des formats et providers | **Acceptée avec contraintes — M0** |
| [ADR-0002](0002-openspec-reference-provider.md) | OpenSpec comme premier provider de référence sans verrouillage | **Acceptée avec contraintes — M0** |
| [ADR-0003](0003-specification-knowledge-store.md) | Persistance derrière `SpecificationKnowledgeStore` | **Acceptée — M0** |
| [ADR-0004](0004-local-first-no-llm-core.md) | Cœur local-first et sans LLM obligatoire | **Acceptée — M0** |
| [ADR-0005](0005-traceability-first-class.md) | Traçabilité comme concept de premier ordre | **Acceptée — M0** |
| [ADR-0006](0006-current-vs-proposed-state.md) | Distinction structurelle état courant / proposé / historique | **Acceptée — M0** |
| [ADR-0007](0007-cross-engine-integration.md) | Intégrations cross-engine découplées | **Acceptée — M0** |
| [ADR-0008](0008-read-first-write-capability.md) | Providers read-first, écriture séparée et optionnelle | **Acceptée — M0** |
| [ADR-0009](0009-stable-domain-identity.md) | Séparer identité logique, version, emplacement et identifiant externe | **Acceptée — M0** |
| [ADR-0010](0010-traceability-relation-taxonomy.md) | Taxonomie contrôlée des relations de traçabilité | **Acceptée avec contraintes — M0** |
| [ADR-0011](0011-provider-capability-negotiation.md) | Sélection des providers par capacités effectives | **Acceptée — M0** |
| [ADR-0012](0012-snapshot-versioning-strategy.md) | Publication par snapshots versionnés et activation atomique | **Acceptée — M0** |
| [ADR-0013](0013-change-lifecycle-state-machine.md) | Cycle de vie des changements sous forme de machine d'état | **Acceptée avec contraintes — M0** |
| [ADR-0014](0014-defer-production-technology-stack.md) | Différer le choix de stack de production jusqu'aux preuves nécessaires | **Acceptée — C0** |
| [ADR-0015](0015-domain-identity-uuidv7.md) | UUIDv7 comme format canonique opaque de `DomainIdentity` | **Acceptée — M0** |
| [ADR-0016](0016-java-21-production-baseline.md) | Java avec source/bytecode baseline 21 | **Acceptée — M0** |
| [ADR-0017](0017-maven-build-foundation.md) | Maven 3.9.16 + Maven Wrapper, `release=21` | **Acceptée avec contraintes — M0/M1** |
| [ADR-0018](0018-sqlite-initial-persistent-store.md) | SQLite comme backend persistant initial derrière le port | **Acceptée avec contraintes — M0** |
| [ADR-0019](0019-maven-coordinates-java-namespace.md) | `io.github.fturleque` + namespace Java `com.morpheus.*` | **Acceptée — bootstrap M1** |
| [ADR-0020](0020-workspace-root-resolution.md) | Discovery explicit-first avec fallback Git structurel sans dépendance au binaire Git | **Acceptée — M1** |
| [ADR-0021](0021-sqlite-schema-migrations-foundation.md) | Migrations SQLite explicites, versionnées et schéma V1 minimal normalisé | **Acceptée — M1** |
| [ADR-0022](0022-m2-normalized-content-before-temporal-projection.md) | Normaliser le contenu en M2 avant la projection temporelle M3 | **Acceptée — M2** |
| [ADR-0023](0023-persistent-provider-scoped-entity-identity.md) | Persister les mappings d'identité provider-scoped avec continuité explicite | **Proposée — validation M2** |

Les décisions de sortie sont consignées dans :

- [`../VALIDATION_C0.md`](../VALIDATION_C0.md) ;
- [`../VALIDATION_M0.md`](../VALIDATION_M0.md) ;
- [`../VALIDATION_M1.md`](../VALIDATION_M1.md).

---

## Contraintes actives

### ADR-0001

Le test d'architecture empêche :

```text
domain -> provider-openspec
domain -> SQLite
domain -> CLI/MCP/API adapters
```

### ADR-0002

Le premier provider de production vise :

```text
OpenSpec schema = spec-driven
```

Les schémas inconnus échouent explicitement.

`Scenario` n'est pas automatiquement un `AcceptanceCriterion`.

### ADR-0010

La taxonomie initiale reste gouvernée. `RELATED_TO` est faible ; la transitivité n'est jamais implicite ; `IMPLEMENTS / SATISFIES` pourra être réévalué sur les cas M4.

### ADR-0013

L'étape `DESIGNED` peut être sautée uniquement par politique explicite lorsque `design_required=false`.

`COMPLETED` ne promeut pas automatiquement la spécification en `CURRENT`.

### ADR-0017

Le bootstrap M1 a prouvé sous Windows :

```text
mvnw.cmd clean test
Apache Maven 3.9.16
maven.compiler.release = 21
javac release 21
BUILD SUCCESS
```

Le Maven Wrapper constitue le gate de build obligatoire. Une CI distante reste optionnelle.

Maven 4 reste différé jusqu'à GA et validation de migration.

### ADR-0018

SQLite reste caché derrière `SpecificationKnowledgeStore` et les ports de persistance applicatifs.

Le schéma JSON du spike E08 est **rejeté** comme schéma de production.

Le schéma et le mécanisme de migrations sont gouvernés par ADR-0021.

### ADR-0019

Coordonnées et namespace validés :

```text
groupId = io.github.fturleque
artifact prefix = morpheus-
Java namespace = com.morpheus
```

### ADR-0020

La discovery M1 a démontré :

```text
explicit path first
Git ancestor fallback only when needed
no git binary dependency
non-Git workspace support
provider-neutral source locator
recognized invalid source is never masked by fallback
BUILD SUCCESS
```

Le comportement spécifique aux symlinks/junctions reste différé tant qu'un cas réel ne justifie pas une canonicalisation physique.

### ADR-0021

La fondation de store M1 a démontré :

```text
UUIDv7 opaque
memory store reference
SQLite store behind same port
schema_migrations ledger
V1 = projects + knowledge_snapshots metadata
V2 = project root uniqueness
migration checksum immutability
migration history tampering rejected
atomic snapshot activation
stale predecessor rejection
persistence across reopen
no generic JSON domain payload
BUILD SUCCESS
```

Les tables métier M2+ ne sont créées qu'après stabilisation de leurs contrats Java.

Le warning JDK 24 `--enable-native-access=ALL-UNNAMED` du driver SQLite reste une contrainte runtime/packaging à traiter avant stabilisation CLI.

### ADR-0022

Le premier slice M2 a démontré sous Windows :

```text
provider source
    ↓
provider-internal parsing
    ↓
MORPHEUS normalization
    ↓
provider-neutral content
```

sans projeter prématurément :

```text
CURRENT / PROPOSED / HISTORICAL
```

qui reste une responsabilité M3.

Preuves :

```text
OpenSpecCurrentSpecificationReaderTest   3/3 PASS
NormalizedProjectContentTest             3/3 PASS
TOTAL                                    48/48 PASS
BUILD SUCCESS
```

Le slice normalise `Specification`, `Requirement` et `Scenario`, attache provenance/evidence aux entités importées, garde `DomainIdentity` distinct des clés externes et ne laisse aucun type OpenSpec traverser `com.morpheus.domain`.

### ADR-0023

Le slice en validation doit démontrer :

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7 stable
```

Règles :

```text
provider namespace is part of identity resolution
same key -> same DomainIdentity
external id change -> continuity only when explicit
same key + different DomainIdentity -> IDENTITY_COLLISION
no title/path/content similarity merge
```

Memory et SQLite doivent appliquer le même contrat. SQLite utilise `V003__entity_identity_bindings.sql` et la stabilité doit survivre à une réouverture de la base.

---

## Règles de rédaction

Chaque ADR structurante doit préciser autant que possible :

1. contexte ;
2. problème ;
3. forces en présence ;
4. décision proposée ou adoptée ;
5. invariants ;
6. conséquences positives ;
7. conséquences négatives ;
8. alternatives étudiées ;
9. risques et mitigations ;
10. plan de validation ;
11. critères ou condition d'acceptation ;
12. impact sur les autres décisions.

Les décisions reposant sur une technologie doivent comporter des critères permettant de **revoir ou remplacer** cette technologie si les expérimentations ne confirment pas les hypothèses.

---

## Principe de validation

Une ADR dépendante d'une hypothèse technique doit référencer une preuve avant de passer à `Acceptée`.

Les résultats de benchmark ou d'expérimentation doivent préciser au minimum :

```text
hypothèse
dataset
environnement
mesures
limites observées
décision
contraintes
```

Le registre doit être lu avec :

- [`../research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md) ;
- [`../../experiments/m0/results/README.md`](../../experiments/m0/results/README.md) ;
- [`../VALIDATION_M0.md`](../VALIDATION_M0.md) ;
- [`../VALIDATION_M1.md`](../VALIDATION_M1.md).
