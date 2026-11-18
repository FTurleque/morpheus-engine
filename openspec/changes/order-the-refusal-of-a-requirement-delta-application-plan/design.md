# Conception : ordonner le refus d'un plan d'application de delta (AUD-QUA-38)

Conception allégée : changement local à une méthode, sans comportement spécifié (`skip_specs: true`).

## Contexte

- [EXISTANT VÉRIFIÉ] `RequirementDeltaApplicationService.validateEntityVersionIds` (lignes 232-256) :
  `new HashSet<>(candidateIdentities)` puis `removeAll` pour `missing` et `extra`, concaténés au message de
  `RequirementDeltaApplicationException` ; `new HashSet<>(plan.entityVersionIds().values())` puis
  `ids.stream().filter(activeIds::contains).findFirst()` pour l'identifiant réutilisé ; retour `new HashMap<>(…)`.
- [EXISTANT VÉRIFIÉ] `RequirementDeltaApplicationPlan` copie ses identifiants par `Map.copyOf` (ligne 32) : ordre
  d'itération non spécifié.
- [EXISTANT VÉRIFIÉ] `DomainIdentity` et `EntityVersionId` implémentent `Comparable`.
- [EXISTANT VÉRIFIÉ] `RequirementDeltaApplicationServiceTest` (lignes 237 et 240) n'attend qu'un préfixe de message et un
  seul identifiant manquant : il ne voit pas l'ordre.
- [EXISTANT VÉRIFIÉ] La classe n'a aucun appelant de production (AUD-QUA-27).

## Exigences couvertes

Sans objet : aucune exigence de capacité (`skip_specs: true`). La règle servie est celle de
`.claude/rules/code-style.md`, « Déterminisme ».

## Composants et frontières

Sans objet : une méthode privée de `com.morpheus.application.delta`.

## Modèle de domaine et invariants

Sans objet.

## Traitements et interactions

Sans objet.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet : aucune surface.

## Persistance et intégrité

Sans objet : le refus a lieu avant toute écriture (BE-0035, point 4).

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

L'exception et ses conditions restent les mêmes ; seul l'ordre du texte devient fixe.

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Sans objet : collections de la taille d'un plan.

## Compatibilité, migration et retour arrière

Sans objet.

## Décisions

### D1 : trier par l'ordre naturel des identifiants
- **Nature** : [PROPOSITION]
- **Exigences servies** : règle « Ne jamais laisser un HashMap/HashSet déterminer un ordre observable ».
- **Choix** : `missing` et `extra` en `TreeSet` ; identifiant réutilisé choisi par `sorted().filter(…).findFirst()` (le plus
  petit) ; le `HashMap` retourné reste interne (aucun ordre observable n'en sort, à vérifier à l'application, sinon
  `TreeMap`).
- **Alternatives écartées** : itérer dans l'ordre du plan (suggestion de l'audit) — le plan n'a pas d'ordre
  (`Map.copyOf`), il faudrait changer son type ; signaler tous les identifiants réutilisés — change le message plus que
  nécessaire.
- **ADR** : non concerné.

### D2 : abandon si la classe est supprimée
- **Nature** : [DÉCISION] (arête `depends_on` de l'audit)
- **Choix** : si AUD-QUA-27 supprime la classe, ce change n'est pas appliqué ; le mainteneur le retire de
  `openspec/changes/` dans la PR de la suppression.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit propose d'itérer dans l'ordre du plan | constat `AUD-QUA-38` | `Map.copyOf` sans ordre | D1 trie par l'ordre naturel |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| (règle de déterminisme) | plan à plusieurs identités manquantes et en trop | `RequirementDeltaApplicationServiceTest#aMismatchedPlanListsMissingAndExtraIdentitiesInOrder` : message égal, mot pour mot, à la liste triée | à écrire |
| idem | plusieurs identifiants réutilisés | `#theReusedOccurrenceReportedIsTheSmallest` | à écrire |

## Documentation durable et diagrammes à mettre à jour

Sans objet : aucune page de `docs/developer/specifications/` ne décrit ce service.

## Questions ouvertes

Aucune autre que le sort de la classe (AUD-QUA-27).
