# ADR-0095 — Full publish version semantics and failed candidates

- Statut : **Acceptée — post-audit M28**
- Date : 19 août 2026
- Dépend de : ADR-0031, ADR-0060, ADR-0094
- Portée : publication complète, version métier, snapshot technique, récupération après panne

## Contexte

ADR-0031 établit correctement que `SpecificationVersion` et `KnowledgeSnapshot` sont deux concepts distincts et que le modèle doit permettre à une reconstruction technique de réutiliser explicitement une version métier existante.

ADR-0060 introduit ensuite `ProjectSnapshotImportService.publishFull(...)` comme opération de publication complète et crée une nouvelle `SpecificationVersion` à chaque publication. Pris sans précision supplémentaire, les deux textes peuvent laisser croire que toute reconstruction technique doit réutiliser la version courante, alors que le chemin `publishFull` est volontairement une opération de publication sémantique et non un simple refresh technique transparent.

Un second point concerne les échecs tardifs. MORPHEUS conserve le candidat en état `FAILED` afin de rendre la tentative observable. Les données déjà persistées pour ce candidat constituent donc une trace durable de tentative, et non une baseline publiée.

## Décision

### 1. Capacité du modèle

L'invariant d'ADR-0031 reste valable :

```text
SpecificationVersion != KnowledgeSnapshot
```

Le modèle et les ports de persistance restent capables de lier plusieurs snapshots techniques à une même `SpecificationVersionId` lorsqu'un flux de reconstruction explicitement technique le demande.

### 2. Sémantique de `publishFull`

`ProjectSnapshotImportService.publishFull(...)` est une **commande explicite de publication complète**. Chaque exécution acceptée par ce service crée une nouvelle `SpecificationVersionId` et consomme une nouvelle `sequence`.

Ainsi :

```text
technical reconstruction capability != publishFull command semantics
```

Un futur flux de relecture/reprojection purement technique qui ne doit pas créer de version métier devra utiliser un contrat distinct et fournir explicitement la `SpecificationVersionId` réutilisée. Il ne doit pas détourner `publishFull`.

### 3. Candidat FAILED

Un candidat qui a reçu une `SpecificationVersion` avant un échec tardif reste durable et passe à `FAILED`. Sa version n'est jamais publiée comme baseline ACTIVE, mais son numéro de séquence reste consommé comme identifiant ordinal de tentative durable.

La prochaine publication calcule donc :

```text
nextSequence = max(sequence de toutes les versions liées à un snapshot durable) + 1
```

et non :

```text
activeVersion.sequence + 1
```

Exemple :

```text
sequence 1 -> ACTIVE
sequence 2 -> FAILED
sequence 3 -> ACTIVE après retry
```

La `predecessor` métier de la version 3 reste la version 1, car la version 2 n'a jamais été publiée. La séquence est un ordre durable de création, pas une chaîne de predecessor.

## Conséquences

- aucune collision `(project, sequence)` ne peut être produite par un retry normal après un échec tardif ;
- les tentatives FAILED restent inspectables pour le diagnostic et la récupération ;
- l'ancien snapshot ACTIVE reste autoritaire jusqu'à l'activation finale du nouveau candidat ;
- la distinction version métier / snapshot technique reste conservée au niveau du modèle ;
- l'ambiguïté entre ADR-0031 et ADR-0060 est levée pour le chemin `publishFull`.

## Preuves attendues

Les tests de contrat doivent démontrer :

1. première publication -> sequence 1 ;
2. échec tardif après création de version -> candidat FAILED avec sequence 2 ;
3. retry valide -> sequence 3 ;
4. predecessor du retry -> version ACTIVE précédente ;
5. un seul snapshot ACTIVE à tout instant.
