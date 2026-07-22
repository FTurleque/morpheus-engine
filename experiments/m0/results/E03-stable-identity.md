# E03 — Stable identity

Statut : **PASS (règles de continuité) — choix du format d'ID produit encore ouvert**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut séparer l'identité logique d'un élément de :

- son titre ;
- son emplacement source ;
- son contenu courant ;
- son identifiant externe ;
- son état temporel ;

et conserver la même identité lorsque la continuité est démontrable, sans fusionner silencieusement les cas ambigus.

## Dataset / oracle

```text
experiments/m0/fixtures/identity-scenarios.json
```

## Spike

```text
experiments/m0/spikes/e03_identity_python/
├── identity.py
└── test_identity.py
```

Le format d'identifiant utilisé par le spike :

```text
exp-000001
exp-000002
...
```

est **strictement expérimental**. Il sert à prouver l'opacité de `DomainIdentity` et ne constitue pas un choix de format de production.

## Environnement

```text
Python 3.13.5
Linux container
standard library only
```

ADR-0014 s'applique : cette technologie et ce format d'ID sont jetables par défaut.

## Protocole exécuté

```text
python -m unittest -v
```

Résultat :

```text
Ran 10 tests
10 PASS
0 FAIL
```

## Scénarios validés

### Déplacement de fichier

```text
same external id
new SourceLocator
=> same DomainIdentity
```

Résolution :

```text
RESOLVED / EXTERNAL_ID
```

### Renommage du titre

Lorsque la logical key reste stable :

```text
new title
same logical key
=> same DomainIdentity
```

Le titre n'est donc pas l'identité.

### Changement d'external ID

Un nouvel external ID ne préserve l'identité que si une continuité explicite est fournie :

```text
previous_external_id = REQ-1
external_id = REQ-100
=> same DomainIdentity
```

Résolution :

```text
RESOLVED / EXPLICIT_CONTINUITY
```

### Modification du texte

Avec external ID stable :

```text
new statement
same external id
=> same DomainIdentity
```

Le contenu est versionné ; il n'est pas l'identité.

### Contenu dupliqué

Deux exigences au contenu identique mais sans preuve de continuité restent distinctes :

```text
DomainIdentity A != DomainIdentity B
resolution = HEURISTIC
warning = HEURISTIC_CONTINUITY_CANDIDATE
```

La similarité de contenu peut aider au diagnostic mais **ne provoque jamais une fusion automatique**.

### Suppression puis restauration

Après suppression logique, une réapparition portant le même external ID retrouve la même identité :

```text
delete -> inactive
restore with same external id -> same DomainIdentity, active
```

### Archivage

L'archivage modifie l'état temporel, pas l'identité :

```text
same DomainIdentity
TemporalState = HISTORICAL
active = false
```

### Collision entre providers

Le même external ID dans deux providers différents produit deux identités distinctes :

```text
openspec:REQ-1 != synthetic:REQ-1
```

L'external ID est donc résolu dans un namespace provider + type d'entité.

### Identifiants contradictoires

Le premier prototype avait un défaut important :

```text
new external id
+
logical key déjà possédée
```

était fusionné vers l'identité existante.

Le test a échoué sur ce scénario.

Le resolver a été corrigé pour produire :

```text
new DomainIdentity
resolution = PARTIALLY_RESOLVED
reason = CONFLICTING_IDENTIFIERS
warning = IDENTITY_COLLISION
```

Ce comportement respecte l'invariant :

> **une ambiguïté d'identité ne doit jamais être résolue par fusion silencieuse.**

## Ordre de résolution expérimenté

```text
1. external id stable
2. continuité externe explicite
3. logical key stable
4. similarité de contenu = signal heuristique seulement
5. sinon nouvelle identité
```

Toute collision détectée reste observable.

## Ce que E03 démontre

- [x] déplacement sans changement d'identité lorsqu'une preuve stable existe ;
- [x] titre distinct de l'identité ;
- [x] contenu distinct de l'identité ;
- [x] external ID distinct de l'identité MORPHEUS ;
- [x] renommage d'external ID avec continuité explicite ;
- [x] duplication sans fusion silencieuse ;
- [x] suppression / restauration ;
- [x] archivage ;
- [x] collision cross-provider ;
- [x] collision entre identifiants contradictoires ;
- [x] niveaux `RESOLVED`, `PARTIALLY_RESOLVED`, `HEURISTIC` exercés.

## Ce que E03 ne décide pas encore

Le format de production de `DomainIdentity` reste ouvert :

```text
UUID
ULID
identifiant numérique local
identifiant aléatoire opaque
autre
```

Ce choix devra être comparé avec :

- génération hors ligne ;
- sérialisation ;
- stockage ;
- fusion multi-source ;
- lisibilité des diagnostics ;
- performances du backend ;
- compatibilité future multi-projets.

Le spike démontre la **sémantique**, pas le format physique final.

## Impact ADR-0009

**Preuve positive forte sur les invariants d'identité.**

ADR-0009 reste cependant `Proposée`, car ses critères d'acceptation demandent encore :

1. le choix documenté du format concret de `DomainIdentity` ;
2. l'exécution des mêmes invariants sur le store mémoire ;
3. l'exécution sur le backend persistant candidat ;
4. la confirmation que les IDs publics ne dépendent pas du provider.

## Décision

```text
E03 = PASS
IDENTITY_SEMANTICS = RETAIN
PRODUCT_ID_FORMAT = DEFER
```

La prochaine preuve logique est E04/E05 : appliquer ces identités à plusieurs états temporels et snapshots sans perdre la continuité.
