# Spike E01/E02 — Python jetable

Statut : **EXPÉRIMENTAL / DISPOSABLE**

Ce spike sert uniquement à produire une première preuve sur :

```text
E01 — Provider detection
E02 — Domain mapping
```

Il **ne choisit pas Python comme stack de production de MORPHEUS**.

## Pourquoi Python pour ce spike

Objectif recherché : obtenir rapidement une preuve exécutable avec :

- très peu de code ;
- aucun framework ;
- aucune configuration à la racine du dépôt ;
- bibliothèque standard suffisante pour le périmètre ;
- tests `unittest` intégrés ;
- code facilement jetable.

ADR-0014 s'applique intégralement.

## Périmètre implémenté

### Discovery

Détection minimale :

```text
<project>/openspec/config.yaml
schema: spec-driven
```

### Capabilities annoncées lorsque les artefacts sont présents

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_IMPLEMENTATION_TASKS
```

`READ_ACCEPTANCE_CRITERIA` n'est volontairement **pas** annoncée : les scenarios OpenSpec sont normalisés comme `Scenario` dans ce spike et ne deviennent pas automatiquement des `AcceptanceCriterion` MORPHEUS.

### Mapping expérimental

Le spike extrait :

- specs courantes ;
- requirements ;
- scenarios ;
- changements actifs ;
- delta `ADDED` / `MODIFIED` / `REMOVED` ;
- nombre de tâches ;
- décisions explicites sous `## Decisions` ;
- provenance fichier + ligne ;
- état temporel `CURRENT` / `PROPOSED`.

## Non couvert

Ce spike ne doit pas être interprété comme un provider complet.

Il ne couvre pas encore :

- validation exhaustive du YAML ;
- version réelle du package OpenSpec ;
- archives ;
- contraintes ;
- critères d'acceptation MORPHEUS ;
- identité stable cross-version ;
- snapshots ;
- relations de traçabilité persistées ;
- multi-provider ;
- provider distant ;
- version de format non supportée ;
- écriture ;
- watcher ;
- backend persistant.

## Exécution

Depuis ce répertoire :

```text
python -m unittest -v
python spike.py ../../fixtures/openspec-basic
```

## Premier résultat

Exécution locale de contrôle :

```text
Python 3.13.5
7 tests
7 PASS
```

Les résultats détaillés sont consignés dans :

- `../../results/E01-provider-detection.md` ;
- `../../results/E02-domain-mapping.md`.
