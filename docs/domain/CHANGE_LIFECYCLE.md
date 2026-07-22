# Cycle de vie des changements — MORPHEUS

Statut : **Proposition C0 — à valider**

Date : 22 juillet 2026

Ce document définit le cycle de vie conceptuel d'un `ChangeProposal`.

> Le cycle de vie décrit l'avancement d'un changement. Il est orthogonal à l'état temporel `CURRENT / PROPOSED / HISTORICAL` et à la résolution `RESOLVED / ...`.

---

## 1. États candidats

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

---

## 2. Vue nominale

```text
DRAFT
  │
  ▼
PROPOSED
  │
  ▼
SPECIFIED
  │
  ▼
DESIGNED
  │
  ▼
PLANNED
  │
  ▼
IMPLEMENTING
  │
  ▼
VERIFYING
  │
  ▼
COMPLETED
  │
  ▼
ARCHIVED
```

`ABANDONED` est un état terminal métier accessible depuis plusieurs étapes.

---

## 3. `DRAFT`

### Signification

L'intention existe mais n'est pas encore considérée suffisamment formulée pour être examinée comme proposition formelle.

### Contenu minimal attendu

- titre ou identifiant ;
- intention sommaire ou placeholder reconnu par le provider.

### Transitions candidates

```text
DRAFT -> PROPOSED
DRAFT -> ABANDONED
```

### Condition vers `PROPOSED`

- objectif formulé ;
- portée initiale identifiable ;
- source valide.

---

## 4. `PROPOSED`

### Signification

Le changement est une proposition explicite mais les exigences détaillées peuvent être incomplètes.

### Attentes

- rationale ou justification ;
- objectif ;
- impact fonctionnel initial ;
- relation avec une ou plusieurs spécifications si applicable.

### Transitions

```text
PROPOSED -> SPECIFIED
PROPOSED -> DRAFT       (révision substantielle si politique autorisée)
PROPOSED -> ABANDONED
```

---

## 5. `SPECIFIED`

### Signification

Les comportements attendus, exigences, contraintes et critères nécessaires sont suffisamment définis pour concevoir une solution.

### Attentes candidates

- exigences impactées ;
- deltas fonctionnels ;
- contraintes applicables ;
- scénarios critiques ;
- critères d'acceptation minimum.

### Diagnostics possibles

```text
MISSING_ACCEPTANCE_CRITERIA
UNRESOLVED_REQUIREMENT_REFERENCE
CONSTRAINT_CONFLICT
```

### Transitions

```text
SPECIFIED -> DESIGNED
SPECIFIED -> PROPOSED
SPECIFIED -> ABANDONED
```

Le retour vers `PROPOSED` signifie que la spécification n'est plus considérée suffisamment stable.

---

## 6. `DESIGNED`

### Signification

Les décisions de conception nécessaires à l'implémentation ont été prises ou les raisons de ne pas nécessiter de design explicite sont établies.

### Attentes candidates

- décisions structurantes ;
- alternatives importantes si nécessaires ;
- impacts architecturaux ;
- contraintes techniques ;
- références vers ADR lorsque pertinentes.

### Transitions

```text
DESIGNED -> PLANNED
DESIGNED -> SPECIFIED
DESIGNED -> ABANDONED
```

Un changement trivial peut éventuellement passer de `SPECIFIED` à `PLANNED` si une règle explicite indique qu'aucun design séparé n'est requis. La politique exacte sera définie avant acceptation.

---

## 7. `PLANNED`

### Signification

Le travail nécessaire à l'implémentation est découpé à un niveau suffisant pour commencer l'exécution.

### Attentes

- tâches d'implémentation ou plan équivalent ;
- dépendances critiques ;
- critères d'acceptation toujours reliés ;
- bloqueurs connus.

### Transitions

```text
PLANNED -> IMPLEMENTING
PLANNED -> DESIGNED
PLANNED -> ABANDONED
```

---

## 8. `IMPLEMENTING`

### Signification

La réalisation a commencé.

MORPHEUS ne déduit pas cet état simplement parce qu'une branche Git existe. La transition provient d'une source explicite ou d'une intégration autorisée.

### Transitions

```text
IMPLEMENTING -> VERIFYING
IMPLEMENTING -> PLANNED
IMPLEMENTING -> ABANDONED
```

Le retour vers `PLANNED` représente une interruption ou un besoin de replanning substantiel.

---

## 9. `VERIFYING`

### Signification

L'implémentation déclarée est suffisamment avancée pour être confrontée aux critères d'acceptation.

### Attentes

- critères disponibles ;
- preuves de vérification en cours ou accessibles ;
- liens vers tests/reviews/mesures lorsque disponibles.

### Transitions

```text
VERIFYING -> COMPLETED
VERIFYING -> IMPLEMENTING
VERIFYING -> ABANDONED
```

### Condition vers `COMPLETED`

Par défaut, aucun critère bloquant ne doit rester en état :

```text
FAILED
NOT_VERIFIED
UNKNOWN
```

sauf dérogation explicitement représentée.

---

## 10. `COMPLETED`

### Signification

Le changement est considéré réalisé et vérifié selon les règles applicables.

### Important

`COMPLETED` ne signifie pas nécessairement que les spécifications proposées ont déjà été promues dans l'état `CURRENT` par tous les providers.

La promotion de l'état métier et l'archivage de la source peuvent être des opérations distinctes.

### Transitions

```text
COMPLETED -> ARCHIVED
COMPLETED -> VERIFYING  (réouverture exceptionnelle)
```

Une réouverture doit produire une provenance/audit explicite.

---

## 11. `ARCHIVED`

### Signification

Le changement terminé a été déplacé dans l'historique du provider ou marqué comme archive logique.

Il reste interrogeable selon la politique de rétention.

### État terminal nominal

Aucune transition standard n'est prévue.

Une restauration exceptionnelle peut être supportée ultérieurement comme opération explicite de provider sans modifier l'historique existant.

---

## 12. `ABANDONED`

### Signification

La proposition ne doit pas être réalisée dans son état actuel.

Raisons candidates :

```text
REJECTED
OBSOLETE
DUPLICATE
NOT_FEASIBLE
NO_LONGER_NEEDED
SUPERSEDED_BY_OTHER_CHANGE
UNKNOWN
```

### Règle

L'abandon doit conserver :

- la raison ;
- la provenance ;
- les liens historiques ;
- le changement qui le remplace éventuellement.

### Réouverture

Une politique future peut autoriser :

```text
ABANDONED -> PROPOSED
```

mais uniquement comme transition explicite et auditée.

---

## 13. Transitions autorisées — matrice candidate

| Depuis | Vers | Nominal | Condition principale |
|---|---|---:|---|
| DRAFT | PROPOSED | oui | intention formulée |
| DRAFT | ABANDONED | oui | abandon explicite |
| PROPOSED | SPECIFIED | oui | exigences suffisantes |
| PROPOSED | DRAFT | non | révision majeure |
| PROPOSED | ABANDONED | oui | abandon explicite |
| SPECIFIED | DESIGNED | oui | design nécessaire traité |
| SPECIFIED | PROPOSED | non | spécification invalidée |
| SPECIFIED | ABANDONED | oui | abandon explicite |
| DESIGNED | PLANNED | oui | solution planifiable |
| DESIGNED | SPECIFIED | non | exigences/design à revoir |
| DESIGNED | ABANDONED | oui | abandon explicite |
| PLANNED | IMPLEMENTING | oui | démarrage explicite |
| PLANNED | DESIGNED | non | replanning architectural |
| PLANNED | ABANDONED | oui | abandon explicite |
| IMPLEMENTING | VERIFYING | oui | réalisation prête à vérifier |
| IMPLEMENTING | PLANNED | non | replanning |
| IMPLEMENTING | ABANDONED | oui | abandon explicite |
| VERIFYING | COMPLETED | oui | critères satisfaits |
| VERIFYING | IMPLEMENTING | non | corrections nécessaires |
| VERIFYING | ABANDONED | oui | abandon explicite |
| COMPLETED | ARCHIVED | oui | archivage effectué |
| COMPLETED | VERIFYING | exceptionnel | réouverture auditée |

---

## 14. Provider states

Un provider peut posséder des états différents.

Exemple :

```text
provider status = "in-review"
```

Le provider mappe cet état vers un `ChangeLifecycleState` MORPHEUS et conserve la valeur source dans la provenance/métadonnée.

Si aucun mapping fiable n'existe :

- ne pas inventer ;
- conserver la valeur provider ;
- utiliser un état ou diagnostic compatible avec la stratégie de résolution.

---

## 15. Transitions vs observation

MORPHEUS peut fonctionner en lecture seule.

Dans ce cas, il **observe** le passage d'un état à un autre dans la source ; il ne l'ordonne pas.

Lorsqu'une capacité d'écriture sera activée, une transition demandée devra :

1. être valide selon le modèle MORPHEUS ;
2. être supportée par le provider ;
3. respecter les permissions ;
4. être écrite dans la source ;
5. être réingérée ou confirmée avant de devenir l'état observé.

---

## 16. Règles de validation

Les transitions peuvent être :

```text
ALLOWED
ALLOWED_WITH_WARNINGS
BLOCKED
UNKNOWN
```

Exemples de bloqueurs :

- critère d'acceptation manquant ;
- critère bloquant en échec ;
- lien obligatoire non résolu ;
- conflit de contrainte ;
- tâche critique incomplète.

Ces règles de qualité devront être configurables ou adaptées au type de projet lorsque nécessaire.

---

## 17. Représentation des événements

Pour l'explicabilité, il est souhaitable de pouvoir conserver :

```text
ChangeStateTransition
- changeId
- from
- to
- observedAt
- sourceRevision
- provenance
- reason?
```

Cette structure ne force pas l'adoption d'un event sourcing complet.

---

## 18. Invariants

1. le cycle de vie ne remplace pas `TemporalState` ;
2. le cycle de vie ne remplace pas `VerificationStatus` ;
3. un provider state est normalisé mais conservé comme provenance ;
4. une transition interdite ne doit pas être silencieusement acceptée ;
5. une réouverture doit être explicite ;
6. `COMPLETED` ne doit pas être déduit uniquement d'un pourcentage de tâches ;
7. `ARCHIVED` ne doit pas effacer l'historique ;
8. `ABANDONED` n'est pas équivalent à `ARCHIVED`.

---

## 19. Validation M0

Les fixtures D3 doivent couvrir :

- parcours nominal ;
- retour SPECIFIED -> PROPOSED ;
- VERIFYING -> IMPLEMENTING ;
- abandon à plusieurs étapes ;
- archivage ;
- état provider inconnu ;
- changement terminé avec critère non vérifié ;
- réouverture exceptionnelle.

---

## 20. Questions ouvertes

- faut-il conserver `DESIGNED` pour les changements triviaux ?
- `PROPOSED` et `DRAFT` sont-ils toujours distinguables avec OpenSpec ?
- faut-il un état `READY` entre PLANNED et IMPLEMENTING ?
- quels critères sont réellement bloquants ?
- quelle politique pour les changements concurrents ?
- la promotion dans `CURRENT` est-elle une transition distincte ou un événement de version ?

Ces questions doivent être résolues avant acceptation définitive de l'ADR associée.