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
| [ADR-0017](0017-maven-build-foundation.md) | Maven 3.9.16 + Maven Wrapper, `release=21` | **Acceptée avec contraintes — M0** |
| [ADR-0018](0018-sqlite-initial-persistent-store.md) | SQLite comme backend persistant initial derrière le port | **Acceptée avec contraintes — M0** |
| [ADR-0019](0019-maven-coordinates-java-namespace.md) | `io.github.fturleque` + namespace Java `com.morpheus.*` | **Proposée — bootstrap M1** |

La décision de sortie C0 est consignée dans [`../VALIDATION_C0.md`](../VALIDATION_C0.md).

La décision de sortie M0, les preuves et les contraintes d'acceptation sont consignées dans [`../VALIDATION_M0.md`](../VALIDATION_M0.md).

---

## Contraintes actives issues de M0

### ADR-0001

Ajouter au bootstrap M1 un test d'architecture empêchant :

```text
domain -> provider-openspec
domain -> SQLite
domain -> CLI/MCP/API adapters
```

### ADR-0002

Le premier provider de production vise initialement :

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

Le premier squelette M1 doit prouver :

```text
mvnw.cmd test   sur Windows
./mvnw test     en CI
maven.compiler.release = 21
```

Maven 4 reste différé jusqu'à GA et validation de migration.

### ADR-0018

SQLite reste caché derrière `SpecificationKnowledgeStore`.

Le schéma JSON du spike E08 est **rejeté** comme schéma de production.

Le bootstrap M1 doit créer un schéma versionné/migrable et vérifier le driver sous Windows.

### ADR-0019

Le bootstrap M1 doit trancher définitivement les coordonnées avant la création de classes durables :

```text
groupId = io.github.fturleque
artifact prefix = morpheus-
Java namespace = com.morpheus
```

et prouver les dépendances dirigées vers l'intérieur.

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
- [`../VALIDATION_M0.md`](../VALIDATION_M0.md).
