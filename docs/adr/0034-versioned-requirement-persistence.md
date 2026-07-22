# ADR-0034 — Introduire une persistance métier versionnée par occurrence

- Statut : **Proposée — validation M3-S4 requise**
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

Il manque maintenant le premier stockage durable du contenu métier lui-même. ADR-0030 impose que chaque occurrence persistée réponde explicitement à :

```text
quelle identité logique ?
quelle occurrence ?
quelle SpecificationVersion ?
quel KnowledgeSnapshot ?
quel TemporalState ?
```

## Décision proposée

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

Migration candidate `V004__versioned_requirement_persistence.sql` :

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

Après redémarrage SQLite, cette séparation doit rester identique.

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

## Critères d'acceptation

ADR-0034 passe à **Acceptée — M3** lorsque le build complet démontre :

1. `SpecificationVersion` persistée et reconstructible ;
2. binding snapshot/version explicite et cohérent avec le projet ;
3. `RequirementVersionRecord` reconstructible à l'identique ;
4. Memory et SQLite respectent le même contrat ;
5. plusieurs snapshots peuvent représenter la même `SpecificationVersion` ;
6. deux `PROPOSED` concurrents de même identité sont permis ;
7. deux `CURRENT` de même identité dans le même snapshot sont rejetés ;
8. un requirement ne peut pas référencer une version différente de celle liée au snapshot ;
9. `CurrentRequirementQueryService` consulte uniquement le snapshot `ACTIVE` ;
10. un `PROPOSED` ne fuit jamais dans la vue `CURRENT` ;
11. fermeture/réouverture SQLite reconstruit le même état observable ;
12. aucune payload JSON générique n'est ajoutée ;
13. `./mvnw clean test` / `.\mvnw.cmd clean test` est vert.
