# M17 — Controlled Lifecycle & Write Operations

Statut : **🚧 EN COURS — cadrage et implémentation**

Dernière mise à jour : 26 juillet 2026

Issue : **#80**  
Branche : `m17/controlled-lifecycle-write-operations`

## 1. Question de sortie

> **MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?**

## 2. Baseline d'entrée

```text
C0 -> M16       ✅ validés / intégrés
M16 merge       97308005a63854c7cb08dc19cd3cdb02ac739404
M16             393/393 PASS
Architecture    161/161 PASS
Packaging Win   PASS
```

## 3. Invariants

```text
read capability != write capability
ALLOWED != applied
JARVIS owns sequencing
MORPHEUS owns state invariants
no implicit overwrite
no mutation without explicit provider capability
no mutation without conflict policy
no mutation without explicit confirmation when required
idempotent retry != duplicate mutation
audit survives restart
published snapshot != operational lifecycle state
```

## 4. Architecture cible

```text
caller / JARVIS chooses action
        ↓
read-only transition evaluation (M14-M16)
        ↓
ControlledChangeLifecycleMutationCommand
        ↓
write-capability authorization
        ↓
confirmation policy
        ↓
expected revision / CAS
        ↓
ChangeLifecycleMutationStore
        ↓ atomic state + audit + idempotency
new operational lifecycle state
```

L'état lifecycle mutable est séparé des snapshots publiés. Une transition ne réécrit jamais `KnowledgeSnapshot` ni `SnapshotBusinessContent`.

## 5. Slices

### M17-S1 — Domaine / contrats
- [ ] mutation id + idempotency key ;
- [ ] revision monotone ;
- [ ] commande de transition contrôlée ;
- [ ] résultat `APPLIED | ALREADY_APPLIED | CONFLICT | NOT_AUTHORIZED | REQUIRES_CONFIRMATION | REJECTED` ;
- [ ] audit record structuré.

### M17-S2 — Store / CAS
- [ ] `ChangeLifecycleMutationStore` ;
- [ ] Memory ;
- [ ] SQLite migration V011 ;
- [ ] création initiale + compare-and-set ;
- [ ] stale expected revision rejetée ;
- [ ] reopen identique.

### M17-S3 — Idempotency / audit
- [ ] idempotency key unique par projet ;
- [ ] même commande => `ALREADY_APPLIED` sans second audit ;
- [ ] réutilisation incohérente de key => conflit ;
- [ ] audit append-only persistant.

### M17-S4 — Autorisation / confirmation
- [ ] `WRITE_CHANGE` distinct des capacités read ;
- [ ] capability réellement observée sur provider projet ;
- [ ] OpenSpec reste read-only ;
- [ ] Synthetic fournit la preuve positive ;
- [ ] confirmation explicite requise par policy de mutation.

### M17-S5 — Application contrôlée
- [ ] réutilise l'évaluation M16 avant mutation ;
- [ ] `ALLOWED` seul ne mute jamais ;
- [ ] `BLOCKED/UNKNOWN/REQUIRES_INPUT` ne sont jamais appliqués ;
- [ ] abandonment reason explicite ;
- [ ] audit reason/evidence conservés.

### M17-S6 — Surfaces
- [ ] CLI mutation séparée de `transition-check` ;
- [ ] MCP mutation tool séparé du catalogue read-only historique ;
- [ ] HTTP mutation endpoint séparé du POST d'évaluation ;
- [ ] OpenAPI versionnée ;
- [ ] réponses exposent revision/audit/idempotency.

### M17-S7 — Gate
- [ ] tests domain/application/store ;
- [ ] Memory == SQLite ;
- [ ] SQLite close/reopen ;
- [ ] concurrence stale CAS ;
- [ ] idempotent retry ;
- [ ] read-only regression ;
- [ ] CLI/MCP/HTTP ;
- [ ] Maven reactor complet ;
- [ ] packaging Windows + smokes ;
- [ ] `VALIDATION_M17.md` ;
- [ ] ADR M17 acceptée après preuve ;
- [ ] PR Ready seulement après gate.

## 6. Gate M17

```text
read-only mode remains fully supported                     NOT RUN
write paths are opt-in                                     NOT RUN
ALLOWED evaluation never mutates by itself                 NOT RUN
concurrent stale mutation rejected deterministically       NOT RUN
idempotent retry does not duplicate audit/mutation         NOT RUN
mutation audit survives restart                            NOT RUN
no mutation without explicit WRITE_CHANGE capability       NOT RUN
MCP/API mutation surface separated from evaluation         NOT RUN
full Maven reactor PASS                                     NOT RUN
Windows packaging + smokes PASS                             NOT RUN
```

## 7. Gouvernance

ADR dépendante d'une preuve reste Proposée. La PR M17 reste Draft jusqu'au gate complet. Aucun merge sans autorisation explicite distincte.