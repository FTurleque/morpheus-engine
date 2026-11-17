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

Forme canonique d'un critère et liens de traçabilité qu'il ouvre :

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

Requirement          VERIFIED_BY AcceptanceCriterion
ChangeProposal       VERIFIED_BY AcceptanceCriterion
AcceptanceCriterion  VERIFIED_BY Evidence
```

Les deux niveaux de preuve répondent à deux questions : `criterion.provenance.evidenceId` à « d'où vient la définition
de ce critère ? », `criterion.verificationEvidenceIds` à « quelles preuves soutiennent l'état de vérification
affirmé ? ». Leur séparation évite de prendre le texte qui définit le critère pour la preuve qu'il est satisfait.

## Decision

1. `morpheus-domain` porte, dans `com.morpheus.domain.acceptance`, `AcceptanceCriterionId` (identité MORPHEUS-owned
   sur `DomainIdentity`, patron de `RequirementId`), `VerificationStatus` et `AcceptanceCriterion`
   (`id`, `requirementId` et `changeId` optionnels, `title`, `condition`, `verificationStatus`,
   `verificationEvidenceIds`, `provenance`).
2. `VerificationStatus` vaut `NOT_VERIFIED`, `PARTIALLY_VERIFIED`, `VERIFIED`, `FAILED` ou `UNKNOWN`. Aucun état n'est
   déduit de la seule présence d'un test, d'une référence externe ou d'un scénario.
3. Au moins un des rattachements `requirementId` / `changeId` est présent ; les deux le sont seulement si la source
   fournit explicitement les deux relations. Aucun rattachement n'est complété par inférence.
4. `provenance.evidenceId` (origine de la définition) n'est jamais une preuve de vérification, que seuls portent les
   `verificationEvidenceIds`.
5. `PARTIALLY_VERIFIED`, `VERIFIED` et `FAILED` exigent au moins une preuve de vérification, optionnelle pour
   `UNKNOWN` et `NOT_VERIFIED`. La liste est non nulle, sans doublon, triée par `EvidenceId`. Sans preuve exploitable,
   un adaptateur garde `UNKNOWN` ou `NOT_VERIFIED` selon les faits observés.
6. `TraceabilityEntityKind` porte `ACCEPTANCE_CRITERION` et `EVIDENCE`. Les liens `VERIFIED_BY` (requirement ou
   changement vers critère, critère vers `Evidence`) ne dérivent que des rattachements explicites ; le second ne
   vient que des `verificationEvidenceIds`.
7. La couverture d'acceptance d'un snapshot publié est évaluée par `AcceptanceQualityService` (ADR-0124) ; zéro
   critère donne `NO_CRITERIA`, jamais « modèle indisponible ». L'analyse d'un changement proposé n'évalue pas cette
   couverture et rend le statut que fixe ADR-0138.
8. Un provider sans structure d'acceptance explicite ne déclare pas `READ_ACCEPTANCE_CRITERIA` et ne produit aucun critère.

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

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine propre à MORPHEUS.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité de domaine stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et versions.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — projection du contenu métier d'un snapshot.
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — analyse de changement.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — état d'orchestration d'un changement.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité, `Scenario != AcceptanceCriterion`.
- [VALIDATION_M15](../../project/validation/VALIDATION_M15.md) — preuve du jalon M15.
