# M19 — Production Hardening, Scale & Operability

Statut : **🚧 EN COURS — Issue #88**

Dernière mise à jour : 26 juillet 2026

Branche : `m19/production-hardening-scale-operability`  
Base fonctionnelle : M18 merge `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`  
Base documentaire : head réconcilié PR #87, en attente d'autorisation de merge.

## 1. Question de sortie

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

Cette question reste la porte de sortie de M19.

## 2. Méthode

Pour chaque slice :

```text
inspecter l'existant
-> définir le contrat
-> implémenter
-> ajouter les tests
-> auditer les invariants
-> mettre à jour issue/PR/roadmap
-> slice suivante
```

Aucun résultat Linux n'est inféré depuis Windows.

## 3. Baseline d'entrée

```text
M18 issue       #85 CLOSED / completed
M18 PR          #86 MERGED
M18 code gated  7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge       30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests       418/418 PASS
Architecture    170/170 PASS
Packaging Win   PASS
SQLite          V012
OpenAPI         1.7.0
```

## 4. Budgets figés avant optimisation

Source : [`M19_PERFORMANCE_BUDGETS.md`](M19_PERFORMANCE_BUDGETS.md).

Le commit de gel des budgets précède tout changement Java M19.

Profil :

```text
5,000 source files
10,000 requirements
2,000 changes
4,000 acceptance criteria
25,000 traceability links
10,000 provider observations
1,000 composition conflicts
5 published snapshots retained
```

Budgets principaux :

```text
inventory scan p95              <= 20 s
incremental diff/plan p95       <= 2 s
full publish p95                <= 60 s
requirement search p95          <= 1,000 ms
trace traversal p95             <= 2,000 ms
composition query p95           <= 1,000 ms
SQLite reopen p95               <= 2,000 ms
packaged startup p95            <= 5,000 ms
heap ceiling                    <= 768 MiB
SQLite 5-snapshot size          <= 512 MiB
retention incremental growth    <= 128 MiB
```

ADR-0085 : **Proposée — M19**.

## 5. M19-S1 — Performance budgets & deterministic fixtures 🚧

Contrats :

```text
same seed -> same logical fixture manifest
fixture generation time excluded from application latency
budgets immutable after optimization starts unless contract change is explicit
```

Livrables :

- [x] budgets figés ;
- [x] ADR-0085 proposée ;
- [ ] deterministic large fixture generator ;
- [ ] fixture manifest checksum contract ;
- [ ] benchmark harness ;
- [ ] M19 performance gate tests.

## 6. M19-S2 — Scale sync/query ⏳

Inspecté avant implémentation :

```text
LocalSourceInventoryScanner hashes every regular file
IncrementalSyncService computes deterministic diff/plan
RequirementQueryService currently materializes and filters the complete snapshot list
TraceabilityTraversalService performs bounded BFS with deterministic neighbor ordering
ProjectSnapshotImportService persists candidate content before final activation
```

Contrats :

```text
same input + baseline -> same published semantics
query ordering remains deterministic under optimization
incremental planning does not silently alter full-rebuild semantics
trace traversal remains depth-bounded
```

- [ ] benchmark current paths ;
- [ ] optimize only if a frozen budget requires it ;
- [ ] add index/query improvements without changing semantics ;
- [ ] size/growth measurements.

## 7. M19-S3 — Robustness / recovery / concurrency ⏳

Contrats obligatoires :

```text
corrupt / partial source -> explicit failure/diagnostic
interrupted candidate -> never ACTIVE
failed candidate -> previous ACTIVE remains authoritative
concurrent readers -> stable published view
concurrent commands -> explicit serialization/conflict behavior
locked SQLite -> bounded explicit failure
migration compatibility -> previous schema upgrades safely
rebuild from sources -> recover authoritative published state
```

- [ ] candidate recovery ;
- [ ] interrupted sync tests ;
- [ ] DB lock timeout/diagnostic ;
- [ ] concurrent reader/command tests ;
- [ ] migration/rebuild tests.

## 8. M19-S4 — Local-first observability ⏳

Cible :

```text
structured logs
stable operational diagnostic codes
health != readiness where applicable
operational counters
sync/provider/composition timing
external integration timing
no mandatory external telemetry
```

- [ ] operational event contract ;
- [ ] local structured sink ;
- [ ] metrics snapshot ;
- [ ] readiness endpoint/semantics ;
- [ ] timing instrumentation ;
- [ ] tests.

## 9. M19-S5 — Local security ⏳

Contrats :

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

- [ ] redactor ;
- [ ] ignored-source policy ;
- [ ] symlink tests ;
- [ ] DB/file permissions where platform supports it ;
- [ ] tests proving safe defaults.

## 10. M19-S6 — Cross-platform reproducibility ⏳

- [ ] Windows validator proof ;
- [ ] Linux workflow/validator proof ;
- [ ] platform/environment manifest ;
- [ ] explicit `MISSING` state if Linux proof unavailable.

## 11. M19-S7 — Final gate ⏳

Expected files :

```text
scripts/validate-m19.ps1
validate-m19.cmd
docs/validation/VALIDATION_M19.md
```

Validator responsibilities :

```text
workspace / SHA
toolchain
clean test full reactor
M19 reproducible performance gates
robustness tests
Windows packaging
packaged smokes
summary PASS/FAIL
first-failure summary
```

PR becomes Ready only after the final gate is green on the exact code SHA. Post-gate commits, if any, must be documentary only and explicitly audited.

## 12. Non-negotiable boundaries

```text
MORPHEUS = specification facts + intent + lifecycle rules + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection + ranking + fusion + compression
JARVIS   = sequencing + orchestration + action choice
```

All M0→M18 identity, temporal, acceptance, constraint, lifecycle, idempotency and multi-provider invariants remain active.

## 13. Merge governance

**No merge of M19 without explicit user authorization.**