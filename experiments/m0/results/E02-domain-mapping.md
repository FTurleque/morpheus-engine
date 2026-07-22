# E02 — Domain mapping

Statut : **PARTIAL_PASS**

Date : 22 juillet 2026

## Hypothèse

Une source OpenSpec peut être transformée en représentation MORPHEUS minimale en conservant :

- la séparation `CURRENT` / `PROPOSED` ;
- requirements ;
- scenarios ;
- provenance ;
- artifacts de changement ;

sans exposer un type OpenSpec dans le résultat normalisé expérimental.

## Question

Le spike peut-il lire le corpus de référence sans appliquer le delta proposé à la baseline courante ?

## Dataset

```text
experiments/m0/fixtures/openspec-basic
```

Oracle :

```text
experiments/m0/fixtures/openspec-basic/expected-morpheus.yaml
```

## Environnement

```text
Python 3.13.5
Linux container
standard library only for spike runtime
```

La technologie est jetable et ne constitue pas une décision de production.

## Protocole exécuté

Suite :

```text
python -m unittest -v
```

Résultat :

```text
Ran 7 tests
OK
```

## Résultats observés

### Baseline courante

```text
Specification count: 1
Requirement count: 2
TemporalState: CURRENT
```

Requirements :

```text
auth-session/session-expiration
auth-session/session-activity-refresh
```

### Changement proposé

```text
Change: add-remember-me
TemporalState: PROPOSED
Tasks: 8
Explicit design decisions: 2
Delta requirements: 3
```

Deltas :

```text
MODIFIED auth-session/session-expiration
ADDED    auth-session/explicit-remember-me-opt-in
ADDED    auth-session/persistent-credential-revocation
```

### Invariant critique vérifié

La clé :

```text
auth-session/session-expiration
```

existe simultanément dans :

```text
CURRENT baseline
PROPOSED MODIFIED delta
```

avec deux contenus distincts.

Le spike ne remplace donc pas la baseline courante par le delta pendant l'ingestion.

### Provenance

Chaque requirement extrait contient :

```text
source path
line number
```

Chaque scenario possède également un locator source minimal.

## Ce qui est démontré

- [x] lecture de spec courante ;
- [x] extraction de requirements ;
- [x] extraction de scenarios ;
- [x] lecture d'un changement actif ;
- [x] reconnaissance `ADDED` / `MODIFIED` ;
- [x] comptage des tâches ;
- [x] extraction minimale de décisions explicites ;
- [x] provenance ;
- [x] séparation `CURRENT` / `PROPOSED` ;
- [x] aucun LLM ;
- [x] aucun réseau ;
- [x] aucun type OpenSpec requis dans les structures normalisées du spike.

## Ce qui reste à démontrer avant PASS complet E02

- [ ] mapping des contraintes ;
- [ ] règle explicite `Scenario` vs `AcceptanceCriterion` ;
- [ ] proposal normalisée comme intention/périmètre ;
- [ ] design plus riche que le simple comptage ;
- [ ] tasks normalisées individuellement ;
- [ ] relations `AFFECTS`, `VALIDATES`, `IMPLEMENTS`, etc. ;
- [ ] éléments supprimés ;
- [ ] archives ;
- [ ] structure invalide ;
- [ ] source partiellement lisible ;
- [ ] second provider synthétique ;
- [ ] invariants de `DomainIdentity` ;
- [ ] `KnowledgeSnapshot`.

## Impact ADR

### ADR-0001 — Domaine indépendant

**Signal positif.**

Le spike montre qu'une normalisation simple peut déjà séparer le modèle produit de l'arborescence externe, mais le second provider synthétique reste obligatoire avant acceptation.

### ADR-0002 — OpenSpec provider de référence

**Signal positif.**

La séparation native specs courantes / changes facilite fortement le test de `CURRENT` / `PROPOSED`.

### ADR-0006 — Current / Proposed / Historical

**Signal positif fort** pour `CURRENT` / `PROPOSED`.

`HISTORICAL` n'est pas encore testé ; l'ADR reste donc proposée.

### ADR-0008 — Read-first

Le spike fonctionne entièrement en lecture seule, ce qui renforce l'hypothèse. Les autres fonctionnalités MVP doivent encore confirmer qu'aucune mutation n'est nécessaire.

## Décision provisoire

```text
CONTINUE_E02
```

Le mapping minimal est viable. La prochaine extension doit privilégier les cas négatifs, les archives, le second provider synthétique et la normalisation des relations avant d'élargir le parser.
