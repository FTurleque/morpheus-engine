# Feuille de route — MORPHEUS

Statut : **C0 à M18 + D0 validés et intégrés — M19 prochain jalon**

Dernière mise à jour : 26 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

La baseline C0→M18 est acquise. La suite officielle reste définie dans [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md). La politique documentaire est [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md).

## 1. Vue globale

| Jalon | Sujet | Statut | Preuve / porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | [`VALIDATION_C0.md`](../validation/VALIDATION_C0.md) |
| M0 | Faisabilité technique | ✅ VALIDÉ | [`VALIDATION_M0.md`](../validation/VALIDATION_M0.md) |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | [`VALIDATION_M1.md`](../validation/VALIDATION_M1.md), 42/42 |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | [`VALIDATION_M2.md`](../validation/VALIDATION_M2.md), 94/94 |
| M3 | Temporalité, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M3.md`](../validation/VALIDATION_M3.md), 147/147 |
| M4 | Traçabilité typée | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M4.md`](../validation/VALIDATION_M4.md), 189/189 |
| M5 | Requêtes et contexte compact | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M5.md`](../validation/VALIDATION_M5.md), 227/227 |
| M6 | Qualité, couverture et diagnostics | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M6.md`](../validation/VALIDATION_M6.md), 261/261 |
| M7 | Synchronisation incrémentale et fraîcheur | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M7.md`](../validation/VALIDATION_M7.md), 282/282 |
| M8 | Analyse des changements | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M8.md`](../validation/VALIDATION_M8.md), 289/289 |
| M9 | CLI stabilisée et distribution locale | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M9.md`](../validation/VALIDATION_M9.md), 298/298 Windows + Linux |
| M10 | Serveur MCP STDIO natif | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M10.md`](../validation/VALIDATION_M10.md), 307/307 |
| M11 | API HTTP headless | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M11.md`](../validation/VALIDATION_M11.md), 314/314 |
| M12 | MINOS optionnel / intention → code | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M12.md`](../validation/VALIDATION_M12.md), 331/331 |
| M13 | NEXUS optionnel / intention → contexte technique | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M13.md`](../validation/VALIDATION_M13.md), 346/346 |
| M14 | JARVIS / contrat d'orchestration read-only | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M14.md`](../validation/VALIDATION_M14.md), 357/357 + JARVIS 536 tests |
| D0 | Réconciliation documentaire post-M14 | ✅ VALIDÉ / INTÉGRÉ — PR #75 | [`VALIDATION_D0.md`](../validation/VALIDATION_D0.md) |
| M15 | Acceptance Criteria, Verification & Evidence | ✅ VALIDÉ / INTÉGRÉ — PR #77 | [`VALIDATION_M15.md`](../validation/VALIDATION_M15.md), 371/371 |
| M16 | Constraint Semantics & Policy Enforcement | ✅ VALIDÉ / INTÉGRÉ — PR #79 | [`VALIDATION_M16.md`](../validation/VALIDATION_M16.md), 393/393 |
| M17 | Controlled Lifecycle & Write Operations | ✅ VALIDÉ / INTÉGRÉ — PR #81 | [`VALIDATION_M17.md`](../validation/VALIDATION_M17.md), 410/410 |
| **M18** | **Real Providers & Multi-Provider Composition** | **✅ VALIDÉ / INTÉGRÉ — PR #86** | [`VALIDATION_M18.md`](../validation/VALIDATION_M18.md), 418/418 + Architecture 170/170 + packaging PASS |
| **M19** | **Production Hardening, Scale & Operability** | **⏭ PROCHAIN** | performances, robustesse, observabilité et sécurité locale mesurées |
| M20 | Release Engineering, Installation PROD & MORPHEUS 1.0 | ⏳ PLANIFIÉ | setup Windows, releases, checksums, upgrade/uninstall, Linux |

Plans actifs et historiques :

- [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — trajectoire active D0→M20 ;
- [`M18_EXECUTION.md`](../roadmap/M18_EXECUTION.md) — dernier jalon intégré ;
- `M19_EXECUTION.md` — à créer au démarrage de M19.

## 2. Baseline M18 autoritative

```text
M18 issue       #85 CLOSED / completed
M18 PR          #86 MERGED
M18 code validé 7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge       30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests       418/418 PASS
Architecture    170/170 PASS
Failures        0
Errors          0
Skipped         0
Reactor         14/14 modules SUCCESS
Packaging Win   PASS
Packaged smokes PASS
API health      PASS
Portable ZIP    33,919,431 bytes
```

M18 apporte :

```text
OpenSpec réel + Structured Markdown réel
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

Surfaces M18 :

```text
CLI  composition sync | status | conflicts
MCP  get_composition_status | list_composition_conflicts
HTTP GET /api/v1/projects/{projectId}/composition
HTTP GET /api/v1/projects/{projectId}/composition/conflicts
OpenAPI 1.7.0
SQLite V012
```

ADR-0084 — **Acceptée — M18**.

## 3. Responsabilités et frontières

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

MORPHEUS rules != JARVIS action sequencing
```

## 4. Gates validés

```text
M2   94/94
M3  147/147
M4  189/189
M5  227/227
M6  261/261
M7  282/282
M8  289/289
M9  298/298 Windows + Linux
M10 307/307
M11 314/314
M12 331/331 | Architecture 153/153 | packaging PASS
M13 346/346 | Architecture 154/154 | packaging PASS
M14 357/357 | Architecture 160/160 | packaging PASS | JARVIS 536 tests BUILD SUCCESS
D0  documentation authority PASS | primary links PASS | historical evidence preserved
M15 371/371 | Architecture 157/157 | packaging + smokes PASS
M16 393/393 | Architecture 161/161 | packaging + smokes PASS
M17 410/410 | Architecture 167/167 | packaging + smokes PASS
M18 418/418 | Architecture 170/170 | packaging + smokes + API health PASS
```

La preuve Linux historique M9 reste distincte. Aucune preuve Linux M18 ou M19 n'est déduite de la preuve Windows.

## 5. M19 — Production Hardening, Scale & Operability ⏭

Question de sortie :

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

Cette question est la porte de sortie de M19.

Axes obligatoires :

### Performance et capacité

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

Les budgets et seuils sont définis **avant** toute optimisation. Les résultats doivent être reproductibles et documentés ; aucun seuil ne doit être choisi après observation des performances.

### Robustesse

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

Invariant de sortie : **aucun échec ne doit exposer un état `ACTIVE` partiellement construit**.

### Observabilité

```text
structured logs
stable diagnostics
health / readiness semantics
operational counters
sync timing
provider timing
external integration timing
```

L'observabilité reste local-first et n'introduit aucune télémétrie externe obligatoire.

### Sécurité locale

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

Chaque règle importante doit être vérifiable par contrat ou test.

### Gate M19

```text
performance budgets documented before optimization
large-fixture gates reproducible
no partial ACTIVE exposure under failure
migration/recovery scenarios validated
operational diagnostics documented and tested
security-local contracts tested
Windows proof real and recorded
Linux proof real and recorded, or explicitly marked missing
```

## 6. M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

M20 reste planifié après M19. Il porte l'installation produit, les releases, checksums, upgrade/uninstall, packaging et la préparation 1.0. Il ne doit pas absorber les obligations de mesure, robustesse et opérabilité de M19.

## 7. Règle de clôture documentaire

Après chaque merge de jalon autorisé :

1. vérifier le merge commit et fermer l'issue ;
2. réconcilier immédiatement `ROADMAP.md`, `POST_M14_EXECUTION.md` et les index actifs ;
3. mettre à jour les index validation/ADR sans falsifier les preuves historiques ;
4. supprimer les branches de jalon devenues obsolètes lorsque cela est autorisé ;
5. vérifier que `main` constitue la baseline active pertinente.

Aucun jalon intégré ne doit rester annoncé comme futur dans la documentation active.
