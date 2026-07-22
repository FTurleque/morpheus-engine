# E02 — Domain mapping

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

Une source OpenSpec peut être transformée en représentation MORPHEUS normalisée en conservant la sémantique utile, la provenance et les états temporels, sans exposer les structures propres au provider dans le contrat normalisé.

La même forme de contrat peut être produite par un second provider synthétique.

## Datasets

```text
experiments/m0/fixtures/openspec-basic
experiments/m0/fixtures/openspec-partial
experiments/m0/fixtures/synthetic-basic
```

Oracle principal :

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

```text
python -m unittest -v
```

Résultat global E01/E02 :

```text
Ran 15 tests
15 PASS
0 FAIL
```

Sous-ensemble E02 : **6 tests PASS**.

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
Requirements: 3
Constraints: 2
Tasks: 8
Design decisions: 2
```

Deltas :

```text
MODIFIED auth-session/session-expiration
ADDED    auth-session/explicit-remember-me-opt-in
ADDED    auth-session/persistent-credential-revocation
```

### Invariant CURRENT / PROPOSED

La clé :

```text
auth-session/session-expiration
```

existe simultanément comme :

```text
CURRENT baseline
PROPOSED MODIFIED delta
```

Le delta ne remplace donc pas silencieusement la baseline pendant l'ingestion.

### Proposal

Le spike normalise séparément :

```text
intent
scope[]
out_of_scope[]
risks[]
provenance
```

### Constraints

La fixture contient deux contraintes explicites dans le proposal. Elles sont normalisées comme objets distincts avec provenance.

### Design decisions

Les décisions ne sont plus simplement comptées : elles sont extraites individuellement avec :

```text
title
statement
provenance
```

### Tasks

Les 8 tâches sont normalisées individuellement avec :

```text
label
completed
provenance
```

### Scenario vs AcceptanceCriterion

Règle retenue pour E02 :

> Un `Scenario` OpenSpec reste un `Scenario` MORPHEUS tant qu'aucune règle explicite ne démontre qu'il constitue un `AcceptanceCriterion`.

Le provider ne revendique donc pas `READ_ACCEPTANCE_CRITERIA`.

### Historique

Une fixture archivée est exposée séparément :

```text
legacy-session-warning
TemporalState: HISTORICAL
```

Elle n'apparaît ni dans `CURRENT`, ni dans les changements `PROPOSED`.

### Provenance

Les requirements et scenarios extraits possèdent au minimum :

```text
source path
line number
```

Les proposal, contraintes, décisions et tâches possèdent également un locator source minimal.

### Source partiellement lisible

Une fixture contenant une exigence sans scenario conserve les éléments lisibles et produit :

```text
PARTIAL_INGESTION
```

Le provider ne transforme donc pas une lecture partielle en succès silencieux et ne jette pas les éléments valides.

### Second provider synthétique

Le provider `synthetic-json` produit la même enveloppe normalisée :

```text
probe
current
proposed
historical
diagnostics
```

avec les mêmes concepts temporels `CURRENT` et `PROPOSED`.

Le code de consommation du test n'a pas besoin de connaître le format OpenSpec pour lire les résultats du provider synthétique.

## Ce qui est démontré

- [x] lecture de spécifications courantes ;
- [x] extraction de requirements ;
- [x] extraction de scenarios ;
- [x] lecture d'un changement actif ;
- [x] reconnaissance `ADDED` / `MODIFIED` ;
- [x] proposal normalisée ;
- [x] contraintes normalisées ;
- [x] design decisions normalisées individuellement ;
- [x] tasks normalisées individuellement ;
- [x] provenance ;
- [x] séparation `CURRENT` / `PROPOSED` ;
- [x] représentation `HISTORICAL` séparée ;
- [x] diagnostic de lecture partielle ;
- [x] second provider synthétique ;
- [x] même forme de contrat normalisé pour deux providers ;
- [x] aucun LLM ;
- [x] aucun réseau ;
- [x] aucun type OpenSpec requis dans les structures normalisées publiques du spike.

## Périmètre volontairement transféré aux expériences dédiées

Les sujets suivants ne bloquent plus E02 car ils disposent de leur propre expérience :

```text
relations de traçabilité -> E06
DomainIdentity           -> E03
KnowledgeSnapshot        -> E05
suppression/incrémental  -> E11
```

Ils ne doivent pas être implémentés artificiellement dans le parser E02 uniquement pour gonfler son périmètre.

## Impact ADR

### ADR-0001 — Domaine indépendant

**Preuve positive forte.**

Deux providers distincts produisent une enveloppe de domaine commune sans imposer leur format au consommateur.

ADR-0001 reste `Proposée` jusqu'aux preuves d'architecture et de store prévues par ses critères d'acceptation.

### ADR-0002 — OpenSpec provider de référence

**Preuve positive forte sur le schéma `spec-driven`.**

La séparation native entre specs courantes et changes facilite fortement la normalisation `CURRENT` / `PROPOSED`.

### ADR-0006 — Current / Proposed / Historical

**Les trois états temporels sont maintenant exercés par E02.**

L'ADR reste néanmoins `Proposée` jusqu'à E04 qui doit tester plusieurs changements concurrents et les règles de promotion.

### ADR-0008 — Read-first

E02 fonctionne entièrement en lecture seule et ne nécessite aucune mutation de la source.

## Décision

```text
E02 = PASS
CONTINUE_NORMALIZED_DOMAIN
```

Le mapping provider → domaine normalisé est suffisamment viable pour passer à E03 sans élargir davantage ce spike.
