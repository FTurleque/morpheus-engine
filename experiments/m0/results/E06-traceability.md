# E06 — Traceability

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut représenter une traçabilité typée, directionnelle, explicable et traversable sans autoriser des relations libres non gouvernées.

## Spike

```text
experiments/m0/spikes/e06_traceability_python/
├── traceability.py
└── test_traceability.py
```

## Protocole

```text
python -m unittest -v
```

Résultat :

```text
Ran 8 tests
8 PASS
0 FAIL
```

## Relations exercées

```text
Scenario REFINES Requirement
AcceptanceCriterion VALIDATES Requirement
ImplementationTask IMPLEMENTS Requirement
ChangeProposal AFFECTS Requirement
ChangeProposal DECIDED_BY DesignDecision
Constraint CONSTRAINS ChangeProposal
DesignDecision DEPENDS_ON Constraint
Requirement LINKS_TO_CODE ExternalReference
Requirement RELATED_TO Requirement
```

## Capacités validées

### Sens direct

`outgoing(source)` retourne les liens canoniques dans leur direction de stockage.

### Sens inverse

`incoming(target)` permet l'interrogation inverse sans persister artificiellement une deuxième arête.

Une relation inverse de requête n'est donc pas une seconde preuve.

### Traversée profondeur 3

Le spike traverse plusieurs relations jusqu'à profondeur 3 et conserve le chemin observé.

La profondeur est explicite ; aucune transitivité illimitée n'est déduite du simple fait que les données forment un graphe.

### Lien non résolu

Un lien vers une cible absente reste conservé :

```text
relation = LINKS_TO_CODE
resolution = UNRESOLVED
```

La cible manquante n'est pas supprimée silencieusement.

### Type / origine / résolution séparés

Exemple exercé :

```text
type = RELATED_TO
origin = HEURISTIC
resolution = HEURISTIC
confidence = 0.62
evidence = heuristic:v1
```

La confiance n'est donc pas encodée dans le type de relation.

### Taxonomie contrôlée

Une relation inconnue comme :

```text
WHATEVER
```

est rejetée explicitement.

Les providers ne peuvent pas injecter une chaîne arbitraire dans le cœur de la taxonomie.

### Déduplication

Deux observations strictement identiques du même lien ne créent pas deux arêtes physiques dans ce spike.

## Ce que E06 démontre

- [x] relations typées ;
- [x] direction canonique ;
- [x] requête inverse ;
- [x] traversée profondeur 3 ;
- [x] cible non résolue conservée ;
- [x] provenance/evidence ;
- [x] origine explicite / dérivée / heuristique ;
- [x] résolution orthogonale au type ;
- [x] confidence optionnelle ;
- [x] rejet des relations libres ;
- [x] déduplication d'une arête identique.

## Impact ADR-0005

**Preuve positive forte.**

`TraceabilityLink` apporte directement :

- explicabilité ;
- requêtes inverses ;
- conservation des références cassées ;
- chemins multi-niveaux ;
- séparation fait / dérivation / heuristique.

ADR-0005 reste `Proposée` jusqu'à intégration avec les stores M0.

## Impact ADR-0010

**Preuve positive forte pour une taxonomie contrôlée.**

La taxonomie candidate couvre les parcours exigés par E06 sans recourir à des relations libres.

Les points restant à trancher restent notamment :

- distinction finale `IMPLEMENTS` / `SATISFIES` ;
- politiques exactes de transitivité par relation ;
- cardinalités devant devenir invariant ou diagnostic ;
- persistance des liens dans le backend candidat.

## Impact E09

E06 démontre que les traversées peuvent être implémentées sans graph database dédiée sur le corpus fonctionnel minimal.

Cela **ne suffit pas encore** à conclure qu'un graph store est inutile à grande échelle. E09 devra être déclenchée seulement si E08 ou un benchmark de volume révèle une limite mesurable.

## Décision

```text
E06 = PASS
TRACEABILITY_FIRST_CLASS = RETAIN
CONTROLLED_TAXONOMY = RETAIN
FREE_FORM_RELATION_TYPES = REJECT
```
