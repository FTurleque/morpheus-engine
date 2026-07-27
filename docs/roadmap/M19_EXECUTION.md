# M19 — Production Hardening, Scale & Operability

Statut : **✅ VALIDÉ TECHNIQUEMENT — Issue #88 / PR #89 — NON MERGÉ**

Dernière mise à jour : 27 juillet 2026

Branche : `m19/production-hardening-scale-operability`  
Base fonctionnelle : M18 merge `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`  
Base réconciliée : `main@2853318cba7a067a430dbd719d619529fdf85edf`, PR #90 intégrée.
Historique M19 : 67 commits propres à M19 transplantés sur cette base, équivalence vérifiée par `git range-diff`.

## 1. Question de sortie

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

**Réponse : OUI**, sur le profil gelé `M19-LARGE-GATE-1`, avec preuves Windows et Linux réelles sur le SHA de code exact `dca27db969b426ad43941ccb8cee7e926efb931b`.

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

ADR-0085 : **Acceptée — M19**.

## 5. M19-S1 — Performance budgets & deterministic fixtures ✅

Contrats :

```text
same seed -> same logical fixture manifest
fixture generation time excluded from application latency
budgets immutable after optimization starts unless contract change is explicit
```

Livrables :

- [x] budgets figés ;
- [x] ADR-0085 acceptée après preuve ;
- [x] deterministic large fixture generator ;
- [x] fixture manifest checksum contract ;
- [x] benchmark harness ;
- [x] M19 performance gate tests ;
- [x] mesures du gate final exact-head.

## 6. M19-S2 — Scale sync/query ✅

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

- [x] benchmark current paths ;
- [x] preserve deterministic semantics in scale gates ;
- [x] query/store paths exercised at frozen scale ;
- [x] size/growth measurement gates implemented ;
- [x] final measured results on the exact code SHA.

## 7. M19-S3 — Robustness / recovery / concurrency ✅

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

- [x] candidate recovery ;
- [x] interrupted sync tests ;
- [x] DB lock timeout/diagnostic ;
- [x] concurrent reader/command tests ;
- [x] migration/rebuild tests ;
- [x] recovery wired in CLI, API and MCP composition roots.

## 8. M19-S4 — Local-first observability ✅

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

- [x] operational event contract ;
- [x] local structured sink ;
- [x] metrics snapshot ;
- [x] HTTP readiness and metrics routes with real local dependency probe ;
- [x] sync/provider/composition/external timing instrumentation ;
- [x] bounded-cardinality and transport contract tests.

## 9. M19-S5 — Local security ✅

Contrats :

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

- [x] redactor ;
- [x] ignored-source policy ;
- [x] symlink tests ;
- [x] all public SQLite entry points hardened without destructive parent ACL rewrite ;
- [x] SQLite PERSIST journal owner-only, WAL/SHM absence and PRAGMA contract ;
- [x] tests proving safe defaults.

## 10. M19-S6 — Cross-platform reproducibility ✅

- [x] Windows validator implemented ;
- [x] Linux validator implemented ;
- [x] platform/environment manifest in each validator ;
- [x] Windows validator proof on `dca27db969b426ad43941ccb8cee7e926efb931b` ;
- [x] Linux validator proof on the same SHA from a clean ext4 clone ;
- [x] Windows and Linux environments recorded separately.

## 11. M19-S7 — Final gate ✅

Expected files :

```text
scripts/validate-m19.ps1
scripts/validate-m19.sh
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
exact-head workspace stability
separate Windows/Linux evidence
```

La source de vérité est le Maven Wrapper et l'exécution locale reproductible des validateurs. GitHub Actions n'est pas une preuve autoritative M19.

PR becomes Ready only after the final gate is green on the exact code SHA. Post-gate commits, if any, must be documentary only and explicitly audited.

Final proof:

```text
Code SHA                dca27db969b426ad43941ccb8cee7e926efb931b
Windows                 PASS
Linux ext4 / WSL2       PASS
Tests                   449/449 PASS, 0 failure, 0 error, 0 skipped
Architecture            178/178 PASS
Reactor                 14/14 SUCCESS, BUILD SUCCESS
Budgets                 PASS, frozen thresholds unchanged
Packaging/smokes        PASS Windows + Linux
Packaged startup p95    159.6 ms Windows / 290.6 ms Linux
SQLite final size       251,360,832 B Windows / 251,687,960 B Linux
SQLite growth           50,811,080 B Windows / 50,958,464 B Linux
SQLite reopen p95       7 ms Windows / 2 ms Linux
```

Les résultats détaillés et la chronologie des tentatives sont enregistrés dans `docs/validation/VALIDATION_M19.md`. L'essai WSL initial sur le montage `9p` n'est pas compté comme preuve ; seul le gate complet ext4 au SHA exact est un PASS Linux.

## 12. Non-negotiable boundaries

```text
MORPHEUS = specification facts + intent + lifecycle rules + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection + ranking + fusion + compression
JARVIS   = sequencing + orchestration + action choice
```

All M0→M18 identity, temporal, acceptance, constraint, lifecycle, idempotency and multi-provider invariants remain active.

## 13. Merge governance

M19 est techniquement terminé. La PR #89 est destinée à devenir Ready après le contrôle final du diff, mais reste non mergée et l'issue #88 reste ouverte jusqu'à l'intégration.

**No merge of M19 without explicit user authorization.**
