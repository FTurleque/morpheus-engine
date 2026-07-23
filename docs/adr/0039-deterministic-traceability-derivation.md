# ADR-0039 — Dérivation déterministe de la traçabilité depuis le modèle normalisé

- Statut : **Proposée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0022, ADR-0025, ADR-0037, ADR-0038
- Portée : M4-S3, application / dérivation de relations

## Contexte

M4-S1 a stabilisé `TraceabilityLink` et la taxonomie contrôlée. M4-S2 a stabilisé la persistance snapshot-scoped Memory + SQLite.

S3 doit maintenant produire des liens à partir des relations déjà explicitement encodées dans `NormalizedProjectContent`, sans fuzzy matching et sans inventer d'identité.

Baseline :

```text
M4-S2 = 160/160 PASS
main   = e8fdb629ec592db1ea35d9c43dc704fb2fc7e5d3
```

## Décision candidate

Introduire une dérivation applicative provider-neutral :

```text
TraceabilityDerivationKey
TraceabilityLinkIdentityResolver
DeterministicTraceabilityDerivationService
```

Le service ne connaît ni OpenSpec, ni SQLite, ni MINOS, ni un format de fichier.

## Relations S3

Uniquement les relations structurelles déjà démontrables :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS via RequirementDelta
```

Les scénarios imbriqués dans un `RequirementDelta` peuvent également produire `Scenario REFINES Requirement` lorsque l'identité de requirement est explicite.

## Aucun fuzzy matching

Interdit en S3 :

```text
matching par titre
matching par statement
matching par chemin
matching par proximité textuelle
LLM
embedding
heuristique de nom
Task -> Requirement sans fait source explicite
```

L'absence de référence structurelle signifie : **aucun lien dérivé**.

## Identité explicite du lien

ADR-0037 interdit une identité cachée dérivée de `(source, relation, target)`.

S3 n'appelle donc jamais :

```text
TraceabilityLinkId.generate()
```

La matérialisation reçoit un resolver explicite :

```text
TraceabilityLinkIdentityResolver
    resolve(TraceabilityDerivationKey)
        -> Optional<TraceabilityLinkId>
```

Si aucune identité n'est fournie pour une relation dérivée, la dérivation échoue explicitement.

`TraceabilityDerivationKey` décrit le fait source sans être elle-même une `TraceabilityLinkId` :

```text
fact: TraceabilityEntityRef
source: TraceabilityEntityRef
relationType: TraceabilityRelationType
target: TraceabilityEntityRef
```

Le `fact` est une référence **typée** vers l'entité qui encode le fait :

```text
REQUIREMENT       pour Requirement -> Specification
SCENARIO          pour Scenario -> Requirement
CONSTRAINT        pour Constraint -> Change
DESIGN_DECISION   pour Change -> DesignDecision
REQUIREMENT_DELTA pour Change -> Requirement
```

La clé descriptive n'est jamais transformée en ID par hash. Elle sert uniquement à demander une identité explicite au resolver.

Deux faits explicites distincts peuvent donc produire deux observations distinctes même si leurs endpoints sont identiques.

## Origin / resolution / confidence

Les liens S3 sont déterministes :

```text
origin     = DERIVED
resolution = RESOLVED
confidence = empty
```

S3 n'utilise pas `HEURISTIC`.

## Evidence

Chaque lien conserve l'`EvidenceId` de l'entité qui encode directement le fait relationnel :

```text
Requirement.provenance      -> DERIVES_FROM
Scenario.provenance         -> REFINES
Constraint.provenance       -> CONSTRAINS
DesignDecision.provenance   -> DECIDED_BY
RequirementDelta.provenance -> AFFECTS
```

Une déduplication n'est autorisée que pour une **même `TraceabilityDerivationKey` exacte**. Les evidences de ce même fait exact sont agrégées. Aucune similarité sémantique n'est utilisée.

## Déterminisme

L'ordre d'entrée des listes de `NormalizedProjectContent` ne doit pas modifier l'ordre sémantique du résultat.

Les faits sont stockés dans un ordre canonique `(source, relation, target, fact)` avant résolution des IDs et matérialisation.

Le resolver d'identité reste une dépendance explicite : le service est déterministe pour un même contenu, le même instant d'observation et le même mapping d'identité.

Un même `TraceabilityLinkId` ne peut pas être fourni pour deux clés de dérivation différentes dans une même matérialisation.

## Temps d'observation

`observedAt` est fourni explicitement au service.

Aucun `Instant.now()` caché n'est autorisé dans le cœur de dérivation.

## Implémentation S3

```text
morpheus-application
  com.morpheus.application.traceability
    TraceabilityDerivationKey
    TraceabilityLinkIdentityResolver
    DeterministicTraceabilityDerivationService
```

Le service retourne les `TraceabilityLink` ; il n'écrit pas lui-même dans `TraceabilityStore`.

## Frontières

S3 ne fait pas :

```text
TraceabilityStore.putLink
traverse
findPath
résolution externe
LINKS_TO_CODE / LINKS_TO_TEST
fuzzy matching
invalidation incrémentale
```

La persistance reste S2 ; traversal/path reste S4 ; external/unresolved reste S5.

## Preuves ajoutées

`DeterministicTraceabilityDerivationServiceTest` ajoute **7 tests** :

1. dérivation des cinq familles structurelles + scenario de delta, evidence et sémantique ;
2. scenario sans requirement + task au titre similaire n'inventent aucun lien ;
3. identité de lien manquante = échec explicite ;
4. un même link ID ne peut représenter deux faits distincts ;
5. ordre d'entrée différent = même ordre de liens ;
6. deux deltas distincts vers la même requirement restent deux observations ;
7. même clé de fait exacte = une observation avec evidences agrégées.

Baseline avant S3 : `160/160`.

Total attendu avant gate : **167 tests**, à confirmer par le gate réel.

## Critères d'acceptation

ADR-0039 pourra passer à **Acceptée — M4** lorsque le gate local complet démontre :

1. les cinq familles de relations S3 sont dérivées uniquement depuis des références structurelles ;
2. les scenarios sans `requirementId` ne produisent aucun `REFINES` ;
3. aucun `Task -> Requirement` n'est inventé ;
4. `origin=DERIVED`, `resolution=RESOLVED`, confidence absente ;
5. l'evidence correspond au fait source ;
6. les IDs de liens sont fournis explicitement par resolver ;
7. une identité manquante échoue explicitement ;
8. aucun appel caché à `TraceabilityLinkId.generate()` dans le service ;
9. l'ordre de sortie reste déterministe malgré un ordre d'entrée différent ;
10. deux faits distincts vers la même arête ne sont pas fusionnés par similarité ;
11. une même identité de lien n'est pas réutilisable pour deux faits distincts ;
12. le domaine et l'application restent indépendants des providers/stores ;
13. `\.\mvnw.cmd clean test` est vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
