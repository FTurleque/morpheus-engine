# MORPHEUS — Roadmap post-M14

Statut : **ACTIVE — D0 et M15→M18 validés/intégrés ; M19 prochain jalon**

Dernière mise à jour : 26 juillet 2026

Cette roadmap prolonge la baseline **C0 à M14 validée et intégrée**. Elle ne réécrit pas les preuves historiques : elle décrit l’état courant du cycle post-M14 et les prochaines étapes.

La roadmap globale reste [`../governance/ROADMAP.md`](../governance/ROADMAP.md). La politique documentaire est [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

---

## 1. Baseline acquise

```text
C0 -> M14   ✅ validés et intégrés
D0          ✅ intégré

M15         ✅ validé / intégré
M15 merge   c37134439844cb088adff855c339a259bb908b6a
M15 tests   371/371 PASS
Architecture M15 157/157 PASS
Packaging M15 Windows + smokes PASS

M16         ✅ validé / intégré
M16 merge   97308005a63854c7cb08dc19cd3cdb02ac739404
M16 code    f349c5f4701665e649d985426d35b5e6a6060e32
M16 tests   393/393 PASS
Architecture M16 161/161 PASS
Packaging M16 Windows + smokes PASS

M17         ✅ validé / intégré
M17 merge   02bdb38669efc85af17343d15e689743362d2e12
M17 code    87d2c0238f90aeb17dab5fed04f1c83a1b548f15
M17 tests   410/410 PASS
Architecture M17 167/167 PASS
Packaging M17 Windows + smokes PASS

M18         ✅ validé / intégré — PR #86
M18 issue   #85 CLOSED / completed
M18 merge   30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 code    7e8caacff567f51354fcb88bd7505a6d135071c0
M18 tests   418/418 PASS
Architecture M18 170/170 PASS
Packaging M18 Windows + smokes + API health PASS
Portable ZIP M18 33,919,431 bytes
```

Capacités disponibles après M18 :

```text
Domain model
Persistence Memory / SQLite
TemporalState + lifecycle + snapshots + versions
Published history / comparison / logical rollback
Typed traceability
Business queries + compact context
Quality diagnostics
Incremental synchronization / freshness
Change analysis
AcceptanceCriterion + VerificationStatus + Evidence semantics
Acceptance verification coverage
Acceptance traceability
Explicit ConstraintApplicability / Severity / Satisfaction
Explicit ConstraintBlockingPolicy
Explainable ConstraintEvaluation
Lifecycle BLOCKING_CONSTRAINT decisions
Controlled lifecycle mutation command
WRITE_CHANGE capability negotiation
expected revision / CAS
idempotency key + duplicate suppression
audit append-only + SQLite reopen
OpenSpec real provider
Structured Markdown real provider
ProviderContribution provider-neutral
multi-provider deterministic composition
explicit precedence + preserved provenance
explicit composition conflicts
composition state Memory / SQLite V012
CLI / MCP / HTTP composition surfaces
CLI / MCP / HTTP controlled-write surfaces
CLI / MCP / HTTP acceptance + constraint-policy surfaces
MINOS optional integration
NEXUS optional integration
JARVIS orchestration boundary preserved
Portable Windows/Linux packaging capability
```

Frontières à préserver :

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts

MINOS    = code intelligence

NEXUS    = context selection
           + ranking
           + fusion
           + compression

JARVIS   = sequencing
           + orchestration
           + action choice
```

Invariants structurants :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot

PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE

Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion

UNKNOWN != FAILED
UNKNOWN != BLOCKED

applicable != blocking
warning != blocker
severity != blocking policy
constraint text != executable policy

transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied

published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit

provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced

optional provider absence != project failure when optional
optional engine absence != MORPHEUS failure

live external observation != published snapshot mutation
NEXUS ContextBundle != KnowledgeSnapshot persistence
MORPHEUS rules != JARVIS action sequencing
```

---

## 2. Progression post-M14

```text
D0   Documentation reconciliation                   ✅
M15  Acceptance Criteria, Verification & Evidence   ✅
M16  Constraint Semantics & Policy Enforcement      ✅
M17  Controlled Lifecycle & Write Operations        ✅
M18  Real Providers & Multi-Provider Composition    ✅
M19  Production Hardening, Scale & Operability      ⏭
M20  Release Engineering / Installation PROD / 1.0  ⏳
```

La boucle cible reste :

```text
INTENTION
   ↓
REQUIREMENT
   ↓
CHANGE
   ↓
IMPLEMENTATION
   ↓
VERIFICATION
   ↓
EVIDENCE
   ↓
SATISFACTION EXPLICABLE
```

---

# D0 — Réconciliation documentaire post-M14

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #75**

Preuve : [`../validation/VALIDATION_D0.md`](../validation/VALIDATION_D0.md).  
Issue : **#74**. PR : **#75**. Merge : `ec75d3963422d6281f2904c5ebd547124db92ad6`.

---

# M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #77**

Question de sortie :

> MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?

**Réponse : OUI.**

```text
TOTAL 371/371 PASS
Architecture 157/157 PASS
Packaging Windows + smokes PASS
```

Head de code validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`.  
Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md).  
Plan : [`M15_EXECUTION.md`](M15_EXECUTION.md).  
ADR : ADR-0081 **Acceptée — M15**.  
Merge : `c37134439844cb088adff855c339a259bb908b6a`.

---

# M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #79**

Question de sortie :

> MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?

**Réponse : OUI.**

```text
TOTAL 393/393 PASS
Architecture 161/161 PASS
Packaging Windows + smokes PASS
```

Head de code validé : `f349c5f4701665e649d985426d35b5e6a6060e32`.  
Preuve : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).  
Plan : [`M16_EXECUTION.md`](M16_EXECUTION.md).  
ADR : ADR-0082 **Acceptée — M16**.  
Merge : `97308005a63854c7cb08dc19cd3cdb02ac739404`.

---

# M17 — Controlled Lifecycle & Write Operations

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #81**

Question de sortie :

> MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?

**Réponse : OUI.**

```text
TOTAL 410/410 PASS
Architecture 167/167 PASS
Failures / Errors / Skipped = 0 / 0 / 0
Packaging Windows + smokes PASS
Portable ZIP 33,839,272 bytes
```

Head de code validé : `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`.  
Preuve : [`../validation/VALIDATION_M17.md`](../validation/VALIDATION_M17.md).  
Plan : [`M17_EXECUTION.md`](M17_EXECUTION.md).  
ADR : ADR-0083 **Acceptée — M17**.  
Merge : `02bdb38669efc85af17343d15e689743362d2e12`.

---

# M18 — Real Providers & Multi-Provider Composition

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #86**

Question de sortie :

> MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?

**Réponse : OUI.**

Architecture livrée :

```text
OpenSpec réel
+
Structured Markdown réel
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
        ↓
precedence explicite
provenance conservée
conflits explicites
        ↓
Memory / SQLite V012
        ↓
CLI / MCP / HTTP
```

Surfaces :

```text
CLI  composition sync | status | conflicts
MCP  get_composition_status | list_composition_conflicts
HTTP GET /api/v1/projects/{projectId}/composition
HTTP GET /api/v1/projects/{projectId}/composition/conflicts
OpenAPI 1.7.0
SQLite V012
```

Gate :

```text
TOTAL 418/418 PASS
Architecture 170/170 PASS
Failures / Errors / Skipped = 0 / 0 / 0
14/14 modules Maven SUCCESS
BUILD SUCCESS
Packaging Windows PASS
Packaged smokes PASS
API health smoke PASS
Portable ZIP 33,919,431 bytes
```

Head de code validé : `7e8caacff567f51354fcb88bd7505a6d135071c0`.  
Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).  
Plan : [`M18_EXECUTION.md`](M18_EXECUTION.md).  
ADR : ADR-0084 **Acceptée — M18**.  
Issue : **#85 CLOSED / completed**.  
PR : **#86 MERGED**.  
Merge : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

Le SHA de code gated et le merge commit restent deux faits distincts : le gate a exécuté `7e8caac...`; la PR a ensuite été fusionnée en `30f11ac...` après trois commits documentaires de clôture.

---

# M19 — Production Hardening, Scale & Operability

Statut : **⏭ PROCHAIN JALON**

## Question de sortie

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

Cette question est la porte de sortie de M19 et ne doit pas être remplacée par une simple réussite de build.

## Objectif

Transformer les garanties fonctionnelles M0→M18 en garanties d'exploitation mesurées, reproductibles et testées, sans déplacer les responsabilités de MINOS, NEXUS ou JARVIS dans MORPHEUS.

## Règle préalable sur les budgets

Les budgets et seuils de M19 doivent être **définis et versionnés avant l'implémentation de toute optimisation**.

```text
mesure initiale
    !=
choix opportuniste du seuil
```

Un seuil ne doit jamais être choisi après avoir observé une implémentation optimisée afin de faire passer le gate.

Les budgets doivent préciser au minimum : fixture, machine/environnement de référence, commande de benchmark, warmup, nombre d'itérations, métrique, percentile ou agrégat retenu, seuil et marge de reproductibilité.

## M19-S1 — Performance budgets & fixtures

À définir avant optimisation :

```text
large repository fixtures
large requirement sets
large traceability graphs
incremental sync benchmarks
query latency budgets
startup time
memory budget
SQLite size / growth
history retention cost
```

Livrables attendus :

- fixtures déterministes et générables ;
- manifestes de taille/volume ;
- budgets versionnés ;
- harness de benchmark reproductible ;
- distinction claire entre benchmark informatif et gate bloquant.

## M19-S2 — Synchronisation et requêtes à l'échelle

Contrats :

```text
same input + same baseline -> same published result
incremental sync does not silently become semantically different from full rebuild
query ordering remains deterministic
large graph traversal remains bounded
```

Mesurer et, seulement après les budgets, optimiser :

- full sync ;
- incremental sync ;
- requirement search ;
- traceability traversal ;
- composition status/conflicts ;
- startup/open database ;
- SQLite growth and retention.

## M19-S3 — Robustesse transactionnelle et recovery

Couvrir par contrats/tests réels :

```text
corrupt / partial sources
interrupted sync
failed candidate recovery
concurrent readers
concurrent commands
locked database behavior
migration compatibility
rebuild from sources
```

Invariant critique :

```text
failure during BUILDING / VALIDATING / persistence
    -> no partially constructed ACTIVE state is observable
    -> previous valid ACTIVE remains authoritative
```

Les tests doivent distinguer échec provider, échec de validation, échec SQLite, interruption et contention.

## M19-S4 — Observabilité locale-first

Évaluer puis compléter :

```text
structured logs
stable diagnostics
health / readiness semantics
operational counters
sync timing
provider timing
external integration timing
```

Principes :

```text
local-first
no mandatory external telemetry
no secret leakage
stable machine-readable diagnostic codes where operationally relevant
```

Health et readiness doivent être sémantiquement distincts si l'application peut être vivante mais temporairement non prête à servir un état cohérent.

## M19-S5 — Sécurité locale vérifiable

Couvrir :

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

Chaque règle structurante doit avoir un contrat ou un test. Les règles purement documentaires ne suffisent pas.

## M19-S6 — Cross-platform reproducibility

Le gate final doit distinguer les preuves :

```text
Windows proof = réelle ou absente
Linux proof   = réelle ou absente
```

Aucune preuve Linux ne peut être inférée depuis Windows. Si GitHub Actions Linux est indisponible, la preuve Linux reste explicitement manquante et M19 ne doit pas être présenté comme validé cross-platform.

## M19-S7 — Gate final

Validateur attendu :

```text
scripts/validate-m19.ps1
validate-m19.cmd
```

Le validateur Windows doit automatiser :

```text
workspace / SHA
toolchain
clean test reactor complet
benchmarks/gates M19 reproductibles
tests de robustesse
packaging Windows
smokes
résumé PASS/FAIL
failure-summary automatique
```

Porte finale :

```text
performance budgets documented before optimization
large-fixture gates reproducible
query and sync budgets pass on reference environment
no partial ACTIVE exposure under failure
migration/recovery scenarios validated
concurrency/locking behavior explicit and tested
operational diagnostics documented and tested
local security rules verified
Windows proof real and recorded
Linux proof real and recorded, or explicitly declared missing
```

`VALIDATION_M19.md` doit enregistrer le SHA de code réellement testé et séparer les commits documentaires post-gate comme pour M18.

---

# M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **PLANIFIÉ**

M20 traite l'installation produit et la release 1.0 après M19 :

```text
Windows per-user installer
%LOCALAPPDATA%\Programs\MORPHEUS
separate application data
optional PATH integration
checksums
GitHub Releases
upgrade / uninstall
portable ZIP retained
Linux distribution/release path
```

M20 ne doit pas absorber les responsabilités de M19 : performances, capacité, recovery, observabilité et sécurité locale doivent être prouvés avant la finalisation 1.0.

---

## Règle de clôture de jalon

Après un merge autorisé :

```text
verify merge commit
close issue
reconcile active roadmaps immediately
update validation/ADR indexes
remove obsolete milestone branches when appropriate
verify main is the authoritative baseline
```

Aucun jalon déjà intégré ne doit rester annoncé comme « prochain » dans la documentation active.
