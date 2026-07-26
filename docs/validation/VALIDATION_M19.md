# VALIDATION M19 — Production Hardening, Scale & Operability

Statut : **PRÉ-GATE — AUCUNE VALIDATION FINALE ENREGISTRÉE**

Issue : #88  
Draft PR : #89  
Branche : `m19/production-hardening-scale-operability`

## 1. Règle de preuve

Ce document ne doit jamais transformer une intention de test, une inspection statique ou un résultat d'une autre plateforme en PASS.

La preuve finale M19 devra enregistrer séparément :

```text
SHA de code réellement testé
Windows gate réel
Linux gate réel, ou MISSING explicite
nombre de tests / failures / errors / skipped
architecture tests
benchmarks M19 et métriques observées
packaging/smokes
archive produite et taille
```

Tant que le validateur final n'a pas été exécuté sur le SHA final :

```text
Windows proof = MISSING
Linux proof   = MISSING
M19 result    = NOT VALIDATED
```

## 2. Baseline d'entrée M18

Cette section décrit la baseline autoritative déjà validée avant M19 :

```text
M18 issue       #85 CLOSED / completed
M18 PR          #86 MERGED
M18 code gated  7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge       30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests       418/418 PASS
Architecture    170/170 PASS
Packaging Win   PASS
```

Ces résultats ne constituent pas une preuve M19.

## 3. Budgets M19 figés avant optimisation

Source : `docs/roadmap/M19_PERFORMANCE_BUDGETS.md`  
Commit de gel : `fb3dc4741e1357ea69ae8e797d5cfa674b8f80d2`

```text
inventory scan p95              <= 20 s
incremental diff/plan p95       <= 2 s
full publish p95                <= 60 s
requirement search p95          <= 1,000 ms
trace traversal p95             <= 2,000 ms
composition status/conflicts    <= 1,000 ms
SQLite reopen p95               <= 2,000 ms
packaged startup p95            <= 5,000 ms
heap ceiling                    <= 768 MiB
SQLite 5-snapshot size          <= 512 MiB
retention incremental growth    <= 128 MiB
```

Aucun seuil ne peut être relevé après observation d'un échec simplement pour rendre le gate vert.

## 4. Gates implémentés — tests ciblés verts, gate final non encore prouvé

### Performance / capacité

```text
M19PerformanceGate
M19QueryPerformanceGate
M19TraceabilityPerformanceGate
M19CompositionPerformanceGate
M19FullPublishPerformanceGate
```

Ils couvrent :

```text
5,000 source files / >=10 MiB
50 changed files incremental
10,000 requirements SQLite
25,000 traceability links SQLite
10,000 provider observations
1,000 composition conflicts
5 retained published snapshots
SQLite size / retention growth / reopen
packaged startup via platform validator
```

### Robustesse

Contrats ajoutés :

```text
partial source scan -> incomplete / no publishable inventory
invalid normalized content -> failed candidate / previous ACTIVE preserved
valid rebuild after failed candidate
stale BUILDING/VALIDATING recovery
Memory + SQLite recovery persistence
bounded SQLite lock + stable DATABASE_LOCKED classification
concurrent successor activation -> one winner
concurrent readers -> previous or new complete ACTIVE only
pre-M18/V011-like schema -> V012 migration reapplied
all public SQLite adapters -> one hardened local connection factory
SQLite persistent journal owner-only; WAL/SHM absent; NORMAL locking / memory temp store
```

### Observabilité / sécurité locale

Contrats ajoutés :

```text
stable operational event codes
canonical structured event attributes
local JSON-lines sink
secret/path redaction before write
process-local counters and duration aggregates
source scan timing
provider/external timing execution helper
health != readiness
readiness performs a real local store operation
HTTP /health, /readiness and /metrics routes are wired and contract-tested
ignored directory policy
symlink non-following by default
owner-only POSIX/ACL hardening when supported
pre-existing user parent permissions are preserved
```

## 5. Validateurs

Windows :

```text
validate-m19.cmd
  -> scripts/validate-m19.ps1
```

Linux :

```text
scripts/validate-m19.sh
```

Le Maven Wrapper et les validateurs locaux sont les seules sources de preuve M19. La tentative `.github/workflows/m19-validation.yml` a été supprimée : GitHub Actions n'est ni requise ni autoritative pour ce jalon.

Le validateur Windows est la commande canonique de validation locale utilisateur. Il exécute :

```text
workspace / SHA
clean workspace + exact-head stability
reference environment manifest and eligibility
toolchain
mvnw.cmd clean test
complete M19 robustness/security/operability contracts
M19 performance gates under -Xmx768m
Windows portable packaging + smokes
packaged health/readiness/local metrics smokes
packaged launcher --json version warmup + 5 measures
Surefire and architecture totals
PASS/FAIL summary
failure-summary automatique
```

Le validateur Linux exécute le protocole logique équivalent. Un PASS Windows ne vaut jamais PASS Linux.

## 6. État de preuve actuel

```text
Final M19 code SHA     PENDING
Windows validation     NOT RUN / MISSING
Linux validation       NOT RUN / MISSING
Packaging M19          NOT PROVEN
Benchmarks M19         NOT RUN
Failures/errors        UNKNOWN UNTIL GATE
```

## 7. Conditions avant passage de la Draft PR en Ready

- tous les contrats M19 compilent et passent ;
- reactor complet vert ;
- budgets figés respectés sur environnement de référence ;
- robustesse et migration vertes ;
- packaging/smokes verts ;
- Windows proof enregistrée sur le SHA final ;
- Linux proof enregistrée si réellement obtenue, sinon manque explicitement documenté ;
- ADR-0085/0086/0087 acceptées seulement si leurs preuves existent ;
- tout commit post-gate est documentaire uniquement et son diff est audité.

## 8. Gouvernance

**Aucun merge M19 sans autorisation explicite de l'utilisateur.**
