# Proposition : ordonner le refus d'un plan d'application de delta (AUD-QUA-38)

## Why

Quand un plan d'application de delta ne correspond pas aux identités qu'il doit produire,
`RequirementDeltaApplicationService.validateEntityVersionIds` écrit dans son refus deux `HashSet` (`missing=…, extra=…`,
lignes 237 à 242), et signale l'identifiant d'occurrence réutilisé choisi par `findFirst()` sur un `HashSet`
(lignes 245 à 253). Deux exécutions sur la même entrée peuvent donc rendre deux messages différents, contre la règle
« Ne jamais laisser un HashMap/HashSet déterminer un ordre observable » (`.claude/rules/code-style.md`).

Preuve : constat `AUD-QUA-38` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`. Revérifié le 17/11/2026 sur la tête de la branche : code présent tel que cité. Le plan lui-même copie ses
identifiants par `Map.copyOf` (`RequirementDeltaApplicationPlan`, ligne 32), dont l'ordre d'itération n'est pas
spécifié : « itérer dans l'ordre du plan », seconde option de l'audit, n'est pas possible sans changer le plan.

## What Changes

- Le refus écrit `missing` et `extra` triés selon l'ordre naturel de `DomainIdentity`.
- L'identifiant réutilisé signalé est le plus petit, selon l'ordre naturel de `EntityVersionId`, parmi ceux qui
  réutilisent une occurrence `ACTIVE`.
- Sans objet si `give-every-public-type-of-the-product-a-production-caller` (AUD-QUA-27) supprime
  `RequirementDeltaApplicationService` : ce change est alors abandonné sans être appliqué.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : la classe n'a aucun appelant de production (AUD-QUA-27), donc aucun comportement livré ne change. Le change pose
`skip_specs: true`.

## Périmètre et exclusions

**Inclus :** `validateEntityVersionIds` de `RequirementDeltaApplicationService` (`com.morpheus.application.delta`) et
son test.

**Exclus :** la décision de garder ou supprimer la classe (AUD-QUA-27, change
`give-every-public-type-of-the-product-a-production-caller`, qui **précède** celui-ci).

## Acteurs et surfaces concernés

Aucun acteur externe : la classe n'est servie par aucune surface.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le refus écrit deux `HashSet` et choisit l'identifiant par `findFirst()` sur un `HashSet` | existant vérifié | `RequirementDeltaApplicationService.java:236-254` |
| Le plan n'a pas d'ordre d'itération spécifié | existant vérifié | `RequirementDeltaApplicationPlan.java:32`, `Map.copyOf` |
| `DomainIdentity` et `EntityVersionId` sont `Comparable` | existant vérifié | déclarations des deux records |
| Ce change dépend de la décision d'AUD-QUA-27 | décision | arête `depends_on` de l'audit |

## Impact

`morpheus-application` (`com.morpheus.application.delta`) et `RequirementDeltaApplicationServiceTest`. Aucun ADR, aucune
surface, aucun test d'architecture.

## Questions ouvertes

- Sort de `RequirementDeltaApplicationService`, tranché par le mainteneur dans AUD-QUA-27 ; ce change n'a d'objet que si
  la classe est gardée.
