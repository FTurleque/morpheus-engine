# Conception : tester les budgets de nœuds et de liens de la traversée dans le module application (AUD-TST-32)

Conception allégée : un test ajouté dans le module qui porte la classe, sans comportement modifié.

## Contexte

[EXISTANT VÉRIFIÉ] `TraceabilityTraversalService` déclare `MAX_NODES = 1_000` et `MAX_LINKS = 5_000` (lignes 29-30,
constantes publiques : le test les lit, il ne les recopie pas). Sa traversée en largeur (`traverseExplained`) lit le
port `TraceabilityStore` par `outgoing` et `incoming` seulement (lignes 181 et 186), pose la raison
`LINK_BUDGET_REACHED:` avant d'ajouter un lien au-delà de `MAX_LINKS` (ligne 125-126), puis `NODE_BUDGET_REACHED:` avant
d'ajouter un nœud au-delà de `MAX_NODES` (lignes 130-131), et `DEPTH_BUDGET_REACHED:` à la profondeur demandée
(ligne 145).

[EXISTANT VÉRIFIÉ] La classe est construite par `ChangeAnalysisService`, `ChangeContextQueryService` et
`TraceRequirementService` ; elle est donc exécutée par leurs tests, mais aucun n'atteint les budgets de nœuds ou de
liens.

[EXISTANT VÉRIFIÉ] `TraceabilityTruncationContractTest` (module d'architecture) construit une étoile de
`MAX_NODES + 4` nœuds et un graphe de plus de `MAX_LINKS` liens sur le store mémoire ; il reste en place.

[EXISTANT VÉRIFIÉ] `morpheus-application` ne peut dépendre ni de `morpheus-store-memory` ni de
`morpheus-provider-synthetic` en test (cycle du réacteur) : ses tests implémentent à la main les ports qu'ils lisent,
chaque double réduit à ce que son test lit (`.claude/rules/testing.md`).

Motivation : voir `proposal.md`, section Why.

## Exigences couvertes

Sans objet : aucune exigence ne change (`skip_specs: true`). Le comportement testé est la troncature bornée avec
raison, règle de `.claude/rules/code-style.md` (Déterminisme) ; aucune capacité OpenSpec ne la porte aujourd'hui.

## Composants et frontières

Sans objet : aucune frontière ne bouge. Le test vit dans `com.morpheus.application.traceability`, paquet de la classe.

## Modèle de domaine et invariants

Sans objet : aucun type n'est touché.

## Traitements et interactions

Sans objet : aucun traitement ne change.

## États et transitions

Sans objet : aucun cycle de vie.

## Contrats d'interface

Sans objet : aucune surface publique.

## Persistance et intégrité

Sans objet : le test n'emploie aucune base.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Sans objet : la troncature n'est pas un échec.

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Sans objet : le graphe du test reste en mémoire et de la taille des budgets ; le coût de la traversée est tenu par
`change-analysis-traversal-cost` et par `M19TraceabilityPerformanceGate`, inchangés.

## Compatibilité, migration et retour arrière

Sans objet.

## Décisions

### D1 : un double du port, écrit à la main, réduit à `outgoing` et `incoming`
- **Nature** : [DÉCISION]
- **Choix** : une classe imbriquée du test implémente `TraceabilityStore` sur une table en mémoire ; les méthodes que
  la traversée ne lit pas lèvent `UnsupportedOperationException`. Deux graphes : une étoile de `MAX_NODES + k` voisins
  (budget de nœuds) et un graphe à peu de nœuds et plus de `MAX_LINKS` liens parallèles (budget de liens).
- **Alternatives écartées** : déplacer `TraceabilityTruncationContractTest` dans le module — il emploie le store
  mémoire, interdit en test d'application ; atteindre les budgets à travers `TraceRequirementService` — la preuve
  dépendrait d'un autre service et de ses propres bornes.
- **Compromis** : un double de plus à garder aligné sur le port ; ses méthodes inutilisées lèvent, donc une évolution
  de la traversée qui lirait autre chose échoue bruyamment.
- **ADR** : non concerné.

### D2 : le test asserte la raison exacte, le compte et l'ordre
- **Nature** : [DÉCISION]
- **Choix** : pour chaque budget, exiger `truncationReason()` égal à `"NODE_BUDGET_REACHED:" + MAX_NODES` (ou
  `LINK_BUDGET_REACHED`), le nombre de nœuds ou de liens rendus égal au budget, et deux exécutions au résultat
  identique. Un graphe sous les deux budgets ne porte aucune raison.
- **Alternatives écartées** : n'asserter que la présence d'une raison — passerait sur une raison fausse ; asserter un
  littéral `1000` — ne suivrait pas la constante.
- **Compromis** : aucun.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Les budgets de nœuds et de liens ne sont prouvés que hors du module (`AUD-TST-32`) | `TraceabilityTruncationContractTest` | `morpheus-application/src/test` | D1, D2 |
| L'audit compte 177 lignes non vides, l'arbre en compte 211 | `docs/project/audits/2026-11-17/findings.md` | `grep -c .` sur `TraceabilityTraversalService.java` | sans effet sur le constat ; relevé ici |
| La troncature bornée avec raison n'est portée par aucune capacité OpenSpec | `.claude/rules/code-style.md` | `openspec list --specs` | hors périmètre ; à proposer au mainteneur si elle doit devenir une exigence |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| troncature au budget de nœuds (règle de déterminisme) | étoile au-delà de `MAX_NODES` | test de `TraceabilityTraversalService` dans `morpheus-application` | à écrire |
| troncature au budget de liens | graphe au-delà de `MAX_LINKS` | idem | à écrire |
| aucune troncature sous les budgets | petit graphe | idem | à écrire |
| le contrat transverse reste tenu | les mêmes graphes sur le store mémoire | `TraceabilityTruncationContractTest` | existant |

## Documentation durable et diagrammes à mettre à jour

Sans objet : aucune page durable ne porte cette règle ; `docs/developer/specifications/change-analysis-traversal-cost.md`
ne cite pas les tests du module et n'a pas à changer.

## Questions ouvertes

Aucune.
