# E01 — Provider detection

Statut : **PARTIAL_PASS**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut détecter une source OpenSpec minimale et exposer un ensemble de capacités effectives sans confondre présence du format et support de toutes les fonctionnalités.

## Question

Le mécanisme de probe peut-il distinguer :

- un projet OpenSpec supporté ;
- son schéma ;
- les capabilities effectivement couvertes par le spike ;
- une source absente ?

## Dataset

```text
experiments/m0/fixtures/openspec-basic
```

## Environnement d'exécution

```text
Python 3.13.5
Linux container
standard library only for spike runtime
```

La technologie est expérimentale et n'est pas une décision de stack de production.

## Protocole exécuté

Suite :

```text
python -m unittest -v
```

Tests E01 :

1. détection du projet OpenSpec ;
2. lecture du schéma `spec-driven` ;
3. capabilities minimales présentes ;
4. absence volontaire de `READ_ACCEPTANCE_CRITERIA` ;
5. source inexistante → diagnostic `NO_PROVIDER_FOUND`.

## Résultat observé

```text
7 tests de la suite E01/E02
7 PASS
0 FAIL
```

Sous-ensemble E01 : **PASS**.

Capabilities observées sur la fixture :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_IMPLEMENTATION_TASKS
```

## Découverte importante

Le premier oracle déclarait initialement `READ_ACCEPTANCE_CRITERIA`.

Cette déclaration a été retirée avant stabilisation du spike :

> la présence de scenarios OpenSpec ne prouve pas automatiquement que le provider sait produire la sémantique `AcceptanceCriterion` de MORPHEUS.

Cette correction confirme l'intérêt d'une négociation par **capabilities effectives** plutôt que par simple détection de format.

## Ce qui reste à exécuter avant PASS complet E01

- [ ] version OpenSpec/format explicitement supportée ;
- [ ] version non supportée ;
- [ ] plusieurs providers candidats ;
- [ ] provider explicite incompatible ;
- [ ] required capability manquante ;
- [ ] optional capability manquante ;
- [ ] provider read-only ;
- [ ] provider distant nécessitant opt-in ;
- [ ] politique de sélection déterministe.

## Mesures

Sur ce corpus minimal, l'exécution de la suite complète E01/E02 est inférieure au seuil de mesure utile à l'échelle humaine (~milliseconde dans l'environnement de contrôle).

Aucun seuil de performance n'est déduit de ce résultat : le corpus est trop petit.

## Impact ADR

### ADR-0011

**Signal positif**, mais preuve insuffisante pour acceptation.

Le concept `ProviderCapabilitySet` est utile dès le premier spike, notamment pour éviter une capability exagérée.

### ADR-0002

OpenSpec reste un candidat viable pour poursuivre l'expérimentation.

Aucune conclusion finale sur le provider de référence n'est encore possible.

## Décision provisoire

```text
CONTINUE_E01
```

Conserver l'approche capability-based et enrichir les fixtures négatives avant réévaluation des ADR-0002 et ADR-0011.
