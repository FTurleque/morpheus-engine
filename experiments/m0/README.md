# Laboratoire M0 — MORPHEUS

Ce répertoire contient les **preuves expérimentales** de M0.

Il ne constitue pas encore la fondation de production de MORPHEUS.

Conformément à ADR-0014 :

> une technologie utilisée dans un spike est expérimentale tant qu'une décision explicite ne l'a pas adoptée comme fondation de production.

## Structure

```text
experiments/m0/
├── fixtures/     jeux de données reproductibles
├── results/      protocoles, observations et résultats
└── README.md
```

## Première séquence

La première séquence couvre :

```text
E01 — Provider detection
E02 — Domain mapping
```

Objectifs :

1. disposer d'un projet OpenSpec représentatif et minimal ;
2. distinguer les specs courantes des changements proposés ;
3. disposer de requirements et scenarios structurés ;
4. disposer de proposal / design / tasks ;
5. disposer d'un delta de spécification ;
6. définir les attentes MORPHEUS avant d'écrire un provider ;
7. éviter de coder le comportement attendu après coup en fonction de ce que retourne le parser.

## Règle de résultats

Chaque expérience doit produire un document contenant :

```text
Hypothesis
Question
Dataset
Environment
Technology used
Measurement protocol
Expected result
Observed result
Measurements
Diagnostics
Limitations
ADR impact
Decision proposal
```

Un résultat non encore exécuté doit rester explicitement marqué `NOT_RUN`.

## Sources du format OpenSpec

Le corpus suit la structure OpenSpec actuelle observée au démarrage de M0 :

- configuration projet dans `openspec/config.yaml` ;
- schéma par défaut `spec-driven` ;
- specs courantes sous `openspec/specs/` ;
- changements sous `openspec/changes/<change>/` ;
- artifacts `proposal.md`, `specs/`, `design.md`, `tasks.md` ;
- métadonnées de changement optionnelles dans `.openspec.yaml` ;
- requirements et scenarios structurés dans les specs.

La fixture est volontairement minimale : elle sert à tester MORPHEUS, pas à reproduire tous les cas possibles d'OpenSpec.
