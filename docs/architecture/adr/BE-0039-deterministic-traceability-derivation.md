---
id: BE-0039
title: "Dérivation déterministe de la traçabilité depuis le modèle normalisé"
domain: backend
rules: true
files: ["morpheus-application/src/main/java/**/DeterministicTraceabilityDerivationService.java"]
---

# Dérivation déterministe de la traçabilité depuis le modèle normalisé

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

`TraceabilityLink` et sa taxonomie contrôlée (ADR-0118) sont stables, comme leur persistance scopée par snapshot
(ADR-0038). Le modèle normalisé (`NormalizedProjectContent`, ADR-0121) encode déjà explicitement certaines relations
entre entités. MORPHEUS doit produire des liens de traçabilité à partir de ces seules relations, sans
rapprochement approximatif et sans inventer d'identité.

La dérivation provider-neutral de `com.morpheus.application.traceability` se compose de :

```text
TraceabilityDerivationKey
TraceabilityLinkIdentityResolver
DeterministicTraceabilityDerivationService
```

Les relations structurelles dérivées, avec le fait source qui les encode et l'evidence conservée :

| Relation | Fait source (`fact`) | Evidence conservée |
|---|---|---|
| `Requirement DERIVES_FROM Specification` | `REQUIREMENT` | `Requirement.provenance` |
| `Scenario REFINES Requirement` | `SCENARIO` | `Scenario.provenance` |
| `Constraint CONSTRAINS Change` | `CONSTRAINT` | `Constraint.provenance` |
| `Change DECIDED_BY DesignDecision` | `DESIGN_DECISION` | `DesignDecision.provenance` |
| `Change AFFECTS Requirement` | `REQUIREMENT_DELTA` | `RequirementDelta.provenance` |
| `Requirement VERIFIED_BY AcceptanceCriterion` | `ACCEPTANCE_CRITERION` | `AcceptanceCriterion.provenance` |
| `Change VERIFIED_BY AcceptanceCriterion` | `ACCEPTANCE_CRITERION` | `AcceptanceCriterion.provenance` |
| `AcceptanceCriterion VERIFIED_BY Evidence` | `EVIDENCE` | l'evidence de vérification elle-même |

Les relations `VERIFIED_BY` suivent les critères d'acceptation et leurs evidences de vérification (ADR-0081). Une
identité cachée dérivée de `(source, relation, target)` est interdite par ADR-0118 ; le service reçoit donc un resolver
explicite :

```text
TraceabilityLinkIdentityResolver
    resolve(TraceabilityDerivationKey)
        -> Optional<TraceabilityLinkId>
```

`TraceabilityDerivationKey` décrit le fait source sans être elle-même une `TraceabilityLinkId` :

```text
fact: TraceabilityEntityRef
source: TraceabilityEntityRef
relationType: TraceabilityRelationType
target: TraceabilityEntityRef
```

Le `fact` est une référence typée vers l'entité qui encode le fait (colonne « Fait source » ci-dessus) ; la clé sert
uniquement à demander une identité au resolver. Deux faits distincts restent deux observations même si leurs
extrémités sont identiques. L'ordre des listes en entrée ne modifie pas l'ordre du résultat : le service est
déterministe pour un même contenu, un même instant d'observation et un même mapping d'identité. La traversée relève
d'ADR-0040 et la résolution externe d'ADR-0142.

## Decision

`DeterministicTraceabilityDerivationService` (`com.morpheus.application.traceability`) dérive des liens sans connaître
OpenSpec, SQLite, MINOS ni aucun format de fichier.

1. **Relations dérivées** : seulement `Requirement DERIVES_FROM Specification`, `Scenario REFINES Requirement`,
   `Constraint CONSTRAINS Change`, `Change DECIDED_BY DesignDecision`, `Change AFFECTS Requirement` et
   `VERIFIED_BY` de `Requirement` ou `Change` vers `AcceptanceCriterion` et d'`AcceptanceCriterion` vers `Evidence`.
   Le fait source est l'entité qui encode la relation, et son evidence est conservée. Un scénario, imbriqué ou non
   dans un `RequirementDelta`, ne produit `REFINES` que s'il porte l'identité de son exigence.
2. **Aucun rapprochement** : ni titre, ni énoncé, ni chemin, ni proximité textuelle, ni LLM, ni embedding, ni
   heuristique de nom, ni `Task -> Requirement` sans fait source. Sans référence structurelle, aucun lien.
3. **Identité explicite** : jamais `TraceabilityLinkId.generate()` ; chaque identité est demandée au
   `TraceabilityLinkIdentityResolver` par une `TraceabilityDerivationKey`, jamais hachée. Une identité absente, ou
   fournie pour deux clés d'une même matérialisation, fait échouer la dérivation.
4. **Déduplication** : deux faits distincts donnent deux observations ; seule une même clé exacte est dédupliquée, ses
   evidences agrégées ; aucune similarité sémantique.
5. **Qualification** : `origin = DERIVED`, `resolution = RESOLVED`, confiance absente, jamais `HEURISTIC`.
6. **Déterminisme** : faits rangés en ordre canonique avant résolution et matérialisation ; `observedAt` fourni
   explicitement, aucun `Instant.now()` caché.
7. **Périmètre** : le service retourne les liens sans écrire dans `TraceabilityStore` ; ni traversée, ni résolution
   externe, ni `LINKS_TO_CODE` / `LINKS_TO_TEST`, ni invalidation incrémentale.

## Do's and Don'ts

### Do

- Dériver un lien uniquement d'une référence structurelle présente dans `NormalizedProjectContent`.
- Demander chaque identité de lien au `TraceabilityLinkIdentityResolver` et échouer si elle manque.
- Conserver comme evidence celle de l'entité qui encode directement le fait.
- Recevoir `observedAt` en paramètre.
- Ranger les faits dans un ordre canonique avant la matérialisation.

### Don't

- N'appeler ni `TraceabilityLinkId.generate()` ni `Instant.now()` dans le service de dérivation.
- Ne jamais hacher une clé de dérivation pour en faire une identité.
- Ne jamais rapprocher deux entités par titre, énoncé, chemin, proximité textuelle, LLM ou embedding.
- Ne pas inventer de lien `Task -> Requirement` sans fait source explicite.
- Ne pas réutiliser une identité de lien pour deux faits distincts.
- Ne pas écrire dans `TraceabilityStore` depuis le service de dérivation.

## Consequences

### Positives

- Les liens dérivés sont reproductibles, explicables et rattachés à leur fait source.
- L'identité des liens reste sous le contrôle explicite de l'appelant.
- Le service est indépendant des providers et des stores.

### Négatives

- Une relation non encodée structurellement par la source ne produit aucun lien.
- L'appelant doit fournir un mapping d'identité complet pour chaque relation dérivée.

### Risques

- Aucun risque propre n'est consigné par cette décision au-delà des conséquences négatives.

## Compliance and Enforcement

- Règle Archgate `BE-0039-deterministic-traceability-derivation.rules.ts` : `derivation-is-deterministic`.
- `morpheus-application/src/test/java/com/morpheus/application/traceability/DeterministicTraceabilityDerivationServiceTest.java` :
  familles structurelles et evidence, absence de lien inventé, identité manquante en échec, identité réutilisée
  refusée, ordre d'entrée sans effet, faits distincts conservés, evidences agrégées pour une clé exacte.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`applicationMustNotDependOnAdapters`) : l'application ne dépend d'aucun provider ni store.
- Revue : absence de `TraceabilityLinkId.generate()` et d'`Instant.now()` dans le service (aucune règle ne la tient).

## References

- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance de la traçabilité par snapshot.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée.
- [ADR-0142](DATA-0142-references-externes.md) — références externes et liens non résolus.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation et evidence.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé.
- [VALIDATION_M4](../../project/validation/VALIDATION_M4.md)
