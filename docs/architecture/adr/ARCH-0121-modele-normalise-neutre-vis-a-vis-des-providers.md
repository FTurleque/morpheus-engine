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

Les invariants que la décision tient :

```text
provider facts                   != MORPHEUS domain
content normalization            != temporal projection
Scenario                         != AcceptanceCriterion
DomainIdentity                   != SourceLocator != externalId
change structure                 != temporal projection
source checkbox                  != ChangeLifecycleState
RequirementDeltaId               != RequirementId
RequirementDeltaKind             != TemporalState
normalized delta                 != applied delta
structural ownership by ChangeId != traceability graph
```

Un provider peut garder dans ses faits internes toute information utile à la projection temporelle, sans qu'elle
devienne une propriété publique du domaine. Le booléen `completed` d'une tâche est un fait source minimal, pas le
cycle de vie du changement.

`RequirementDelta` porte un `RequirementDeltaId`, son `ChangeId`, un `RequirementDeltaKind`, la clé de la spécification
concernée, le `RequirementId` logique, une clé métier optionnelle, un titre, un énoncé, ses scénarios et sa
provenance. `RequirementDeltaId` identifie l'occurrence « le changement X modifie le requirement Y » ; `RequirementId`
identifie le requirement logique Y. `NormalizedProjectContent` réunit spécifications, requirements, scénarios
courants, changements, deltas, contraintes, décisions, tâches, critères d'acceptation, preuves et diagnostics ; à la
construction, elle vérifie que chaque spécification et chaque changement appartiennent au projet, que chaque
sous-entité et chaque delta référencent un changement connu, que chaque requirement et chaque scénario courant
référencent une entité connue, et que chaque provenance, scénarios de delta compris, référence une preuve présente. Un
requirement courant et un delta `MODIFIED` y partagent légitimement le même `RequirementId`.

Le provider OpenSpec emploie les clés `constraint:<changeKey>:<ordinal>`, `task:<changeKey>:<ordinal>`,
`requirement:<specificationKey>/<slug>` et `requirement-delta:<changeKey>:<kind>:<requirementKey>`. Ses lecteurs
`OpenSpecCurrentSpecificationReader`, `OpenSpecChangeMetadataReader` et `OpenSpecRequirementDeltaReader` alimentent
`OpenSpecSpecificationContentReader`, le lecteur du port, qui produit le `NormalizedProjectContent`. Conséquence
assumée de la clé structurelle : réordonner des éléments anonymes peut changer leur clé.

## Decision

1. **Contenu sans temporalité** : concepts provider-neutral (`ProjectSpecification`, `Specification`, `Requirement`,
   `Scenario`, `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision`, `ImplementationTask`,
   `AcceptanceCriterion`, `Provenance`, `Evidence`, `ExternalReference`), sans `TemporalState` ; la temporalité est
   une occurrence versionnée qui les enveloppe (ADR-0136), jamais déduite de l'arborescence du provider. Un scénario
   n'est critère d'acceptation que par règle (ADR-0081).
2. **Identité** : `DomainIdentity` (UUIDv7 par provider, ADR-0168), `SourceLocator` et clé externe provider-scoped
   sont distincts ; même clé, deux providers : deux identités ; le texte n'est jamais une identité.
3. **Changement** : `ChangeId`, `ConstraintId`, `DesignDecisionId`, `TaskId` ; chaque sous-entité référence son
   `ChangeId` (traçabilité : ADR-0118). Ni cycle de vie ni promotion `CURRENT` (ADR-0119) ; une tâche garde
   seulement `completed`, s'il vient d'une case à cocher.
4. **Anonymes** : clé structurelle par position, jamais par texte (décision titrée : `changeKey` et titre) ; la
   continuité se déclare, aucune fusion par texte ou similarité.
5. **Deltas** : `RequirementDelta` (`ADDED`, `MODIFIED`, `REMOVED`) a sa propre clé ; son `RequirementId` vient de la
   clé du requirement courant. `ADDED` : nouvelle identité hors baseline ; `MODIFIED` : même `RequirementId`, contenu
   séparé ; seul `REMOVED` se passe d'énoncé. Scénarios dans `RequirementDelta.scenarios`, hors courants. Application :
   ADR-0035.
6. **Enveloppe** : `NormalizedProjectContent` refuse une entité hors projet, une référence inconnue, une identité en
   double dans sa famille, une provenance sans preuve. Chaque entité a une `Provenance` (`SourceLocator`, `EvidenceId`
   vers une `Evidence` : plage, SHA-256).
7. **Lecteurs** : lecteurs séparés, pas de parser monolithique.

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

- `morpheus-application/src/test/java/com/morpheus/application/read/NormalizedProjectContentTest.java` : rejets
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

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — distinction courant / proposé / historique.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité de domaine stable.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité UUIDv7.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité persistante scopée par provider.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle explicite.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md) — preuve du jalon M2.
