# M17 — Controlled Lifecycle & Write Operations

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #81 prête à intégrer**

Dernière mise à jour : 26 juillet 2026

Issue : **#80**  
Branche : `m17/controlled-lifecycle-write-operations`  
PR : **#81 — Ready**  
Head de code validé : `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`

## 1. Question de sortie

> **MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?**

**Réponse : OUI.**

## 2. Baseline d'entrée

```text
C0 -> M16       ✅ validés / intégrés
M16 merge       97308005a63854c7cb08dc19cd3cdb02ac739404
M16             393/393 PASS
Architecture    161/161 PASS
Packaging Win   PASS
```

## 3. Invariants validés

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
idempotent retry != duplicate audit
audit survives restart
published snapshot != operational lifecycle state
ABANDONED mutation != missing abandonment reason
```

## 4. Architecture retenue

```text
caller / JARVIS chooses action
        ↓
read-only transition evaluation (M14-M16)
        ↓
ChangeLifecycleMutationCommand
        ↓
idempotency guard
        ↓
WRITE_CHANGE capability authorization
        ↓
confirmation policy
        ↓
expected revision / CAS
        ↓
M14-M16 transition must still be ALLOWED
        ↓
ChangeLifecycleMutationStore
        ↓ atomic state + audit + idempotency
new operational lifecycle state
```

L'état lifecycle mutable est séparé des snapshots publiés. Une transition ne réécrit jamais `KnowledgeSnapshot` ni `SnapshotBusinessContent`.

L'audit append-only constitue la preuve persistée de l'application : identité, clé d'idempotency, empreinte de commande, from/to, révisions, acteur, provider autorisant l'écriture, raison et timestamp.

## 5. Slices

### M17-S1 — Domaine / contrats ✅
- `ChangeLifecycleMutationId` + `ChangeLifecycleIdempotencyKey` ;
- `ChangeLifecycleRevision` monotone ;
- `ChangeLifecycleMutationCommand` explicite ;
- résultats `APPLIED | ALREADY_APPLIED | CONFLICT | NOT_AUTHORIZED | REQUIRES_CONFIRMATION | REJECTED` ;
- `ChangeLifecycleMutationAuditRecord` structuré ;
- projection JSON-safe `ChangeLifecycleMutationResultView`.

### M17-S2 — Store / CAS ✅
- `ChangeLifecycleMutationStore` ;
- état initial virtuel `DRAFT / revision 0` ;
- Memory + SQLite ;
- migration **V011** ;
- compare-and-set sur revision/state ;
- stale expected revision rejetée ;
- lifecycle opérationnel séparé des snapshots.

### M17-S3 — Idempotency / audit ✅
- idempotency key unique par projet ;
- empreinte logique stable ;
- même commande => `ALREADY_APPLIED` sans second audit ;
- key incohérente => `CONFLICT` ;
- mutation-id collision => `CONFLICT` ;
- audit append-only atomique avec l'état ;
- audit conservé après close/reopen SQLite.

### M17-S4 — Autorisation / confirmation ✅
- `WRITE_CHANGE` distinct des capacités read ;
- `RegisteredProjectWriteCapabilityResolver` ;
- zéro provider write => deny ;
- plusieurs providers write => deny ambigu ;
- OpenSpec reste read-only ;
- Synthetic fournit la preuve positive `WRITE_CHANGE` ;
- confirmation explicite requise par la policy stricte ;
- adapters historiques deny-by-default sans resolver write.

### M17-S5 — Application contrôlée ✅
- ordre des guards : idempotency → capability → confirmation → revision → evaluation → CAS ;
- réutilisation de `ChangeTransitionEvaluationService` ;
- `ALLOWED` seul ne mute jamais ;
- toute décision différente de `ALLOWED` => `REJECTED`, sans audit ;
- abandonment reason explicite ;
- actor/provider/reason persistés dans l'audit ;
- retry idempotente résolue avant stale/evaluation.

### M17-S6 — Surfaces ✅
- CLI : `lifecycle apply` ;
- MCP : 20 tools read-only + `apply_change_lifecycle_transition` write explicite ;
- HTTP : `POST .../lifecycle-transitions` distinct de `POST .../transition-check` ;
- OpenAPI **1.6.0** ;
- revision/audit/idempotency exposés ;
- composition root injecte le resolver write ;
- README + docs CLI/MCP/API alignées ;
- packaging exige classes M17 + migration V011 + surface CLI.

### M17-S7 — Gate ✅ VALIDÉ
- tests domain/application : PASS ;
- contrats Memory/SQLite : PASS ;
- SQLite close/reopen : PASS ;
- concurrence stale CAS : PASS ;
- idempotent retry : PASS ;
- read-only evaluation sans mutation : PASS ;
- décision non-ALLOWED => `REJECTED` sans audit : PASS ;
- CLI/API/MCP STDIO : PASS ;
- reactor Maven complet : **410/410 PASS** ;
- Architecture : **167/167 PASS** ;
- packaging Windows + smokes : **PASS** ;
- `VALIDATION_M17.md` créée ;
- ADR-0083 : **Acceptée — M17**.

## 6. Gate autoritatif

```text
Domain               40/40 PASS
Application        104/104 PASS
OpenSpec             26/26 PASS
Synthetic              7/7 PASS
SQLite                 7/7 PASS
MINOS Integration      8/8 PASS
NEXUS Integration      7/7 PASS
MCP                     5/5 PASS
API                   11/11 PASS
CLI                   28/28 PASS
Architecture        167/167 PASS
---------------------------------
TOTAL               410/410 PASS
Failures                  0
Errors                    0
Skipped                   0
BUILD SUCCESS
```

Packaging :

```text
M14-M17 classes + V011 embedded                         PASS
standalone optional engines + M17 controlled-write     PASS
API health                                              PASS
portable ZIP                                            PASS
```

Archive : `dist/morpheus-0.1.0-windows-x64.zip` — **33,839,272 bytes**.

Environnement : Windows 10 amd64, OpenJDK 24.0.1, Maven 3.9.16, target Java 21.

Preuve : [`../validation/VALIDATION_M17.md`](../validation/VALIDATION_M17.md).

## 7. Gouvernance

M17 est techniquement validé. Les commits postérieurs au SHA de code validé sont documentaires uniquement. La PR #81 peut être intégrée ; l'issue #80 est clôturée après merge.