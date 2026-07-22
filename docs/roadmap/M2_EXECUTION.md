# M2 — Plan d'exécution détaillé

Statut : **M2 VALIDÉE — 8 slices sur 8 ; M3 autorisée**

Dernière mise à jour : 22 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et conserve la vue opérationnelle finale de M2.

---

# 1. Position finale

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     ✅ validé
  S1   ✅ domaine courant
  S2   ✅ identité persistante
  S3   ✅ modèle de changement
  S4   ✅ requirement deltas
  S5   ✅ ExternalReference
  S6   ✅ lecture unifiée / partiel / diagnostics
  S7   ✅ second provider / anti-lock-in
  S8   ✅ validation finale / décision persistance
M3     🚀 autorisé
```

Progression :

```text
M2 : [████████████████████] 8 / 8 slices validés
```

Gate final :

```text
94/94 PASS
Failures = 0
Errors   = 0
Skipped  = 0
BUILD SUCCESS
```

---

# 2. Tableau de progression M2

| Slice | Résultat | Statut | PR | ADR | Gate |
|---|---|---|---|---|---|
| M2-S1 | domaine courant + provenance/evidence | ✅ VALIDÉ | #10 | ADR-0022 | 48/48 |
| M2-S2 | identité persistante provider-scoped | ✅ VALIDÉ | #11 | ADR-0023 | 58/58 |
| M2-S3 | changements / contraintes / décisions / tâches | ✅ VALIDÉ | #12 | ADR-0024 | 64/64 |
| M2-S4 | requirement deltas ADDED/MODIFIED/REMOVED | ✅ VALIDÉ | #13 | ADR-0025 | 70/70 |
| M2-S5 | ExternalReference + résolution optionnelle | ✅ VALIDÉ | #15 | ADR-0026 | 76/76 |
| M2-S6 | lecture unifiée + partiel + diagnostics | ✅ VALIDÉ | #17 | ADR-0028 | 84/84 |
| M2-S7 | second provider anti-lock-in | ✅ VALIDÉ | #18 | ADR-0029 | 94/94 |
| M2-S8 | audit final + décision persistance + validation | ✅ VALIDÉ | #19 | ADR-0030 | 94/94 |

ADR-0027 est transversale et fixe la distribution `native-first / container-supported` ; elle n'est pas un slice M2.

---

# 3. Ce que M2 livre réellement

## 3.1 Discovery et sélection

```text
workspace
  ↓
WorkspaceRootResolver
  ↓
SpecificationProviderRegistry
  ↓
probe
  ↓
capability negotiation
```

Garanties :

```text
explicit path first
Git ancestor fallback seulement si nécessaire
non-Git workspace supporté
local preferred à capacité équivalente
remote opt-in
sélection déterministe
```

## 3.2 Lecture provider

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

`probe()` détermine compatibilité et capabilities.

`read()` expose ce qui a réellement été produit.

Résultat :

```text
ProviderReadResult
├── providerId
├── NormalizedProjectContent?
├── ReadCategoryReport[]
└── Diagnostic[]
```

Statuts :

```text
READ
ABSENT
UNSUPPORTED
FAILED
PARTIAL
```

Invariant :

```text
empty collection != ambiguous success
```

## 3.3 Domaine normalisé

```text
ProjectSpecification
Specification
Requirement
RequirementDelta
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
Provenance
ExternalReference
ExternalReferenceTarget
ResolvedExternalTarget
```

Règle :

```text
Scenario != AcceptanceCriterion
```

Un `AcceptanceCriterion` ne sera produit que lorsqu'un provider expose une sémantique explicite compatible.

## 3.4 OpenSpec courant

```text
openspec/specs/**/spec.md
  ↓
Specification
Requirement
Scenario
Evidence
Provenance
```

Schéma supporté :

```text
spec-driven
```

Un schéma inconnu est rejeté explicitement.

## 3.5 Changements OpenSpec

```text
proposal.md
  ↓ ChangeProposal + Constraint

design.md
  ↓ DesignDecision

tasks.md
  ↓ ImplementationTask

changes/*/specs/**/spec.md
  ↓ RequirementDelta
```

Deltas :

```text
ADDED
MODIFIED
REMOVED
```

Ils sont normalisés mais non appliqués en M2.

## 3.6 Oracle OpenSpec principal

`openspec-basic` :

```text
1 Specification
2 current Requirements
2 current Scenarios
1 ChangeProposal
3 RequirementDeltas
2 Constraints
2 DesignDecisions
8 ImplementationTasks
26 Evidence
```

Invariant :

```text
baseline RequirementId == MODIFIED delta RequirementId
baseline content       != MODIFIED delta content
```

## 3.7 Identité

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

Invariants :

```text
DomainIdentity != externalId
DomainIdentity != SourceLocator
DomainIdentity != ExternalReference
provider namespace fait partie de la résolution
continuité explicite uniquement
aucune fusion par similarité
```

SQLite persiste les bindings via `V003__entity_identity_bindings.sql`.

## 3.8 Provenance et evidence

```text
normalized entity
      ↓
Provenance
      ↓ EvidenceId
Evidence
      ↓
SourceLocator + SourceRange + hash?
```

## 3.9 ExternalReference

États :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

Architecture :

```text
ExternalReference
    ↓ optional
ExternalReferenceResolutionService
    ↓
ExternalReferenceResolverRegistry
    ↓
adapter externe optionnel
```

Une panne ou absence MINOS/GitHub/Jira ne rend pas MORPHEUS indisponible.

## 3.10 Source partielle

Fixture `openspec-partial` :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

Les éléments valides restent exploitables.

## 3.11 Second provider / anti-lock-in

Module :

```text
morpheus-provider-synthetic
```

Statut : `verification-only`.

Architecture prouvée :

```text
OpenSpec source ─────┐
                     ├──> SpecificationContentReader
Synthetic JSON ──────┘          ↓
                         ProviderReadResult
                               ↓
                      NormalizedProjectContent
```

Garanties :

```text
même port SpecificationProvider
même port SpecificationContentReader
même ReadCategory vocabulary
même domaine MORPHEUS
consumer sans branche provider-specific
aucun type provider dans domain/application
```

Une même external key est namespacée :

```text
(openspec, requirement, X) != (synthetic-json, requirement, X)
```

---

# 4. Preuves accumulées

```text
M2-S1  PR #10  ADR-0022  48/48 PASS
M2-S2  PR #11  ADR-0023  58/58 PASS
M2-S3  PR #12  ADR-0024  64/64 PASS
M2-S4  PR #13  ADR-0025  70/70 PASS
M2-S5  PR #15  ADR-0026  76/76 PASS
M2-S6  PR #17  ADR-0028  84/84 PASS
M2-S7  PR #18  ADR-0029  94/94 PASS
M2-S8  PR #19  ADR-0030  94/94 PASS
```

Gate final :

```text
Domain                                   4 tests
Application                             38 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             6 tests
Architecture tests                      13 tests
-----------------------------------------------
TOTAL                                   94/94 PASS
```

---

# 5. Décision de persistance M2 -> M3

ADR-0030 est acceptée.

```text
M2 persiste :
  projects
  knowledge snapshot metadata
  entity identity bindings
  schema migration ledger

M3 introduit :
  TemporalState
  SpecificationVersion
  KnowledgeSnapshot complet
  snapshot/version membership
  premières tables métier versionnées
```

Pourquoi :

```text
M2 stabilise la structure
M3 possède temporalité et versions
ADR-0012 exige une publication cohérente par snapshot
ADR-0021 avait volontairement différé les tables métier
```

Le contenu normalisé reste reconstructible depuis les sources jusqu'à M3 ; la stabilité des identités est déjà persistée.

---

# 6. Frontière M2 / M3 / M4

## M2 possède

```text
normalisation structurelle
provider-neutral domain
stable identity
provenance / evidence
change metadata
requirement deltas non appliqués
ExternalReference
lecture partielle et diagnostics
anti-lock-in provider
```

## M3 possède

```text
TemporalState
CURRENT / PROPOSED / HISTORICAL
SpecificationVersion
KnowledgeSnapshot complet
snapshot/version membership
activation atomique du contenu
ChangeLifecycleState complet
application/promotion des deltas
rétention / comparaison
premières tables métier versionnées
```

## M4 possède

```text
TraceabilityLink
AFFECTS / REFINES / SATISFIES / etc.
traversées de graphe métier
```

---

# 7. Checklist de sortie M2

| Condition | État | Slice |
|---|---|---|
| domaine provider-neutral | ✅ | S1 |
| provenance et evidence | ✅ | S1 |
| identité persistante | ✅ | S2 |
| changement normalisé | ✅ | S3 |
| deltas normalisés | ✅ | S4 |
| ExternalReference | ✅ | S5 |
| résolution externe optionnelle | ✅ | S5 |
| lecture unifiée | ✅ | S6 |
| ingestion partielle explicite | ✅ | S6 |
| politique AcceptanceCriterion | ✅ | S6 |
| second provider | ✅ | S7 |
| décision persistance métier | ✅ ADR-0030 | S8 |
| `VALIDATION_M2.md` | ✅ | S8 |
| gate final 94/94 | ✅ | S8 |

---

# 8. Warnings non bloquants

```text
JDK 24 / Xerial SQLite native access
ArchUnit / SLF4J NOP provider
```

Ils restent à traiter selon leur jalon naturel ; aucun n'invalide M2.

---

# 9. Suite

M2 est fermée.

Le prochain jalon est :

```text
M3 — État temporel, lifecycle, snapshots et versions
```

Le démarrage M3 doit partir de `main` après merge de la PR #19.
