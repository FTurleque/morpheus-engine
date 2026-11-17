---
id: ARCH-0136
title: "L'état temporel est une donnée normalisée, portée par des occurrences versionnées"
domain: architecture
rules: false
---

# L'état temporel est une donnée normalisée, portée par des occurrences versionnées

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0006, ADR-0031

## Context

Un moteur d'intelligence des spécifications doit éviter une ambiguïté critique : confondre ce que le système **doit
déjà faire aujourd'hui** avec ce qu'un changement **propose qu'il fasse demain**. Cette confusion peut conduire un
agent à traiter une fonctionnalité non encore validée comme existante, à ignorer qu'une exigence est en cours de
remplacement, à produire du code contre une version future non approuvée, à mélanger plusieurs changements
concurrents, ou à construire un contexte incohérent pour NEXUS ou JARVIS.

OpenSpec offre une séparation utile entre specs courantes et changements/deltas, mais MORPHEUS possède cette
distinction indépendamment d'OpenSpec. Le contenu normalisé (ADR-0121) ne porte pas `CURRENT / PROPOSED /
HISTORICAL`. MORPHEUS doit pourtant représenter plusieurs états d'une même identité logique sans modifier l'identité
stable lorsqu'un contenu évolue, sans confondre version métier et snapshot technique, sans déduire la temporalité
d'un chemin source, d'un dossier `archive`, d'un état de checkbox ou d'un lifecycle, et sans laisser un contenu
`PROPOSED` fuiter dans une vue `CURRENT`.

Le cas de référence est une exigence logique unique portée à trois valeurs à la fois, sans fusion ni choix arbitraire :

```text
CURRENT baseline
  auth-session/session-expiration = 30 minutes

PROPOSED change A
  same logical requirement = 60 minutes

PROPOSED change B
  same logical requirement = 15 minutes
```

Options écartées :

- **Fusionner immédiatement les changements dans une vue unique** : détruit la distinction temporelle essentielle.
- **Déduire l'état uniquement du chemin du provider** comme modèle public : le provider peut utiliser ses chemins pour
  déterminer l'état, mais le domaine stocke la sémantique normalisée.
- **Ajouter directement `TemporalState` aux records normalisés** (`Specification`, `Requirement`, `Scenario`, etc.) :
  cela couplerait normalisation structurelle et projection temporelle, obligerait les providers à produire une
  temporalité qu'ils ne connaissent pas toujours, casserait la frontière du modèle normalisé (ADR-0121) et
  dupliquerait les mêmes champs sur toutes les entités.

Les trois dimensions que la décision garde séparées, et les champs d'une occurrence versionnée
(`com.morpheus.domain.version.EntityVersion`) :

| Dimension | Type | Valeurs |
|---|---|---|
| Position par rapport à l'état courant | `com.morpheus.domain.temporal.TemporalState` | `CURRENT`, `PROPOSED`, `HISTORICAL` |
| Cycle de vie d'un changement | `com.morpheus.domain.change.lifecycle.ChangeLifecycleState` | celles d'ADR-0119 |
| Qualité / résolution de l'information | `com.morpheus.domain.traceability.TraceabilityResolutionState` | `RESOLVED`, `PARTIALLY_RESOLVED`, `UNRESOLVED`, `HEURISTIC` |

| Champ | Rôle |
|---|---|
| `EntityVersionId` | identité de l'occurrence |
| `DomainIdentity` | identité logique stable |
| `SpecificationVersionId` | version métier propriétaire |
| `TemporalState` | état temporel explicite |
| contenu | contenu normalisé |

Une reconstruction technique peut produire un nouvel `EntityVersionId` en réutilisant explicitement la même version
métier : une réingestion n'impose pas intrinsèquement une nouvelle version (ADR-0143). La politique de la publication
complète officielle est un choix applicatif, non une identité entre `KnowledgeSnapshot` et `SpecificationVersion` ;
d'autres orchestrations peuvent réutiliser explicitement une version.

`com.morpheus.application.temporal.TemporalProjection<T>` fournit `all()`, `current()`, `proposed()`, `historical()`,
`byState(state)`, `forEntity(identity)` et `currentFor(identity)`.

```text
snapshot/reingestion A -> EntityVersionId A -> SpecificationVersionId V1
snapshot/reingestion B -> EntityVersionId B -> SpecificationVersionId V1

full publish attempt -> new SpecificationVersionId + unique project-local sequence
FAILED candidate     -> version remains durable and consumes its sequence
successful retry     -> allocates the next sequence
predecessor          -> remains the previously ACTIVE published version

same DomainIdentity
├── CURRENT   30 minutes      <- la vue courante reste 30 minutes
├── PROPOSED  60 minutes
└── PROPOSED  15 minutes
```

## Decision

1. **Séparation** : courant, proposé ou en cours, historique archivé ou supersédé sont normalisés à part,
   jamais déduits d'un chemin, d'un dossier `archive`, d'une checkbox ou d'un lifecycle.
2. **Orthogonalité** : `TemporalState` (`CURRENT`, `PROPOSED`, `HISTORICAL` seuls) reste distinct de
   `ChangeLifecycleState`, `KnowledgeSnapshotState`, `TraceabilityResolutionState` et de la vérification, sans
   enum unique. Le statut externe du provider reste en provenance ; sans équivalence sûre : `UNKNOWN` ou
   diagnostic.
3. **Occurrence** : `EntityVersion<T>` enveloppe le contenu normalisé sans le modifier, avec `EntityVersionId`
   (`!= DomainIdentity`), `SpecificationVersionId` et `TemporalState` ; aucun record normalisé ne porte
   `TemporalState`. Une identité admet plusieurs occurrences.
4. **Version métier** : `SpecificationVersion` (`ProjectSpecificationId`, `sequence` optionnelle > 0,
   `providerVersion`, `sourceRevision`, `createdAt`, `predecessor` optionnel explicite, jamais elle-même) n'est pas un
   `KnowledgeSnapshot` ; créer ou réutiliser une version relève d'une politique explicite.
   `ProjectSnapshotImportService.publishFull()` (ADR-0144) crée par tentative une version à séquence locale unique,
   gardée par un candidat `FAILED` (une reprise prend la suivante), de prédécesseur la version `ACTIVE` précédente.
5. **Vue courante** : `current()` et `currentFor` de `TemporalProjection<T>` filtrent sur `TemporalState == CURRENT`
   seul ; deux `CURRENT` d'une identité sont rejetés, plusieurs `PROPOSED` admis. `get_current_specification`
   n'incorpore aucun delta actif ; `preview_specification_after(changeId)` se déclare simulation.
6. **Concurrence** : des changements actifs ne sont ni supposés compatibles ni fusionnés ; chacun garde identité,
   base, spécifications affectées, dépendances, conflits. Archives et éléments supersédés restent interrogeables.

## Do's and Don'ts

### Do

- Porter la position temporelle (courant, proposé, historique) comme une donnée normalisée du modèle, dans une
  `EntityVersion` qui enveloppe le contenu normalisé.
- Donner à chaque occurrence un `EntityVersionId` distinct de sa `DomainIdentity`.
- Conserver séparément plusieurs propositions concurrentes pour la même identité logique.
- Rejeter une projection qui porte deux occurrences `CURRENT` pour la même identité.
- Rendre explicite le prédécesseur d'une `SpecificationVersion`.
- Laisser à une politique explicite le choix de créer ou de réutiliser une `SpecificationVersionId`.
- Marquer explicitement comme simulation ou projection toute vue qui applique un changement à l'état courant.
- Conserver l'identité, la base de référence, les spécifications affectées et les dépendances de chaque changement
  actif.
- Garder interrogeables les changements archivés et les éléments supersédés.
- Conserver le statut externe du provider dans la provenance et exposer `UNKNOWN` ou un diagnostic quand aucune
  équivalence MORPHEUS n'est sûre.

### Don't

- Ne pas ajouter `TemporalState` aux records du modèle normalisé.
- Ne pas déduire l'état temporel d'un chemin, d'un dossier `archive`, d'une checkbox ou d'un lifecycle.
- Ne pas inclure `PROPOSED` ou `HISTORICAL` dans `current()` ou `currentFor()`, ni un delta de changement actif dans
  la vue de l'état courant.
- Ne pas promouvoir implicitement une occurrence en `CURRENT` sur une autre propriété que son état temporel.
- Ne pas fusionner silencieusement plusieurs changements actifs ni les supposer compatibles.
- Ne pas mélanger position temporelle, cycle de vie et résolution dans une seule enum.
- Ne pas confondre `SpecificationVersion` et `KnowledgeSnapshot`.
- Ne pas déclarer une `SpecificationVersion` comme son propre prédécesseur.
- Ne pas inventer une équivalence entre un statut provider et un état MORPHEUS.

## Consequences

### Positives

- Contexte fiable pour les agents ; distinction claire entre vérité courante et intention future.
- Les états concurrents d'une même exigence coexistent sans fusion ni choix arbitraire.
- L'identité logique reste stable quand le contenu évolue.
- Le modèle normalisé reste indépendant de la temporalité et des providers.
- Version métier et snapshot technique restent deux notions distinctes.
- Historique intelligible, meilleure analyse de changement et comparaison de versions, détection de conflits et
  projections contrôlées possibles.

### Négatives

- Modèle plus complexe : une indirection supplémentaire (occurrence versionnée) entre l'identité logique et son
  contenu.
- Nécessité de gérer des versions, des règles d'archivage et des changements concurrents.
- La politique de création de version doit être choisie explicitement par chaque orchestration.
- Duplication logique possible de certaines exigences entre état courant et deltas.

### Risques

- **Divergence entre source et index** : mitigée par les snapshots, les empreintes et la synchronisation explicite.
- **Changement archivé mais état courant non synchronisé** : mitigé par la validation de cohérence lors de l'ingestion
  et par les diagnostics.
- **Mapping imparfait des statuts provider** : mitigé en conservant le statut externe et en exposant `UNKNOWN` ou un
  diagnostic plutôt qu'une équivalence inventée.
- Lire la politique de la publication complète comme une propriété du modèle conduirait à confondre snapshot et
  version métier ; la distinction capacité / politique ci-dessus le prévient.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/version/TemporalVersioningTest.java` : trois états temporels
  seulement, `EntityVersionId` distinct de la `DomainIdentity`, plusieurs occurrences par identité, `sequence`
  positive, prédécesseur différent de la version.
- `morpheus-application/src/test/java/com/morpheus/application/temporal/TemporalProjectionTest.java`
  (`currentViewNeverIncludesProposedOrHistoricalContent` notamment) : la vue courante exclut `PROPOSED` et
  `HISTORICAL`, propositions concurrentes conservées, deux `CURRENT` rejetés, réutilisation explicite d'une
  `SpecificationVersionId` par une réingestion.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/VersionedRequirementPersistenceTest.java` :
  la requête courante ne lit que le snapshot actif et l'état temporel `CURRENT`, en mémoire et en SQLite, et la
  séparation courant/proposé survit à la réouverture SQLite.
- Constructeur compact de `SpecificationVersion` et `TemporalProjection` : rejets à la construction.
- Revue : orthogonalité des dimensions, aucun record du modèle normalisé ne porte `TemporalState`, absence de fusion
  silencieuse de changements concurrents, conservation du statut provider dans la provenance.

## References

- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité de domaine stable.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — contenu métier par snapshot.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion explicites
  des deltas.
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — analyse de changement dérivée et bornée.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et concurrence bornée.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre vis-à-vis des
  providers.
- [ADR-0144](DATA-0144-publication-complete-cli-et-http.md) — publication complète d'un snapshot.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M3](../../project/validation/VALIDATION_M3.md).
