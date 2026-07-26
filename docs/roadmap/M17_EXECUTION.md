# M17 — Controlled Lifecycle & Write Operations

Statut : **🚧 EN COURS — S1→S6 codées ; gate réel S7 restant**

Dernière mise à jour : 26 juillet 2026

Issue : **#80**  
Branche : `m17/controlled-lifecycle-write-operations`  
PR : **#81 — Draft**

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

L'audit append-only constitue la preuve persistée de l'application de la mutation : identité, clé d'idempotency, empreinte de commande, from/to, révisions, acteur, provider autorisant l'écriture, raison et timestamp. M17 n'invente pas une nouvelle relation métier `Audit -> Evidence` qui ne serait pas portée par une source normalisée.

## 5. Slices

### M17-S1 — Domaine / contrats ✅ CODED
- ✅ `ChangeLifecycleMutationId` + `ChangeLifecycleIdempotencyKey` ;
- ✅ `ChangeLifecycleRevision` monotone ;
- ✅ `ChangeLifecycleMutationCommand` explicite ;
- ✅ résultat `APPLIED | ALREADY_APPLIED | CONFLICT | NOT_AUTHORIZED | REQUIRES_CONFIRMATION | REJECTED` ;
- ✅ `ChangeLifecycleMutationAuditRecord` structuré ;
- ✅ projection JSON-safe `ChangeLifecycleMutationResultView`.

### M17-S2 — Store / CAS ✅ CODED
- ✅ `ChangeLifecycleMutationStore` ;
- ✅ état initial virtuel `DRAFT / revision 0` ;
- ✅ Memory ;
- ✅ SQLite migration V011 ;
- ✅ création initiale + compare-and-set ;
- ✅ `UPDATE ... WHERE revision = expected AND state = expected` ;
- ✅ stale expected revision rejetée ;
- ✅ état lifecycle opérationnel séparé des snapshots.

### M17-S3 — Idempotency / audit ✅ CODED
- ✅ idempotency key unique par projet ;
- ✅ empreinte logique SHA-256 indépendante du `mutationId` et du timestamp de retry ;
- ✅ même commande => `ALREADY_APPLIED` sans second audit ;
- ✅ réutilisation incohérente de key => `CONFLICT` ;
- ✅ mutation-id collision => `CONFLICT` ;
- ✅ audit append-only persisté avec la même transaction que l'état.

### M17-S4 — Autorisation / confirmation ✅ CODED
- ✅ `WRITE_CHANGE` distinct des capacités read ;
- ✅ `RegisteredProjectWriteCapabilityResolver` sur le root réellement enregistré ;
- ✅ zéro provider write => deny ;
- ✅ plusieurs providers write => deny ambigu, aucune sélection silencieuse ;
- ✅ OpenSpec reste read-only ;
- ✅ Synthetic fournit la preuve positive `WRITE_CHANGE` ;
- ✅ confirmation explicite requise par `ChangeLifecycleMutationPolicy.strict()` ;
- ✅ adapters historiques deny-by-default lorsqu'aucun resolver write n'est injecté.

### M17-S5 — Application contrôlée ✅ CODED
- ✅ ordre des guards : idempotency → capability → confirmation → revision → evaluation → CAS ;
- ✅ réutilise `ChangeTransitionEvaluationService` M14-M16 avant mutation ;
- ✅ `ALLOWED` seul ne mute jamais ;
- ✅ toute décision différente de `ALLOWED` => `REJECTED`, sans audit ;
- ✅ abandonment reason reste explicite ;
- ✅ reason de décision + acteur + provider conservés dans l'audit ;
- ✅ une retry idempotente est résolue avant contrôle stale/evaluation.

### M17-S6 — Surfaces ✅ CODED
- ✅ CLI write séparée : `lifecycle apply` ;
- ✅ CLI exige expected revision, idempotency key, actor et `--confirm` ;
- ✅ MCP : 20 tools read-only historiques conservés + 1 tool write séparé `apply_change_lifecycle_transition` ;
- ✅ HTTP : `POST .../lifecycle-transitions` séparé de `POST .../transition-check` ;
- ✅ OpenAPI **1.6.0** ;
- ✅ réponses exposent revision/audit/idempotency ;
- ✅ composition root injecte le resolver write ;
- ✅ README + docs CLI/MCP/API alignées ;
- ✅ packaging exige les classes M17 et smoke la présence de `lifecycle apply` sans exécuter d'écriture.

### M17-S7 — Gate 🚧
- ✅ tests domain/application écrits ;
- ✅ contrats Memory/SQLite écrits ;
- ✅ SQLite close/reopen écrit ;
- ✅ concurrence stale CAS écrite ;
- ✅ idempotent retry écrit ;
- ✅ read-only evaluation sans mutation écrite ;
- ✅ décision BLOCKED => mutation REJECTED sans audit écrite ;
- ✅ CLI/API/MCP STDIO écrits ;
- ✅ `validate-m17.cmd` + `scripts/validate-m17.ps1` ;
- ⏳ reactor Maven complet réel ;
- ⏳ correction de tout échec réel ;
- ⏳ packaging Windows + smokes réel ;
- ⏳ `VALIDATION_M17.md` avec SHA/compteurs exacts ;
- ⏳ ADR-0083 acceptée seulement après preuve ;
- ⏳ PR #81 Ready seulement après gate.

## 6. Gate M17

```text
read-only mode remains fully supported                     TESTS WRITTEN
write paths are opt-in                                     TESTS WRITTEN
ALLOWED evaluation never mutates by itself                 TEST WRITTEN
non-ALLOWED evaluation cannot be applied                   TEST WRITTEN
concurrent stale mutation rejected deterministically       TEST WRITTEN
idempotent retry does not duplicate audit/mutation         TEST WRITTEN
mutation audit survives restart                            TEST WRITTEN
no mutation without explicit WRITE_CHANGE capability       TESTS WRITTEN
MCP/API mutation surface separated from evaluation         TESTS WRITTEN
full Maven reactor PASS                                     NOT RUN
Windows packaging + smokes PASS                             NOT RUN
```

## 7. Validation

Aucun PASS final n'est revendiqué avant exécution réelle du Maven Wrapper et du packaging Windows sur le head courant.

```text
PR #81    reste Draft
ADR-0083  reste Proposée
M17       reste EN COURS
```

## 8. Gouvernance

La branche/PR M17 reste isolée de `main` jusqu'au gate complet. Aucun merge sans autorisation explicite distincte.
