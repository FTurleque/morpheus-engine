# ADR-0081 — AcceptanceCriterion et VerificationStatus first-class

- Statut : **Acceptée — M15**
- Date : 26 juillet 2026
- Dépend de : ADR-0001, ADR-0005, ADR-0009, ADR-0031, ADR-0044, ADR-0049, ADR-0079
- Portée : M15, modèle canonique d'acceptance, vérification, preuve et surfaces associées

## Contexte

Depuis M6, MORPHEUS expose explicitement :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

car aucun type production `AcceptanceCriterion` n'existait encore. Cette décision a volontairement empêché la conversion silencieuse d'un `Scenario` en critère d'acceptation.

M15 ferme ce gap sans casser les invariants acquis :

```text
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
```

Le domaine possède déjà `Evidence`, `EvidenceId` et `Provenance`. `Evidence` désigne un matériau source précis ; il ne faut pas créer un second concept concurrent uniquement pour M15.

## Décision

Introduire dans `morpheus-domain` :

```text
AcceptanceCriterionId
VerificationStatus
AcceptanceCriterion
```

### AcceptanceCriterionId

Identité MORPHEUS-owned basée sur `DomainIdentity`, selon le même pattern que `RequirementId`, `TaskId`, etc.

### VerificationStatus

Taxonomie canonique :

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Aucun état n'est déduit de la simple présence d'un test, d'une référence externe ou d'un scénario.

### AcceptanceCriterion

Forme canonique :

```text
AcceptanceCriterion
├── id
├── optional requirementId
├── optional changeId
├── title
├── condition
├── verificationStatus
├── verificationEvidenceIds[]
└── provenance
```

Au moins un des rattachements `requirementId` / `changeId` doit être présent.

Un critère peut référencer les deux lorsque la source fournit explicitement les deux relations. MORPHEUS ne complète pas le second rattachement par inférence textuelle.

## Deux niveaux de preuve

`provenance.evidenceId` et `verificationEvidenceIds` ont des responsabilités distinctes.

### Provenance du critère

```text
criterion.provenance.evidenceId
```

répond à :

> D'où vient la définition de ce critère ?

### Preuve de vérification

```text
criterion.verificationEvidenceIds
```

répond à :

> Quelles preuves soutiennent l'état de vérification affirmé ?

Cette séparation évite de considérer le simple texte définissant le critère comme preuve que le critère est satisfait.

## Invariant de preuve

```text
UNKNOWN              -> verification evidence optional
NOT_VERIFIED         -> verification evidence optional
PARTIALLY_VERIFIED   -> at least one verification evidence
VERIFIED             -> at least one verification evidence
FAILED               -> at least one verification evidence
```

La liste est canonique, non nulle, sans doublon et triée par `EvidenceId`.

Cette règle garantit notamment :

```text
Test existence != VERIFIED
Evidence != assertion
```

Un adapter qui ne dispose que d'un statut sans preuve exploitable doit conserver `UNKNOWN` ou `NOT_VERIFIED` selon les faits réellement observés, plutôt que fabriquer une preuve.

## Traçabilité

Ajouter :

```text
TraceabilityEntityKind.ACCEPTANCE_CRITERION
TraceabilityEntityKind.EVIDENCE
```

Les relations M15 sont dérivées uniquement à partir des rattachements structurels explicites :

```text
Requirement          VERIFIED_BY AcceptanceCriterion
ChangeProposal       VERIFIED_BY AcceptanceCriterion
AcceptanceCriterion  VERIFIED_BY Evidence
```

Le dernier lien ne concerne que les `verificationEvidenceIds`, jamais la seule provenance source du critère.

## Compatibilité M6

ADR-0049 reste historiquement correcte : au moment de M6, aucun `AcceptanceCriterion` production n'existait.

M15 remplace le comportement :

```text
UNAVAILABLE_IN_NORMALIZED_MODEL
```

par une vraie évaluation de couverture lorsque le modèle est disponible. Zéro critère devient `NO_CRITERIA`, et non « modèle indisponible ».

## Alternatives rejetées

### Convertir Scenario en AcceptanceCriterion

Rejeté : violation directe de l'invariant historique et perte sémantique.

### Utiliser seulement ExternalReference(test)

Rejeté : un test est un artefact externe, pas la condition d'acceptation elle-même, et sa simple existence ne prouve pas son succès.

### Créer VerificationEvidence séparé de Evidence

Rejeté : le concept `Evidence` est déjà provider-neutral et adapté à la provenance de matériaux sources. La distinction nécessaire est portée par la relation au critère, pas par un doublon de type.

### Booléen verified

Rejeté : insuffisant pour représenter `UNKNOWN`, partiel, échec et absence de vérification.

## Conséquences

Positives :

- critère d'acceptation réellement first-class ;
- état de vérification explicite ;
- séparation définition/preuve ;
- pas d'inférence silencieuse depuis scénarios/tests ;
- persistance Memory/SQLite snapshot-scoped ;
- traçabilité Requirement/Change/Criterion/Evidence ;
- couverture et diagnostics réellement calculables ;
- surfaces CLI/MCP/HTTP cohérentes ;
- orchestration capable d'observer les critères sans inventer les blockers de M16.

Coûts :

- nouvelle entité normalisée et persistée ;
- migration SQLite V009 ;
- extension des vues et contrats machine ;
- les providers sans structure acceptance explicite restent `UNSUPPORTED` plutôt que d'utiliser une heuristique.

## Validation d'acceptation

Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md).

Head de code validé :

```text
9e6450a099157cfdfcd11cc29dfb986ef7701247
```

Gate :

```text
AcceptanceCriterion invariants PASS
VerificationStatus invariants PASS
AcceptanceCriterionId round-trip PASS
TraceabilityEntityKind integration PASS
Memory / SQLite persistence + reopen PASS
CLI / MCP / HTTP surfaces PASS
Architecture 157/157 PASS
TOTAL 371/371 PASS
Packaging Windows + smokes PASS
BUILD SUCCESS
```

ADR-0081 est donc **Acceptée — M15**.