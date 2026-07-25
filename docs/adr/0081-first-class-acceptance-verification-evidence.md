# ADR-0081 — AcceptanceCriterion et VerificationStatus first-class

- Statut : **Proposée — M15-S1**
- Date : 26 juillet 2026
- Dépend de : ADR-0001, ADR-0005, ADR-0009, ADR-0031, ADR-0044, ADR-0049, ADR-0079
- Portée : M15-S1, modèle canonique d'acceptance et de vérification

## Contexte

Depuis M6, MORPHEUS expose explicitement :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

car aucun type production `AcceptanceCriterion` n'existe encore. Cette décision a volontairement empêché la conversion silencieuse d'un `Scenario` en critère d'acceptation.

M15 doit fermer ce gap sans casser les invariants acquis :

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
```

Les relations exactes sont introduites dans M15-S4. S1 ne doit pas encore synthétiser de `TraceabilityLink`.

## Compatibilité M6

ADR-0049 reste historiquement correcte : au moment de M6, aucun `AcceptanceCriterion` production n'existait.

M15 remplacera progressivement le comportement :

```text
UNAVAILABLE_IN_NORMALIZED_MODEL
```

par une vraie évaluation de couverture seulement après normalisation et persistance des critères.

## Alternatives rejetées

### Convertir Scenario en AcceptanceCriterion

Rejeté : violation directe de l'invariant historique et perte sémantique.

### Utiliser seulement ExternalReference(test)

Rejeté : un test est un artefact externe, pas la condition d'acceptation elle-même, et sa simple existence ne prouve pas son succès.

### Créer VerificationEvidence séparé de Evidence

Rejeté pour S1 : le concept `Evidence` est déjà provider-neutral et adapté à la provenance de matériaux sources. La distinction nécessaire est portée par la relation au critère, pas par un doublon de type.

### Booléen verified

Rejeté : insuffisant pour représenter `UNKNOWN`, partiel, échec et absence de vérification.

## Conséquences

Positives :

- critère d'acceptation réellement first-class ;
- état de vérification explicite ;
- séparation définition/preuve ;
- pas d'inférence silencieuse depuis scénarios/tests ;
- base stable pour persistance, qualité, orchestration et surfaces machine.

Coûts :

- nouvelle entité à normaliser et persister ;
- migration SQLite requise en S3 ;
- dérivation de traçabilité à étendre ;
- adaptation des compact views/API/MCP/CLI en S6.

## Validation requise avant acceptation

ADR-0081 ne passe en **Acceptée — M15** qu'après preuve S1 :

```text
AcceptanceCriterion invariants PASS
VerificationStatus invariants PASS
AcceptanceCriterionId round-trip PASS
TraceabilityEntityKind integration PASS
full relevant domain/application compilation PASS
```
