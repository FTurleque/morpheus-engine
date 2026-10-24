---
id: BE-0079
title: "État d'orchestration agrégé non destructif"
domain: backend
rules: false
---

# État d'orchestration agrégé non destructif

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

Le cas d'usage UC-16 exige une réponse compacte pour JARVIS : lifecycle, manques, critères d'acceptation, liens non
résolus, contraintes bloquantes et transitions possibles.

Le modèle normalisé n'observe pas toutes ces dimensions avec la même précision. Une vue d'orchestration doit
distinguer une absence réelle d'une information non connue, et ne rien inventer pour combler l'écart.

## Decision

Une agrégation en lecture seule, `ChangeOrchestrationState` (`com.morpheus.application.orchestration`), construite par
`ChangeOrchestrationStateService`, expose pour un changement du snapshot `ACTIVE` :

```text
snapshot
change
lifecycle
observableFacts
missingArtifacts
unavailableFacts
acceptanceCriteria
applicableConstraints
blockingConstraints
unresolvedLinks
qualityFindings
nextAllowedTransitions
transitionEvaluations
persisted=false
```

### Artefacts manquants

Un artefact n'est déclaré manquant que lorsque l'absence est déterministe : le fait observé correspondant vaut
`FALSE`. Un fait inconnu figure dans `unavailableFacts`, jamais dans `missingArtifacts`.

### Critères d'acceptation

La vue expose le nombre de critères d'acceptation explicites rattachés au changement dans le snapshot publié, selon
ADR-0081. Un `Scenario` ne devient jamais un `AcceptanceCriterion`.

### Contraintes bloquantes

`applicableConstraints` liste les contraintes du changement avec leur applicabilité, sévérité, satisfaction, mode et
cibles de blocage, et leurs preuves. `blockingConstraints` qualifie leur caractère bloquant selon la sémantique
explicite d'ADR-0082 :

| Statut | Condition | `observedCount` |
|---|---|---|
| `AVAILABLE` | aucune contrainte, ou sémantique de blocage explicite pour toutes | contraintes applicables, `BLOCK_WHEN_VIOLATED` et `VIOLATED` |
| `PARTIALLY_AVAILABLE` | sémantique explicite pour une partie seulement | idem, sur les contraintes connues |
| `UNKNOWN` | aucune sémantique de blocage explicite | 0 |

`UNKNOWN` n'est jamais traité comme bloquant. Hors `AVAILABLE`, `blockingConstraints` figure dans `unavailableFacts`.

### Liens non résolus

Sont exposés, sans forcer de résolution live :

- les liens de traçabilité sortants du changement dont la résolution diffère de `RESOLVED` ;
- les `ExternalReference` du snapshot appartenant au changement dont l'état diffère de `RESOLVED`.

### Transitions

Les transitions sont évaluées depuis l'état de lifecycle fourni, sans l'inférer ; `nextAllowedTransitions` ne retient
que les évaluations `ALLOWED` (ADR-0119).

### Temporalité

La vue est une observation du snapshot `ACTIVE` et n'est jamais persistée comme nouvel état métier : le constructeur
de `ChangeOrchestrationState` refuse `persisted=true`.

## Do's and Don'ts

### Do

- Construire la vue depuis le snapshot `ACTIVE` uniquement.
- Séparer `missingArtifacts` (fait `FALSE`) et `unavailableFacts` (fait inconnu).
- Qualifier le blocage des contraintes par leur politique explicite (ADR-0082).
- Exposer les liens de traçabilité et les références externes non résolus du changement.

### Don't

- Ne pas persister la vue ni muter le snapshot en la construisant.
- Ne pas convertir un `Scenario` en critère d'acceptation.
- Ne pas traiter une sémantique de blocage `UNKNOWN` comme bloquante ni comme autorisée en silence.
- Ne pas déclarer manquant un artefact dont l'absence n'est pas établie.
- Ne pas déclencher de résolution live pour lister les liens non résolus.
- Ne pas inférer l'état de lifecycle du changement.

## Consequences

### Positives

- Une réponse compacte et stable pour JARVIS, sans état caché.
- L'inconnu reste visible comme tel au lieu d'être converti en absence ou en blocage.

### Négatives

- Le consommateur doit gérer trois statuts de disponibilité au lieu d'un booléen.
- La vue est recalculée à chaque lecture.

### Risques

- Aucun risque n'est consigné au-delà des conséquences négatives.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` :
  lifecycle non inféré, contrainte inconnue jamais bloquante ni autorisée en silence, contrainte non bloquante
  explicite, contrainte violée ciblée qui bloque, abandon sans raison qui exige une entrée.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusJarvisOrchestrationApiContractTest.java` : contrat HTTP de la
  vue.
- Constructeur compact de `ChangeOrchestrationState` : refus de `persisted=true`.

## References

- [ADR-0142](DATA-0142-references-externes.md) — références externes du snapshot.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité, findings et faits tri-état.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation et preuves de
  vérification.
- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) — sémantique des contraintes et
  politique de blocage.
- [ADR-0139](ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) — surfaces d'orchestration JARVIS.
- [VALIDATION_M14](../../project/validation/VALIDATION_M14.md) — preuve du jalon M14.
