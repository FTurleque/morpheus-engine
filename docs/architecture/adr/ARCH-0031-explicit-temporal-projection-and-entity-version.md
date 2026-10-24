---
id: ARCH-0031
title: "Projeter explicitement l'état temporel sur des occurrences versionnées"
domain: architecture
rules: false
---

# Projeter explicitement l'état temporel sur des occurrences versionnées

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Le contenu normalisé (ADR-0121) est stabilisé sans porter `CURRENT / PROPOSED / HISTORICAL`. MORPHEUS doit pourtant
représenter plusieurs états d'une même identité logique sans :

1. modifier l'identité stable lorsqu'un contenu évolue ;
2. confondre version métier et snapshot technique ;
3. déduire la temporalité depuis un chemin source, un dossier `archive`, un état de checkbox ou un lifecycle ;
4. laisser un contenu `PROPOSED` fuiter dans une vue `CURRENT`.

Le cas de référence est une exigence logique unique portée à trois valeurs à la fois, sans fusion ni choix arbitraire :

```text
CURRENT baseline
  auth-session/session-expiration = 30 minutes

PROPOSED change A
  same logical requirement = 60 minutes

PROPOSED change B
  same logical requirement = 15 minutes
```

Ajouter directement `TemporalState` aux records normalisés (`Specification`, `Requirement`, `Scenario`, etc.) a été
écarté : cela couplerait normalisation structurelle et projection temporelle, obligerait les providers à produire une
temporalité qu'ils ne connaissent pas toujours, casserait la frontière du modèle normalisé (ADR-0121) et dupliquerait
les mêmes champs sur toutes les entités.

## Decision

Cinq concepts portent la temporalité : `TemporalState`, `EntityVersionId`, `SpecificationVersionId`,
`EntityVersion<T>` et `SpecificationVersion`. L'occurrence versionnée **enveloppe** le contenu normalisé sans le
modifier.

### TemporalState

Les seuls états temporels sont `CURRENT`, `PROPOSED` et `HISTORICAL`. Cette dimension reste orthogonale à
`ChangeLifecycleState`, `KnowledgeSnapshotState`, à la résolution et à la vérification.

### EntityVersion

Une occurrence versionnée (`com.morpheus.domain.version.EntityVersion`) contient :

| Champ | Rôle |
|---|---|
| `EntityVersionId` | identité de l'occurrence |
| `DomainIdentity` | identité logique stable |
| `SpecificationVersionId` | version métier propriétaire |
| `TemporalState` | état temporel explicite |
| contenu | contenu normalisé |

Invariant : `EntityVersionId != DomainIdentity`. Une même `DomainIdentity` peut avoir plusieurs occurrences
versionnées.

### SpecificationVersion

`SpecificationVersion` représente une version logique du contenu de spécification : `SpecificationVersionId`,
`ProjectSpecificationId`, `sequence` optionnelle et strictement positive, `providerVersion` et `sourceRevision`
optionnels, `createdAt`, `predecessor` optionnel et explicite. Une version ne peut pas être son propre prédécesseur.

Invariant : `SpecificationVersion != KnowledgeSnapshot`.

### Capacité du modèle et politique de publication

Le modèle autorise une reconstruction technique à produire un nouvel `EntityVersionId` tout en réutilisant
explicitement le même `SpecificationVersionId` :

```text
snapshot/reingestion A -> EntityVersionId A -> SpecificationVersionId V1
snapshot/reingestion B -> EntityVersionId B -> SpecificationVersionId V1
```

Une réingestion technique n'impose donc pas intrinsèquement une nouvelle version métier (ADR-0120). La décision de
créer une nouvelle `SpecificationVersionId` appartient à une politique explicite. Le chemin officiel de publication
complète (`ProjectSnapshotImportService.publishFull()`, ADR-0126) applique la politique suivante, qui est un choix
applicatif et non une identité entre `KnowledgeSnapshot` et `SpecificationVersion` :

```text
full publish attempt -> new SpecificationVersionId + unique project-local sequence
FAILED candidate     -> version remains durable and consumes its sequence
successful retry     -> allocates the next sequence
predecessor          -> remains the previously ACTIVE published version
```

D'autres orchestrations peuvent réutiliser explicitement une version ; la publication complète officielle choisit de
ne pas le faire.

### Projection applicative

`com.morpheus.application.temporal.TemporalProjection<T>` fournit `all()`, `current()`, `proposed()`, `historical()`,
`byState(state)`, `forEntity(identity)` et `currentFor(identity)`.

La vue `CURRENT` filtre exclusivement sur `TemporalState == CURRENT`. Aucune autre propriété ne provoque de promotion
implicite.

### Unicité CURRENT

Dans une projection observable donnée, une `DomainIdentity` a au plus une occurrence `CURRENT`. Deux occurrences
`CURRENT` pour la même identité logique rendent la projection ambiguë et sont rejetées. Plusieurs occurrences
`PROPOSED` concurrentes sont autorisées :

```text
same DomainIdentity
├── CURRENT   30 minutes
├── PROPOSED  60 minutes
└── PROPOSED  15 minutes
```

La vue courante reste `30 minutes`.

## Do's and Don'ts

### Do

- Envelopper le contenu normalisé dans une `EntityVersion` qui porte l'état temporel.
- Donner à chaque occurrence un `EntityVersionId` distinct de sa `DomainIdentity`.
- Conserver séparément plusieurs propositions concurrentes pour la même identité logique.
- Rejeter une projection qui porte deux occurrences `CURRENT` pour la même identité.
- Rendre explicite le prédécesseur d'une `SpecificationVersion`.
- Laisser à une politique explicite le choix de créer ou de réutiliser une `SpecificationVersionId`.

### Don't

- Ne pas ajouter `TemporalState` aux records du modèle normalisé.
- Ne pas déduire l'état temporel d'un chemin, d'un dossier `archive`, d'une checkbox ou d'un lifecycle.
- Ne pas inclure `PROPOSED` ou `HISTORICAL` dans `current()` ou `currentFor()`.
- Ne pas promouvoir implicitement une occurrence en `CURRENT` sur une autre propriété que son état temporel.
- Ne pas confondre `SpecificationVersion` et `KnowledgeSnapshot`.
- Ne pas déclarer une `SpecificationVersion` comme son propre prédécesseur.

## Consequences

### Positives

- Les états concurrents d'une même exigence coexistent sans fusion ni choix arbitraire.
- L'identité logique reste stable quand le contenu évolue.
- Le modèle normalisé reste indépendant de la temporalité et des providers.
- Version métier et snapshot technique restent deux notions distinctes.

### Négatives

- Une indirection supplémentaire (occurrence versionnée) entre l'identité logique et son contenu.
- La politique de création de version doit être choisie explicitement par chaque orchestration.

### Risques

- Lire la politique de la publication complète comme une propriété du modèle conduirait à confondre snapshot et
  version métier ; la distinction capacité / politique ci-dessus le prévient.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/version/TemporalVersioningTest.java` : trois états temporels
  seulement, `EntityVersionId` distinct de la `DomainIdentity`, plusieurs occurrences par identité, `sequence`
  positive, prédécesseur différent de la version.
- `morpheus-application/src/test/java/com/morpheus/application/temporal/TemporalProjectionTest.java` : la vue
  courante exclut `PROPOSED` et `HISTORICAL`, propositions concurrentes conservées, deux `CURRENT` rejetés,
  réutilisation explicite d'une `SpecificationVersionId` par une réingestion.
- Constructeur compact de `SpecificationVersion` et `TemporalProjection` : rejets à la construction.
- Revue : aucun record du modèle normalisé ne porte `TemporalState`.

## References

- [ADR-0006](ARCH-0006-current-vs-proposed-state.md) — distinction courant / proposé / historique.
- [ADR-0009](DATA-0009-stable-domain-identity.md) — identité de domaine stable.
- [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre vis-à-vis des
  providers.
- [ADR-0034](DATA-0034-versioned-requirement-persistence.md) — persistance versionnée des exigences.
- [ADR-0126](DATA-0126-publication-complete-d-un-snapshot.md) — publication complète d'un snapshot.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md) — preuve du jalon M3.
