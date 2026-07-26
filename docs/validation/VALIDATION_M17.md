# Validation M17 — Controlled Lifecycle & Write Operations

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #81 prête à intégrer**

Date : 26 juillet 2026

Issue : #80  
PR : #81  
Head de code validé : `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`

## Question de sortie

> MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?

**Réponse : OUI.**

M17 introduit un chemin de mutation lifecycle explicite et contrôlé, séparé des décisions read-only M14-M16 et des snapshots publiés.

## Invariants validés

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

## Modèle et contrôle validés

État initial opérationnel virtuel :

```text
state    = DRAFT
revision = 0
```

Ordre des garde-fous :

```text
idempotency
 -> WRITE_CHANGE capability
 -> confirmation
 -> expectedRevision / CAS
 -> transition evaluation M14-M16
 -> atomic state + audit
```

Résultats applicatifs :

```text
APPLIED
ALREADY_APPLIED
CONFLICT
NOT_AUTHORIZED
REQUIRES_CONFIRMATION
REJECTED
```

Une transition `ALLOWED` ne produit aucun effet sans commande de mutation explicite.

## Persistance / concurrence / audit

Validé :

- `ChangeLifecycleMutationStore` Memory et SQLite ;
- SQLite migration **V011** ;
- lifecycle opérationnel séparé de `KnowledgeSnapshot` ;
- compare-and-set sur revision/state ;
- stale writer rejeté sans last-write-wins ;
- idempotency key identique + même empreinte => `ALREADY_APPLIED` ;
- même key + commande différente => `CONFLICT` ;
- audit append-only persisté atomiquement avec l'état ;
- SQLite close/reopen conserve état et audit ;
- une cible `ABANDONED` exige structurellement une `ChangeAbandonmentReason`.

## Authorization / providers

`READ_CHANGES != WRITE_CHANGE` est validé.

OpenSpec reste read-only. Le provider Synthetic fournit la preuve positive `WRITE_CHANGE` dans les contrats de test. Le launcher officiel reste deny-by-default tant qu'aucun provider de production détecté n'annonce explicitement `WRITE_CHANGE`.

## Surfaces validées

CLI :

```text
lifecycle apply
```

MCP :

```text
20 tools read-only historiques
+ apply_change_lifecycle_transition (write explicite)
```

HTTP :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check       read-only
POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions  controlled write
```

OpenAPI : **1.6.0**.

## Gate Maven autoritatif

Commande exécutée par le validateur M17 :

```powershell
.\mvnw.cmd clean test
```

Head testé :

```text
87d2c0238f90aeb17dab5fed04f1c83a1b548f15
```

Résultats :

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

Le module Memory Store ne contient pas de tests propres et n'ajoute donc aucun cas au total.

## Packaging Windows

Le même validateur a ensuite exécuté le packaging portable et les smokes.

Preuves :

```text
MCP/API/MINOS/NEXUS/M14-M17 classes + V011 migration embedded: PASS
Packaged standalone optional-engines + M14 read-only + M17 controlled-write surface smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,839,272 bytes
```

L'archive contient MORPHEUS, son runtime Java, CLI/MCP/API, les adapters optionnels MINOS/NEXUS, le contrat M14 read-only et la surface de mutation contrôlée M17. MINOS, NEXUS et JARVIS ne sont pas embarqués ni requis.

## Environnement du gate

```text
Windows 10 amd64
OpenJDK 24.0.1
Apache Maven 3.9.16 via Maven Wrapper
Java compilation target: release 21
```

## Validateur reproductible

```powershell
.\validate-m17.cmd
```

Le validateur met à jour la branche, contrôle la toolchain, exécute le reactor complet, puis le packaging/smokes et produit un résumé automatique du premier échec éventuel.

## ADR

ADR-0083 — **Acceptée — M17** après preuve du présent gate.

## Conclusion

M17 est **VALIDÉ TECHNIQUEMENT** sur le head de code `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`.

Les commits de clôture postérieurs à ce SHA sont documentaires uniquement et ne modifient ni runtime, ni tests, ni migration, ni build. La PR #81 peut être intégrée.