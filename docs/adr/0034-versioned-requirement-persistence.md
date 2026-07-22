# ADR-0034 — Introduire une persistance métier versionnée par occurrence

- Statut : **Acceptée — M3**
- Date : 22 juillet 2026
- Dépend de : ADR-0012, ADR-0021, ADR-0022, ADR-0030, ADR-0031, ADR-0033
- Portée : M3-S4, première persistance métier normalisée, ownership version/snapshot

## Contexte

M3-S1 a introduit :

```text
EntityVersion<T>
├── EntityVersionId
├── DomainIdentity
├── SpecificationVersionId
├── TemporalState
└── content
```

M3-S3 a stabilisé le lifecycle technique de `KnowledgeSnapshot` et l'activation atomique observable.

Il manquait encore le premier stockage durable du contenu métier lui-même. ADR-0030 impose que chaque occurrence persistée réponde explicitement à :

```text
quelle identité logique ?
quelle occurrence ?
quelle SpecificationVersion ?
quel KnowledgeSnapshot ?
quel TemporalState ?
```

## Décision

M3-S4 introduit un vertical slice complet sur `Requirement` plutôt que de créer prématurément toutes les tables métier.

`Requirement` est retenu comme entité de référence car il porte déjà :

- une identité MORPHEUS stable ;
- un rattachement structurel à `Specification` ;
- un contenu métier ;
- une provenance ;
- et il sert d'oracle à la séparation `CURRENT / PROPOSED`.

Les autres familles métier devront réutiliser le même pattern après validation de ce slice.

## Séparation version logique / snapshot technique

Une même `SpecificationVersion` peut être représentée par plusieurs snapshots techniques, par exemple après reconstruction ou changement de provider.

Donc :

```text
SpecificationVersion 1 <--- N KnowledgeSnapshot
```

La relation est matérialisée séparément et ne fait pas de l'identité du snapshot une identité métier.

## Port applicatif

Introduire :

```text
VersionedRequirementStore
SnapshotSpecificationVersionBinding
RequirementVersionRecord
CurrentRequirementQueryService
```

`RequirementVersionRecord` associe :

```text
KnowledgeSnapshotId
EntityVersion<Requirement>
```

et impose :

```text
entityVersion.entityIdentity == requirement.id.value
```

Le store exige en plus que :

```text
snapshot -> bound SpecificationVersion
             == entityVersion.specificationVersionId
```

## Schéma SQLite

Migration `V004__versioned_requirement_persistence.sql` :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Aucune payload JSON générique n'est autorisée.

### specification_versions

Persiste la version logique indépendamment des snapshots techniques.

### snapshot_specification_versions

Binding explicite :

```text
snapshot_id -> specification_version_id
```

Plusieurs snapshots peuvent référencer la même version logique.

### requirement_versions

Persiste les colonnes typées nécessaires pour reconstruire exactement :

```text
EntityVersion<Requirement>
```

avec provenance inline pour ce vertical slice.

Le schéma conserve explicitement :

```text
entity_version_id
entity_identity_id
requirement_id
specification_id
specification_version_id
snapshot_id
temporal_state
key/title/statement
provider/source/provenance/evidence
```

## Unicité CURRENT

Dans un snapshot donné :

```text
(snapshot_id, entity_identity_id, CURRENT)
```

est unique.

Plusieurs occurrences `PROPOSED` de la même identité logique restent permises.

Le store mémoire applique la même règle que SQLite.

## Vue CURRENT

`CurrentRequirementQueryService` ne lit jamais toutes les occurrences d'un projet puis ne choisit arbitrairement.

Il procède par :

```text
project
  ↓
activeSnapshot(project)
  ↓
currentRequirement(snapshotId, DomainIdentity)
```

Donc un `PROPOSED` stocké dans le snapshot actif reste invisible dans la vue `CURRENT`.

Après redémarrage SQLite, cette séparation reste identique.

## Idempotence et collisions

`putSpecificationVersion`, `bindSnapshotVersion` et `putRequirementVersion` sont idempotents lorsque la définition est identique.

Même identité technique avec définition différente -> `KnowledgeStoreException`.

Un binding snapshot/version incompatible ou une seconde occurrence `CURRENT` pour la même identité dans un snapshot -> rejet explicite.

## Frontières

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
TemporalState != KnowledgeSnapshotState
normalized Requirement != persisted occurrence
```

Le slice ne modifie pas les providers et ne rend pas SQLite visible depuis le domaine.

## Hors périmètre S4

- persistance des autres familles métier : extension ultérieure du même pattern ;
- application/promotion de `RequirementDelta` : S5 ;
- comparaison/rétention : S6 ;
- graphe de traçabilité : M4 ;
- payload JSON métier ;
- ORM ou framework serveur.

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur le head `b4a367d7e985471e178e5bed49065ecd971b65e6` :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultat :

```text
Domain                           13 tests
Application                      54 tests
OpenSpec provider                26 tests
Synthetic provider                7 tests
SQLite store                      7 tests
Architecture tests               27 tests
-----------------------------------------
TOTAL                           134/134 PASS
Failures                           0
Errors                             0
Skipped                            0
BUILD SUCCESS
```

Preuves spécifiques :

```text
VersionedRequirementPersistenceTest  7/7 PASS
SqliteSchemaMigrationTest            4/4 PASS
```

Les 13 critères d'acceptation sont démontrés :

1. `SpecificationVersion` est persistée et reconstruite à l'identique ;
2. le binding snapshot/version est explicite et cohérent avec le projet ;
3. `RequirementVersionRecord` est reconstructible à l'identique ;
4. Memory et SQLite respectent le même contrat ;
5. plusieurs snapshots peuvent représenter la même `SpecificationVersion` ;
6. deux `PROPOSED` concurrents de même identité sont permis ;
7. deux `CURRENT` de même identité dans le même snapshot sont rejetés ;
8. un requirement ne peut pas référencer une version différente de celle liée au snapshot ;
9. `CurrentRequirementQueryService` consulte uniquement le snapshot `ACTIVE` ;
10. un `PROPOSED` ne fuit jamais dans la vue `CURRENT` ;
11. fermeture/réouverture SQLite reconstruit le même état observable ;
12. aucune payload JSON générique n'est ajoutée ;
13. le Maven Wrapper complet est vert.

Warnings connus non bloquants : Xerial/JDK24 native access et SLF4J NOP dans les tests ArchUnit.

Décision finale :

```text
ADR-0034 = ACCEPTÉE — M3
M3-S4    = VALIDÉ — 134/134
```
