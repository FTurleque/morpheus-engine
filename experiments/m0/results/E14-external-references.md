# E14 — External references

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Prouver qu'un lien MORPHEUS vers un système externe peut exister, être persisté conceptuellement et évoluer sans rendre ce système obligatoire au fonctionnement du moteur.

## Spike

```text
experiments/m0/spikes/e14_external_references_python/
├── external_refs.py
└── test_external_refs.py
```

## Résultat

```text
Ran 6 tests
6 PASS
0 FAIL
```

## Modèle exercé

```text
ExternalReference
├── system
├── project
├── resource_type
├── external_id
├── revision?
├── state
├── provenance?
├── resolved_payload?
└── history[]
```

États exercés :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

## Scénarios validés

### Référence sans système cible

Une référence MINOS peut être créée et conservée alors qu'aucun resolver MINOS n'est branché.

Elle existe d'abord en :

```text
UNVALIDATED
```

puis une tentative de résolution sans resolver produit :

```text
UNRESOLVED
reason = NO_RESOLVER
```

MORPHEUS reste fonctionnel.

### Résolution optionnelle

Un `FakeMinosResolver` branché dans un `ResolverRegistry` peut résoudre :

```text
system = MINOS
resource_type = SYMBOL
external_id = symbol:RequirementService
```

sans que le domaine importe un type MINOS.

### Cible supprimée

Une référence précédemment `RESOLVED` dont la cible disparaît devient :

```text
STALE
```

La référence n'est pas supprimée et son historique reste explicable.

### Résolution différée

Une référence `UNRESOLVED` peut devenir `RESOLVED` ultérieurement lorsque le système cible redevient disponible ou que la ressource apparaît.

### Historique et provenance

Les transitions de résolution conservent :

- la provenance MORPHEUS du lien ;
- l'état précédent ;
- l'état suivant ;
- la raison de la transition.

## Frontière cross-engine confirmée

Le modèle démontré est :

```text
MORPHEUS domain
      │
      ▼
ExternalReference
      │
      ▼ optional
ResolverRegistry
      │
      └── MINOS resolver / autre resolver
```

et non :

```text
MORPHEUS domain -> dépendance directe vers classes MINOS
```

## Impact ADR-0007

**Preuve positive forte.**

E14 démontre une résolution externe optionnelle et une dégradation explicite lorsque le système cible est indisponible.

ADR-0007 peut être réévaluée vers `Acceptée` lors de la revue finale M0, sous réserve que le contrat d'intégration reste abstrait de la stack retenue.

## Décision

```text
E14 = PASS
CROSS_ENGINE_EXTERNAL_REFERENCE = RETAIN
DIRECT_DOMAIN_DEPENDENCY_ON_MINOS = REJECT
```
