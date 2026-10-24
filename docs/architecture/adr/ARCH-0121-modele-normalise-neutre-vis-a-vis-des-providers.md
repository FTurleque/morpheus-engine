---
id: ARCH-0121
title: "Normaliser le contenu des providers sans temporalité ni effet appliqué"
domain: architecture
rules: false
---

# Normaliser le contenu des providers sans temporalité ni effet appliqué

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0022, ADR-0024, ADR-0025

## Context

Une source provider doit être transformée en concepts MORPHEUS indépendants de son format. OpenSpec, par exemple,
range ses fichiers en `specs/`, `changes/` et archives, décrit un changement dans `proposal.md`, `design.md` et
`tasks.md`, et ses deltas de requirements dans `changes/<change>/specs/**/spec.md` sous les sections `ADDED`,
`MODIFIED` et `REMOVED`.

Porter la temporalité (`CURRENT / PROPOSED / HISTORICAL`) dès la normalisation créerait trois risques : coupler la
normalisation structurelle à la reconstruction temporelle, loger dans le domaine des règles qui appartiennent au
versionnement, et laisser l'arborescence d'un provider dicter la temporalité MORPHEUS. De même, le contenu normalisé
d'un changement ne doit pas être confondu avec son cycle de vie, ni un delta avec son application à la baseline.

Certaines sources n'identifient pas leurs éléments : une puce de contrainte ou une ligne de `tasks.md` n'a pas
d'identifiant. Utiliser son texte comme identité ferait varier l'identité à chaque reformulation.

Une `CURRENT` baseline et un delta `PROPOSED MODIFIED` du même requirement doivent coexister sans que l'ingestion du
delta remplace silencieusement la baseline.

## Decision

### Contenu normalisé sans temporalité

Le contenu structurel d'un projet est normalisé en concepts provider-neutral : `ProjectSpecification`,
`Specification`, `Requirement`, `Scenario`, `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision`,
`ImplementationTask`, `AcceptanceCriterion`, `Provenance`, `Evidence` et `ExternalReference`.

Les records du modèle normalisé ne portent pas `TemporalState`. La temporalité est projetée par une occurrence
versionnée qui enveloppe le contenu sans le modifier (ADR-0031). Un provider peut garder dans ses faits internes toute
information utile à cette projection, sans qu'elle devienne une propriété publique du domaine.

```text
provider facts         != MORPHEUS domain
content normalization  != temporal projection
Scenario               != AcceptanceCriterion
DomainIdentity         != SourceLocator != externalId
```

Une position dans l'arborescence d'un provider ne suffit pas à définir un état temporel MORPHEUS. Un scénario ne
devient pas un critère d'acceptation sans règle explicite (ADR-0081).

### Identité, locator et clé externe

Trois notions restent distinctes :

- `DomainIdentity` : identité MORPHEUS, UUIDv7 résolue et persistée par provider (ADR-0023) ;
- `SourceLocator` : emplacement de la source ;
- clé externe : identifiant provider-scoped à partir duquel l'identité est résolue.

Deux providers qui emploient la même clé externe produisent des identités différentes. Le texte source ne devient
jamais une identité MORPHEUS.

### Changement, sous-entités et cycle de vie

`ChangeProposal`, `Constraint`, `DesignDecision` et `ImplementationTask` ont des identités typées (`ChangeId`,
`ConstraintId`, `DesignDecisionId`, `TaskId`). Chaque sous-entité référence explicitement son `ChangeId`. Ce
rattachement est structurel ; les relations de traçabilité génériques relèvent d'ADR-0118.

Le contenu normalisé d'un changement ne porte ni état de cycle de vie, ni transition, ni promotion `CURRENT`
(ADR-0119). Une `ImplementationTask` peut conserver le seul booléen `completed`, lorsqu'il est encodé explicitement par
une case à cocher : c'est un fait source minimal, pas le cycle de vie du changement.

```text
change structure != temporal projection
source checkbox  != ChangeLifecycleState
```

### Clé structurelle des éléments anonymes

Quand la source ne fournit pas d'identifiant à un élément, le provider génère une **clé externe structurelle**
provider-scoped, fondée sur la position de l'élément et jamais sur son texte. Le provider OpenSpec emploie
`constraint:<changeKey>:<ordinal>` et `task:<changeKey>:<ordinal>`. Une décision de conception, qui a un titre de
section explicite, reçoit une clé dérivée du `changeKey` et de ce titre.

Conséquence assumée : réordonner des éléments anonymes peut changer leur clé structurelle. La continuité se déclare
alors explicitement ; aucune fusion par texte ou par similarité n'est permise.

### Deltas de requirements

`RequirementDelta` porte un `RequirementDeltaId`, son `ChangeId`, un `RequirementDeltaKind` (`ADDED`, `MODIFIED`,
`REMOVED`), la clé de la spécification concernée, le `RequirementId` logique, une clé métier optionnelle, un titre, un
énoncé, ses scénarios et sa provenance.

```text
RequirementDeltaId != RequirementId
```

`RequirementDeltaId` identifie l'occurrence « le changement X modifie le requirement Y » ; `RequirementId` identifie le
requirement logique Y. Le requirement logique d'un delta est résolu par la même clé externe que le requirement
courant, si bien qu'un `MODIFIED` partage le `RequirementId` de sa baseline tout en gardant un contenu séparé ;
l'occurrence de delta a sa propre clé externe. Le provider OpenSpec emploie `requirement:<specificationKey>/<slug>` pour
le requirement et `requirement-delta:<changeKey>:<kind>:<requirementKey>` pour l'occurrence.

- `ADDED` : le requirement logique reçoit une nouvelle identité stable ; il n'entre pas dans la baseline courante.
- `MODIFIED` : le requirement réutilise son `RequirementId` courant quand la clé externe est connue ; baseline et delta
  restent deux contenus séparés.
- `REMOVED` : le delta peut exister sans énoncé si la source ne donne que l'identité ou le titre de l'élément supprimé ;
  `ADDED` et `MODIFIED` exigent un énoncé.

Les scénarios d'un delta restent des `Scenario` provider-neutral, embarqués dans `RequirementDelta.scenarios` et
rattachés au `RequirementId` du delta ; ils n'entrent pas dans la collection des scénarios courants.

```text
RequirementDeltaKind                   != TemporalState
normalized delta                       != applied delta
structural ownership by ChangeId       != traceability graph
```

Un delta normalisé n'est pas un delta appliqué : l'application, la promotion et la suppression effective d'un
requirement de la projection courante relèvent d'ADR-0035.

### Enveloppe normalisée

`NormalizedProjectContent` réunit les spécifications, requirements, scénarios courants, changements, deltas,
contraintes, décisions, tâches, critères d'acceptation, preuves et diagnostics d'un projet. À la construction, elle
vérifie que chaque spécification et chaque changement appartiennent au projet, que chaque sous-entité et chaque delta
référencent un changement connu, que chaque requirement et chaque scénario courant référencent une entité connue, que
chaque identité est unique dans sa famille, et que chaque provenance (scénarios de delta compris) référence une preuve
présente. Un requirement courant et un delta `MODIFIED` partagent légitimement le même `RequirementId`.

Chaque entité normalisée porte une `Provenance` : un `SourceLocator` et un `EvidenceId` vers une `Evidence` (plage et
SHA-256).

### Composition des lecteurs

Un provider compose des lecteurs séparés plutôt qu'un parser monolithique. Pour OpenSpec :
`OpenSpecCurrentSpecificationReader`, `OpenSpecChangeMetadataReader` et `OpenSpecRequirementDeltaReader` alimentent
`OpenSpecProjectContentReader`, qui produit le `NormalizedProjectContent`.

## Do's and Don'ts

### Do

- Normaliser le contenu en concepts provider-neutral, avec provenance et preuve pour chaque entité.
- Rattacher chaque contrainte, décision, tâche et delta à un `ChangeId` connu.
- Générer une clé structurelle positionnelle pour un élément sans identifiant.
- Résoudre le requirement d'un delta par la même clé externe que le requirement courant.
- Garder séparés le contenu de la baseline et celui d'un delta `MODIFIED`.
- Déclarer explicitement la continuité d'un élément anonyme réordonné.

### Don't

- Ne pas ajouter `TemporalState` à un record du modèle normalisé.
- Ne pas déduire un état temporel de l'arborescence d'un provider.
- Ne pas utiliser le texte d'une contrainte, d'une tâche ou d'un élément comme identité ou clé externe.
- Ne pas fusionner des éléments par texte ou par similarité.
- Ne pas faire d'une case à cocher de tâche l'état de cycle de vie du changement.
- Ne pas ajouter un requirement `ADDED` à la baseline courante, ni laisser un delta remplacer la baseline.
- Ne pas verser les scénarios d'un delta dans les scénarios courants.
- Ne pas confondre `RequirementDeltaId` et `RequirementId`.
- Ne pas inventer l'énoncé d'un delta `REMOVED`.
- Ne pas transformer un scénario en critère d'acceptation sans règle explicite.

## Consequences

### Positives

- Les conventions d'un provider ne contaminent pas le domaine.
- Le modèle se teste avec plusieurs providers.
- La continuité d'identité d'un requirement modifié est préservée sans écraser sa baseline.
- Chaque lecteur se teste indépendamment.

### Négatives

- Le contenu normalisé seul ne donne pas une vue courante ; il faut la projection temporelle.
- Certaines informations de source restent des faits provider avant leur projection.
- Réordonner des éléments anonymes change leur clé structurelle.

### Risques

- Un provider qui dériverait une clé du texte casserait l'identité à chaque reformulation ; la revue des lecteurs le
  surveille.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/ingestion/NormalizedProjectContentTest.java` : rejets
  d'une sous-entité, d'un delta ou d'un scénario vers une entité inconnue et d'une provenance vers une preuve absente ;
  acceptation d'un `MODIFIED` qui partage l'identité logique de sa baseline.
- Constructeurs compacts de `NormalizedProjectContent` et `RequirementDelta` : énoncé exigé hors `REMOVED`, scénarios
  rattachés au requirement du delta.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecChangeMetadataReaderTest.java`
  (`anonymousConstraintAndTaskKeysAreStructuralNotTextDerived`) : clés structurelles, non dérivées du texte.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecRequirementDeltaReaderTest.java` :
  `MODIFIED` qui réutilise l'identité logique sans remplacer la baseline, occurrence distincte de l'identité logique,
  `REMOVED` sans énoncé inventé.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`) : le domaine ne dépend d'aucun provider.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : consommateurs
  neutres vis-à-vis des providers.
- Revue : aucun record du modèle normalisé ne porte `TemporalState`.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine possédé par MORPHEUS.
- [ADR-0006](ARCH-0006-current-vs-proposed-state.md) — distinction courant / proposé / historique.
- [ADR-0009](DATA-0009-stable-domain-identity.md) — identité de domaine stable.
- [ADR-0015](DATA-0015-domain-identity-uuidv7.md) — identité UUIDv7.
- [ADR-0023](DATA-0023-persistent-provider-scoped-entity-identity.md) — identité persistante scopée par provider.
- [ADR-0031](ARCH-0031-explicit-temporal-projection-and-entity-version.md) — projection temporelle explicite.
- [ADR-0034](DATA-0034-versioned-requirement-persistence.md) — persistance versionnée des requirements.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md) — preuve du jalon M2.
