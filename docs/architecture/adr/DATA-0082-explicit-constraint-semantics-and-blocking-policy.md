---
id: DATA-0082
title: "Sémantique explicite des contraintes et politique de blocage"
domain: data
rules: false
---

# Sémantique explicite des contraintes et politique de blocage

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

MORPHEUS normalise et expose des `Constraint` portant une identité, un changement, un texte et une provenance. Le
cycle de vie d'un changement (ADR-0119) et l'état d'orchestration (ADR-0079) doivent pouvoir dire si une contrainte
bloque une transition, sans jamais interpréter le texte d'une contrainte comme une politique exécutable.

Principes à préserver :

```text
applicable != blocking
warning != blocker
UNKNOWN != BLOCKED
constraint text != executable policy
policy decision must expose provenance and reason
```

Options écartées :

- **Déduire la politique depuis le texte** : heuristique silencieuse, non portable, non prouvable.
- **`severity == CRITICAL` implique blocking** : `severity != policy`.
- **Absence de sémantique => non bloquant** : cela convertirait `UNKNOWN` en `false`.
- **Politique propre à un provider dans le domaine** : MORPHEUS conserve un modèle provider-neutral.

## Decision

Une contrainte peut porter une sémantique provider-neutral explicite :

```text
Constraint
├── id / changeId / statement
├── applicability
├── severity
├── satisfaction
├── blockingPolicy
├── supportingEvidenceIds[]
└── provenance
```

### ConstraintApplicability

```text
APPLICABLE
NOT_APPLICABLE
UNKNOWN
```

`UNKNOWN` n'est jamais converti en `APPLICABLE`.

### ConstraintSeverity

```text
INFO
WARNING
ERROR
CRITICAL
UNKNOWN
```

La sévérité exprime l'importance du constat. Elle ne décide jamais seule d'un blocage.

### ConstraintSatisfaction

```text
SATISFIED
VIOLATED
UNKNOWN
```

`SATISFIED` et `VIOLATED` nécessitent au moins une preuve de support explicite. Une absence de preuve conserve
`UNKNOWN`.

### ConstraintBlockingPolicy

La politique canonique contient un mode et des cibles lifecycle explicites.

Modes :

```text
NON_BLOCKING
BLOCK_WHEN_VIOLATED
UNKNOWN
```

Règles :

```text
NON_BLOCKING          -> aucune cible bloquée
UNKNOWN               -> aucune cible supposée
BLOCK_WHEN_VIOLATED   -> au moins une target lifecycle explicite
```

Une contrainte n'est donc jamais bloquante « par texte » ou « par sévérité ».

### Évaluation

`ConstraintPolicyEvaluationService` produit un `ConstraintEvaluation` pour un contexte de transition, dans cet ordre
déterministe :

```text
NOT_APPLICABLE                           -> NOT_APPLICABLE
applicability UNKNOWN                    -> UNKNOWN
blocking policy UNKNOWN                  -> UNKNOWN
NON_BLOCKING                             -> NON_BLOCKING
target hors policy                       -> NON_BLOCKING
satisfaction UNKNOWN                     -> UNKNOWN
satisfaction SATISFIED                   -> NON_BLOCKING
satisfaction VIOLATED + target explicite -> BLOCKING
```

Chaque évaluation expose :

```text
constraintId
changeId
targetState
state
applicability
severity
satisfaction
blockingPolicy
reason
supportingEvidenceIds
sourceEvidenceId
```

### Compatibilité providers

Les contraintes historiques ou OpenSpec qui ne fournissent pas cette sémantique restent :

```text
applicability = UNKNOWN
severity      = UNKNOWN
satisfaction  = UNKNOWN
policy        = UNKNOWN
```

Elles restent visibles et requêtables mais ne peuvent pas devenir bloquantes.

Le provider synthétique porte des cas explicites : une contrainte violée réellement bloquante sur une transition
ciblée ; une contrainte `WARNING` violée mais `NON_BLOCKING` ; une contrainte à sémantique inconnue qui reste
`UNKNOWN`.

### Persistance

La migration SQLite `V010__constraint_semantics.sql` ajoute les champs sémantiques aux contraintes rattachées au
snapshot, ainsi que les relations ordonnées :

```text
constraint -> blocking lifecycle targets
constraint -> supporting evidence
```

`store-memory` et `store-sqlite` produisent la même projection, y compris après réouverture.

### Lifecycle et orchestration

Les décisions lifecycle structurelles restent propriétaires de leurs invariants. La politique de contraintes
explicites s'applique ensuite :

```text
base lifecycle decision ALLOWED
+ explicit BLOCKING constraint for target
= BLOCKED with explainable constraint reason
```

Une évaluation de contrainte `UNKNOWN` ne devient jamais un blocker ; elle rend le fait de blocage indisponible
lorsque ce fait est requis pour décider.

## Do's and Don'ts

### Do

- Exiger au moins une preuve de support pour une satisfaction `SATISFIED` ou `VIOLATED`.
- Exiger au moins une cible lifecycle pour `BLOCK_WHEN_VIOLATED`, et aucune pour `NON_BLOCKING` ou `UNKNOWN`.
- Évaluer une contrainte dans l'ordre déterministe ci-dessus, et exposer l'état, la raison et les preuves de chaque
  évaluation.
- Laisser `UNKNOWN` sur les quatre axes d'une contrainte dont le provider ne fournit pas de sémantique.
- Expliquer un blocage de transition par chaque contrainte `BLOCKING` qui le cause.
- Garder la projection des contraintes identique entre `store-memory` et `store-sqlite`.

### Don't

- Ne pas déduire une applicabilité, une satisfaction ou une politique du texte de la contrainte.
- Ne pas faire bloquer une contrainte en raison de sa seule sévérité.
- Ne pas convertir `UNKNOWN` en `APPLICABLE`, en `BLOCKING` ni en non bloquant.
- Ne pas bloquer une transition qui n'est pas une cible explicite de la politique.
- Ne pas introduire de politique propre à un provider dans le domaine.

## Consequences

### Positives

- Un blocage est toujours explicable par une contrainte, sa politique, sa satisfaction et ses preuves.
- Une contrainte sans sémantique reste visible sans pouvoir bloquer ni être silencieusement ignorée.

### Négatives

- Un provider qui ne fournit pas de sémantique explicite ne peut jamais produire de contrainte bloquante.
- La migration `V010` élargit le schéma des contraintes et ajoute deux relations ordonnées.

### Risques

- Une décision qui exige le fait de blocage reste indisponible tant qu'une contrainte pertinente est `UNKNOWN` :
  l'appelant doit traiter cette indisponibilité explicitement.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/constraint/ConstraintSemanticsTest.java` : invariants de la
  politique (cibles exigées ou interdites selon le mode) et preuve de support exigée pour `SATISFIED`/`VIOLATED`.
- `morpheus-application/src/test/java/com/morpheus/application/constraint/ConstraintPolicyEvaluationServiceTest.java` :
  `unknownApplicabilityNeverBecomesBlocked`, `warningViolationDoesNotBlockWhenPolicyIsNonBlocking`,
  `criticalSeverityDoesNotBlockOutsideExplicitTarget`, `targetedSatisfiedConstraintDoesNotBlock`,
  `targetedUnknownSatisfactionRemainsUnknown`, `targetedViolatedConstraintBlocksWithEvidenceAndReason`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ConstraintSemanticsPersistenceContractTest.java` :
  parité mémoire/SQLite et réouverture SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` :
  `unknownConstraintNeverBecomesBlockedOrSilentlyAllowed`, `explicitNonBlockingConstraintKeepsAllowedTransitionAllowed`,
  `targetedViolatedConstraintBlocksAndExplainsTransition`.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusConstraintSemanticsApiContractTest.java` et
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusM16McpStdioIntegrationTest.java` : restitution par HTTP et MCP.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine possédé par MORPHEUS.
- [ADR-0044](DATA-0044-snapshot-business-content-projection.md) — projection du contenu métier du snapshot.
- [ADR-0079](BE-0079-nondestructive-change-orchestration-state.md) — état d'orchestration non destructif.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — preuves d'acceptation.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité et faits tri-état.
- [VALIDATION_M16](../../project/validation/VALIDATION_M16.md)
