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

## Decision

L'application porte une dérivation provider-neutral, dans `com.morpheus.application.traceability` :

```text
TraceabilityDerivationKey
TraceabilityLinkIdentityResolver
DeterministicTraceabilityDerivationService
```

Le service ne connaît ni OpenSpec, ni SQLite, ni MINOS, ni un format de fichier.

**Relations dérivées.** Uniquement les relations structurelles explicitement encodées dans le contenu normalisé :

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

Un scénario ne produit `REFINES` que s'il porte explicitement l'identité de son exigence ; les scénarios imbriqués
dans un `RequirementDelta` suivent la même règle. Les relations `VERIFIED_BY` suivent les critères d'acceptation et
leurs evidences de vérification (ADR-0081).

**Aucun rapprochement approximatif.** Sont interdits : matching par titre, par énoncé, par chemin, par proximité
textuelle, LLM, embedding, heuristique de nom, et tout lien `Task -> Requirement` sans fait source explicite.
L'absence de référence structurelle signifie **aucun lien dérivé**.

**Identité explicite du lien.** Une identité cachée dérivée de `(source, relation, target)` est interdite
(ADR-0118). Le service n'appelle jamais `TraceabilityLinkId.generate()` ; il reçoit un resolver explicite :

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

Le `fact` est une référence typée vers l'entité qui encode le fait (colonne « Fait source » ci-dessus). La clé n'est
jamais transformée en identifiant par hachage : elle sert uniquement à demander une identité au resolver.

- Si aucune identité n'est fournie pour une relation dérivée, la dérivation échoue explicitement.
- Une même `TraceabilityLinkId` ne peut pas être fournie pour deux clés de dérivation différentes dans une même
  matérialisation : la dérivation échoue explicitement.
- Deux faits explicites distincts produisent deux observations distinctes, même si leurs extrémités sont identiques.
- Une déduplication n'a lieu que pour une **même clé de dérivation exacte** ; les evidences de ce fait exact sont
  agrégées. Aucune similarité sémantique n'est utilisée.

**Origine, résolution, confiance.** Les liens dérivés sont `origin = DERIVED`, `resolution = RESOLVED`, confiance
absente. La dérivation n'utilise jamais `HEURISTIC`.

**Déterminisme.** L'ordre des listes en entrée ne modifie pas l'ordre du résultat : les faits sont rangés dans un
ordre canonique avant la résolution des identités et la matérialisation. Le service est déterministe pour un même
contenu, un même instant d'observation et un même mapping d'identité. `observedAt` est fourni explicitement ; aucun
`Instant.now()` caché n'est autorisé dans le cœur de dérivation.

**Périmètre.** Le service retourne les `TraceabilityLink` ; il n'écrit pas lui-même dans `TraceabilityStore`. Il ne
fait ni traversée (ADR-0040), ni résolution externe (ADR-0041), ni liens vers le code ou les tests
(`LINKS_TO_CODE`, `LINKS_TO_TEST`), ni invalidation incrémentale.

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
- [ADR-0041](DATA-0041-snapshot-external-reference-traceability.md) — références externes et liens non résolus.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation et evidence.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé.
- [VALIDATION_M4](../../project/validation/VALIDATION_M4.md)
