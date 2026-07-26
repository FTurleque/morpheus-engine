# ADR-0083 — Mutations lifecycle contrôlées, CAS, idempotency et audit

- Statut : **Proposée — M17**
- Date : 26 juillet 2026
- Dépend de : ADR-0008, ADR-0011, ADR-0032, ADR-0033, ADR-0050, ADR-0077, ADR-0078, ADR-0079, ADR-0082
- Portée : M17 — Controlled Lifecycle & Write Operations

## Contexte

M14 à M16 fournissent des décisions lifecycle read-only et explicables. Elles ne mutent rien. M17 doit introduire un premier chemin d'écriture sans transformer une décision `ALLOWED` en effet implicite.

Les snapshots publiés restent immuables. L'état lifecycle mutable est un état opérationnel séparé.

## Décision

### 1. Séparer évaluation et mutation

```text
evaluate transition -> read-only decision
apply transition    -> explicit mutation command
```

`ALLOWED != applied` est un invariant structurel.

### 2. État lifecycle opérationnel versionné

Chaque changement muté possède :

```text
projectId
changeId
ChangeLifecycle
revision >= 1
updatedAt
lastMutationId
```

La révision est monotone et sert au compare-and-set.

### 3. Commande explicite

Une commande porte au minimum :

```text
mutationId
idempotencyKey
projectId
changeId
expectedRevision
targetState
targetAbandonmentReason?
confirmation
actor
requestedAt
```

Aucune valeur n'est inventée par MORPHEUS.

### 4. Contrôle de concurrence

Une mutation n'est appliquée que si `expectedRevision` correspond à l'état courant.

```text
expected != current -> CONFLICT
```

Aucun last-write-wins silencieux.

### 5. Idempotency

Une `idempotencyKey` déjà appliquée avec la même empreinte logique retourne `ALREADY_APPLIED` et le résultat original sans nouvel audit ni nouvelle révision.

La même clé avec une commande différente est un conflit déterministe.

### 6. Capability write explicite

Le chemin d'écriture exige une capacité `WRITE_CHANGE` réellement observée pour le provider du projet.

```text
READ_CHANGES != WRITE_CHANGE
```

OpenSpec reste read-only tant que son adapter ne déclare pas explicitement cette capacité. Le provider Synthetic peut servir de preuve positive contrôlée.

### 7. Confirmation

La mutation exige une confirmation explicite quand la policy l'impose. L'absence de confirmation produit `REQUIRES_CONFIRMATION`, sans écriture ni audit d'application.

### 8. Validation métier avant écriture

Le service de mutation réutilise l'évaluation lifecycle/contraintes M16 :

```text
ALLOWED         -> éligible à mutation
BLOCKED         -> REJECTED
UNKNOWN         -> REJECTED
REQUIRES_INPUT  -> REJECTED
```

Une commande explicite reste nécessaire même lorsque la décision est `ALLOWED`.

### 9. Audit append-only

Chaque mutation réellement appliquée produit exactement un audit record contenant :

```text
mutationId
idempotencyKey
projectId
changeId
fromState
targetState
fromRevision
toRevision
actor
providerId
reason
appliedAt
```

L'audit survit au redémarrage SQLite.

### 10. Surfaces séparées

Les surfaces de mutation sont distinctes des surfaces d'évaluation M14-M16. Les outils/handlers read-only historiques restent read-only.

## Alternatives rejetées

### Muter depuis `transition-check`
Rejeté : confond décision et effet.

### Stocker lifecycle mutable dans le snapshot
Rejeté : casserait l'immuabilité des snapshots publiés.

### Last-write-wins
Rejeté : écrasement concurrent implicite.

### Capability write implicite depuis READ
Rejeté : `read capability != write capability`.

### Réessayer sans idempotency
Rejeté : risque de double mutation / double audit.

## Validation avant acceptation

ADR-0083 ne passe en **Acceptée — M17** qu'après preuve :

```text
ALLOWED evaluation alone does not mutate
explicit WRITE_CHANGE capability required
confirmation policy enforced
stale expected revision rejected
idempotent retry returns original result
idempotency-key mismatch rejected
Memory == SQLite
SQLite close/reopen preserves lifecycle + audit
read-only surfaces regressions PASS
mutation MCP/API surfaces separated
full Maven gate PASS
Windows packaging + smokes PASS
```