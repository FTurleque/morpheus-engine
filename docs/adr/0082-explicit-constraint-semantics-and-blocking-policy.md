# ADR-0082 — Sémantique explicite des contraintes et politique de blocage

- Statut : **Acceptée — M16**
- Date : 26 juillet 2026
- Dépend de : ADR-0001, ADR-0005, ADR-0032, ADR-0044, ADR-0050, ADR-0078, ADR-0079, ADR-0081
- Portée : M16 — Constraint Semantics & Policy Enforcement

## Contexte

MORPHEUS sait déjà normaliser et exposer des `Constraint`, mais jusqu'à M15 une contrainte ne porte qu'une identité, un changement, un texte et une provenance. Le contrat d'orchestration M14/M15 expose donc :

```text
blockingConstraints.status = UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED
```

Cette indisponibilité est volontaire : le texte d'une contrainte ne doit jamais être interprété comme une politique exécutable.

M16 ferme ce gap sans casser :

```text
applicable != blocking
warning != blocker
UNKNOWN != BLOCKED
constraint text != executable policy
policy decision must expose provenance and reason
```

## Décision

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

`SATISFIED` et `VIOLATED` nécessitent au moins une preuve de support explicite. Une absence de preuve conserve `UNKNOWN`.

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

## Evaluation

`ConstraintPolicyEvaluationService` produit un `ConstraintEvaluation` pour un contexte de transition.

Ordre déterministe :

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

## Compatibilité providers

Les contraintes historiques/OpenSpec qui ne fournissent pas cette sémantique restent :

```text
applicability = UNKNOWN
severity      = UNKNOWN
satisfaction  = UNKNOWN
policy        = UNKNOWN
```

Elles restent visibles et requêtables mais ne peuvent pas devenir bloquantes.

Le provider synthétique M16 porte des cas explicites permettant de prouver :

- une contrainte violée réellement bloquante sur une transition ciblée ;
- une contrainte `WARNING` violée mais `NON_BLOCKING` ;
- une contrainte à sémantique inconnue qui reste `UNKNOWN`.

## Persistance

SQLite V010 ajoute les champs sémantiques aux contraintes snapshot-scoped ainsi que les relations ordonnées :

```text
constraint -> blocking lifecycle targets
constraint -> supporting evidence
```

Memory et SQLite produisent la même projection après reopen.

## Lifecycle / orchestration

Les décisions lifecycle structurelles de M3 restent propriétaires de leurs invariants. M16 ajoute ensuite la politique de contraintes explicites :

```text
base lifecycle decision ALLOWED
+ explicit BLOCKING constraint for target
= BLOCKED with explainable constraint reason
```

Une évaluation de contrainte `UNKNOWN` ne devient jamais un blocker ; elle rend le fait de blocage indisponible lorsque ce fait est requis pour décider.

## Alternatives rejetées

### Déduire depuis le texte

Rejeté : heuristique silencieuse, non portable, non prouvable.

### `severity == CRITICAL` implique blocking

Rejeté : `severity != policy`.

### Absence de sémantique => non bloquant

Rejeté : cela convertirait `UNKNOWN` en `false`.

### Provider-specific policy dans le domaine

Rejeté : MORPHEUS conserve un modèle provider-neutral.

## Validation d'acceptation

Gate M16 exécuté sur :

```text
f349c5f4701665e649d985426d35b5e6a6060e32
```

Résultats :

```text
constraint semantic invariants PASS
UNKNOWN != BLOCKED PASS
warning != blocker PASS
Memory == SQLite PASS
SQLite close/reopen PASS
orchestration blockingConstraints AVAILABLE/UNKNOWN explicite PASS
transition explanation includes every blocking constraint PASS
CLI/MCP/HTTP coherent PASS
full Maven gate 393/393 PASS
Architecture 161/161 PASS
Windows packaging + smokes PASS
```

Preuve : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).

ADR-0082 est donc **Acceptée — M16**.