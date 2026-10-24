---
id: DATA-0081
title: "AcceptanceCriterion et VerificationStatus first-class"
domain: data
rules: false
---

# AcceptanceCriterion et VerificationStatus first-class

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

MORPHEUS refuse de convertir silencieusement un `Scenario` en critère d'acceptation (ADR-0124). Pour évaluer une
couverture d'acceptance, il lui faut donc un critère d'acceptation de première classe, sans casser les invariants :

```text
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
```

Le domaine possède déjà `Evidence`, `EvidenceId` et `Provenance`. `Evidence` désigne un matériau source précis ; il ne
faut pas créer un second concept concurrent.

Options rejetées :

- **Convertir Scenario en AcceptanceCriterion** : violation directe de l'invariant et perte sémantique.
- **Utiliser seulement `ExternalReference(test)`** : un test est un artefact externe, pas la condition d'acceptation
  elle-même, et sa simple existence ne prouve pas son succès.
- **Créer `VerificationEvidence` séparé d'`Evidence`** : `Evidence` est déjà provider-neutral et adapté à la
  provenance de matériaux sources ; la distinction nécessaire est portée par la relation au critère, pas par un
  doublon de type.
- **Booléen `verified`** : insuffisant pour représenter `UNKNOWN`, partiel, échec et absence de vérification.

## Decision

`morpheus-domain` porte, dans `com.morpheus.domain.acceptance` :

```text
AcceptanceCriterionId
VerificationStatus
AcceptanceCriterion
```

### AcceptanceCriterionId

Identité MORPHEUS-owned basée sur `DomainIdentity`, selon le même patron que `RequirementId` ou `TaskId`.

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

Au moins un des rattachements `requirementId` / `changeId` est présent. Un critère peut référencer les deux lorsque la
source fournit explicitement les deux relations ; MORPHEUS ne complète pas le second rattachement par inférence
textuelle.

### Deux niveaux de preuve

`provenance.evidenceId` et `verificationEvidenceIds` ont des responsabilités distinctes :

- `criterion.provenance.evidenceId` répond à « d'où vient la définition de ce critère ? » ;
- `criterion.verificationEvidenceIds` répond à « quelles preuves soutiennent l'état de vérification affirmé ? ».

Cette séparation évite de considérer le texte qui définit le critère comme preuve que le critère est satisfait.

### Invariant de preuve

```text
UNKNOWN              -> verification evidence optional
NOT_VERIFIED         -> verification evidence optional
PARTIALLY_VERIFIED   -> at least one verification evidence
VERIFIED             -> at least one verification evidence
FAILED               -> at least one verification evidence
```

La liste est canonique, non nulle, sans doublon et triée par `EvidenceId`. Un adaptateur qui ne dispose que d'un statut
sans preuve exploitable conserve `UNKNOWN` ou `NOT_VERIFIED` selon les faits réellement observés, plutôt que fabriquer
une preuve.

### Traçabilité

`TraceabilityEntityKind` porte `ACCEPTANCE_CRITERION` et `EVIDENCE`. Les relations sont dérivées uniquement des
rattachements structurels explicites :

```text
Requirement          VERIFIED_BY AcceptanceCriterion
ChangeProposal       VERIFIED_BY AcceptanceCriterion
AcceptanceCriterion  VERIFIED_BY Evidence
```

Le dernier lien ne concerne que les `verificationEvidenceIds`, jamais la seule provenance source du critère.

### Couverture d'acceptance

La couverture d'acceptance d'un snapshot publié est évaluée par `AcceptanceQualityService` sur les critères de sa
projection de contenu métier : `EVALUATED` quand au moins un critère existe, `NO_CRITERIA` quand il n'y en a aucun
(zéro critère n'est pas « modèle indisponible »).

L'analyse d'un changement proposé (ADR-0056) n'évalue pas cette couverture : elle rend toujours
`AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL` avec le warning `ACCEPTANCE_CRITERIA_UNAVAILABLE`, car le
`ProposedChangeSet` ne porte aucun critère.

### Providers

Un provider sans structure d'acceptance explicite ne déclare pas la capacité `READ_ACCEPTANCE_CRITERIA` et ne produit
aucun critère, plutôt que d'utiliser une heuristique.

## Do's and Don'ts

### Do

- Rattacher chaque critère à un requirement, à un changement, ou aux deux, depuis des relations explicites de la
  source.
- Exiger au moins une preuve de vérification pour `PARTIALLY_VERIFIED`, `VERIFIED` et `FAILED`.
- Garder `verificationEvidenceIds` trié par `EvidenceId` et sans doublon.
- Conserver `UNKNOWN` ou `NOT_VERIFIED` quand aucune preuve exploitable n'est disponible.
- Rendre `NO_CRITERIA` pour un snapshot sans critère.

### Don't

- Ne pas convertir un `Scenario` en `AcceptanceCriterion`.
- Ne pas déduire un `VerificationStatus` de la présence d'un test, d'une référence externe ou d'un scénario.
- Ne pas traiter la provenance d'un critère comme preuve de sa vérification.
- Ne pas compléter un rattachement manquant par inférence textuelle.
- Ne pas créer de type de preuve concurrent d'`Evidence`.
- Ne pas affirmer que l'analyse de changement évalue les critères d'acceptation.

## Consequences

### Positives

- Critère d'acceptation réellement first-class et état de vérification explicite.
- Séparation entre définition et preuve.
- Aucune inférence silencieuse depuis les scénarios ou les tests.
- Persistance snapshot-scoped en mémoire et en SQLite.
- Traçabilité Requirement / Change / Criterion / Evidence.
- Couverture et diagnostics calculables, exposés par la CLI, MCP et HTTP.

### Négatives

- Une entité normalisée et persistée de plus, avec sa migration SQLite.
- Extension des vues et des contrats machine.
- Un provider sans structure d'acceptance explicite ne fournit aucun critère.

### Risques

- **Statut affirmé sans preuve** : mitigé par l'invariant de preuve du constructeur compact.
- **Lecture de `UNAVAILABLE_IN_NORMALIZED_MODEL` d'une analyse de changement comme absence de critère** : mitigé par le
  warning `ACCEPTANCE_CRITERIA_UNAVAILABLE` et par la distinction avec `NO_CRITERIA`.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/acceptance/AcceptanceCriterionTest.java` : invariants du
  critère (rattachement, preuve exigée, liste canonique).
- `morpheus-application/src/test/java/com/morpheus/application/quality/AcceptanceQualityServiceTest.java` :
  `EVALUATED` et `NO_CRITERIA`.
- `morpheus-application/src/test/java/com/morpheus/application/traceability/AcceptanceTraceabilityDerivationTest.java` :
  liens `VERIFIED_BY` dérivés des seuls rattachements explicites.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SnapshotBusinessContentPersistenceTest.java` :
  persistance des critères en mémoire et en SQLite.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecDiscoveryIntegrationTest.java`
  (`acceptanceCriteriaCannotBeInventedFromOpenSpecScenarios`) : aucun critère inventé depuis les scénarios.
- `contracts/public-surfaces.tsv`, ligne `acceptance.criteria` : surfaces CLI, MCP et HTTP.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine propre à MORPHEUS.
- [ADR-0009](DATA-0009-stable-domain-identity.md) — identité de domaine stable.
- [ADR-0031](ARCH-0031-explicit-temporal-projection-and-entity-version.md) — projection temporelle et versions.
- [ADR-0044](DATA-0044-snapshot-business-content-projection.md) — projection du contenu métier d'un snapshot.
- [ADR-0056](ARCH-0056-deterministic-current-proposed-change-analysis.md) — analyse de changement.
- [ADR-0079](BE-0079-nondestructive-change-orchestration-state.md) — état d'orchestration non destructif.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité, `Scenario != AcceptanceCriterion`.
- [VALIDATION_M15](../../project/validation/VALIDATION_M15.md) — preuve du jalon M15.
