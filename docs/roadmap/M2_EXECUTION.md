# M2 — Plan d'exécution détaillé

Statut : **M2 en cours — 5 slices validés sur 8**

Dernière mise à jour : 22 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et sert de tableau de bord opérationnel.

---

# 1. Vue immédiate — NOW / NEXT / LATER

## NOW — prochain slice actif

```text
M2-S6 — Contrat de lecture unifié, sources partielles et diagnostics
Statut  : 🚧 PROCHAIN
Branche : à créer après merge de PR #15
PR      : à créer
ADR     : candidate à documenter
Gate    : base actuelle = 76/76 tests
```

Objectif :

> Un provider expose un résultat de lecture unique, explicite sur ce qui a été lu, absent, non supporté, partiel ou en échec, sans laisser une collection vide masquer une perte d'information.

Livrables prévus :

```text
SpecificationContentReader
ProviderReadRequest
ProviderReadResult
ReadCategory
ReadCategoryStatus
ReadCategoryReport
```

Statuts candidats :

```text
READ
ABSENT
UNSUPPORTED
FAILED
PARTIAL
```

Catégories à gouverner :

```text
CURRENT_SPECIFICATIONS
CHANGES
REQUIREMENTS
SCENARIOS
CONSTRAINTS
DESIGN_DECISIONS
IMPLEMENTATION_TASKS
EXTERNAL_REFERENCES
ARCHIVES
```

Diagnostics à exercer lorsque sémantiquement justifiés :

```text
PARTIAL_INGESTION
OPTIONAL_CAPABILITY_UNAVAILABLE
UNRESOLVED_REFERENCE
BROKEN_REFERENCE
```

Fixture principale :

```text
experiments/m0/fixtures/openspec-partial
```

Politique AcceptanceCriterion à figer :

```text
Scenario != AcceptanceCriterion
aucune dérivation automatique
absence de sémantique explicite => catégorie non produite
```

## NEXT — immédiatement après S6

```text
M2-S7 — Second provider synthétique / preuve anti-lock-in
```

Objectif :

```text
OpenSpec reader ─────┐
                      ├──> même contrat applicatif
Synthetic reader ────┘     même domaine MORPHEUS
```

Preuves obligatoires :

- aucun type OpenSpec dans `com.morpheus.domain` ;
- aucun branchement `if provider == openspec` dans les contrats applicatifs ;
- mêmes catégories et statuts de lecture ;
- identités provider-scoped distinctes ;
- contenu normalisé comparable par concept, pas par format source.

## LATER — fermeture M2

```text
M2-S8 — Audit final + décision de persistance + VALIDATION_M2.md
```

M3 reste bloquée tant que S6, S7 et S8 ne sont pas validés ou explicitement reportés par ADR.

---

# 2. Tableau de progression M2

| Slice | Résultat | Statut | PR | ADR | Gate |
|---|---|---|---|---|---|
| M2-S1 | domaine courant + provenance/evidence | ✅ VALIDÉ | #10 | ADR-0022 | 48/48 |
| M2-S2 | identité persistante provider-scoped | ✅ VALIDÉ | #11 | ADR-0023 | 58/58 |
| M2-S3 | changements / contraintes / décisions / tâches | ✅ VALIDÉ | #12 | ADR-0024 | 64/64 |
| M2-S4 | requirement deltas ADDED/MODIFIED/REMOVED | ✅ VALIDÉ | #13 | ADR-0025 | 70/70 |
| M2-S5 | ExternalReference + résolution optionnelle | ✅ VALIDÉ | #15 | ADR-0026 | 76/76 |
| **M2-S6** | **lecture unifiée + partiel + diagnostics** | **🚧 PROCHAIN** | — | candidate | base 76 |
| M2-S7 | second provider anti-lock-in | ⬜ À FAIRE | — | à décider | — |
| M2-S8 | validation et clôture M2 | ⬜ À FAIRE | — | revue globale | — |

Progression de pilotage :

```text
M2 : [████████████░░░░░░░░] 5 / 8 slices validés
```

Cette barre représente le nombre de slices, pas une estimation proportionnelle de charge.

---

# 3. Ce qui est réellement disponible aujourd'hui

## 3.1 Discovery / providers

```text
workspace path
    ↓
WorkspaceRootResolver
    ↓
SpecificationProviderRegistry
    ↓
probe + capability negotiation
```

Validé depuis M1.

## 3.2 Lecture OpenSpec

```text
openspec/specs/**/spec.md
    ↓
OpenSpecCurrentSpecificationReader

openspec/changes/*/proposal.md
openspec/changes/*/design.md
openspec/changes/*/tasks.md
    ↓
OpenSpecChangeMetadataReader

openspec/changes/*/specs/**/spec.md
    ↓
OpenSpecRequirementDeltaReader

ensemble
    ↓
OpenSpecProjectContentReader
    ↓
NormalizedProjectContent
```

## 3.3 Domaine normalisé M2 actuellement stabilisé

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
Provenance
Evidence
ExternalReference
ExternalReferenceTarget
ResolvedExternalTarget
```

## 3.4 Identité

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

Store persistant :

```text
V003__entity_identity_bindings.sql
```

## 3.5 Oracle OpenSpec actuel

`openspec-basic` produit :

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

Invariant déjà prouvé :

```text
baseline RequirementId
        ==
MODIFIED delta RequirementId
```

mais :

```text
baseline content != delta content
```

Aucune application implicite de delta n'a lieu en M2.

## 3.6 Références externes

Architecture validée :

```text
Domain entity
    ↓
ExternalReference
    ↓ optional
ExternalReferenceResolutionService
    ↓
ExternalReferenceResolverRegistry
    ↓
resolver externe
```

États :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

Transitions prouvées :

```text
UNVALIDATED -- no resolver --> UNRESOLVED / NO_RESOLVER
UNVALIDATED -- found -------> RESOLVED
UNRESOLVED  -- found -------> RESOLVED
RESOLVED    -- missing -----> STALE
STALE       -- found -------> RESOLVED
```

Gate :

```text
PR #15
ADR-0026
ExternalReferenceResolutionServiceTest 6/6
TOTAL 76/76 PASS
BUILD SUCCESS
```

---

# 4. Historique détaillé des slices validés

## M2-S1 — Domaine courant

Livrables :

```text
ProjectSpecification
Specification
Requirement
Scenario
Provenance
Evidence
NormalizedProjectContent
OpenSpecCurrentSpecificationReader
```

Invariants :

```text
provider facts != MORPHEUS domain
Scenario != AcceptanceCriterion
content normalization != temporal projection
```

Preuve : `PR #10 / ADR-0022 / 48/48`.

## M2-S2 — Identité persistante

Livrables :

```text
EntityIdentityKey
EntityIdentityBinding
EntityIdentityStore
PersistentEntityIdentityResolver
MemoryEntityIdentityStore
SqliteEntityIdentityStore
V003__entity_identity_bindings.sql
```

Invariants :

```text
externalId != DomainIdentity
provider namespace fait partie de la résolution
continuité explicite uniquement
aucune fusion par titre/chemin/contenu
```

Preuve : `PR #11 / ADR-0023 / 58/58`.

## M2-S3 — Métadonnées de changement

Livrables :

```text
ChangeProposal
Constraint
DesignDecision
ImplementationTask
OpenSpecChangeMetadataReader
```

Invariants :

```text
change structure != TemporalState
checkbox task != ChangeLifecycleState
texte anonyme != identité
```

Preuve : `PR #12 / ADR-0024 / 64/64`.

## M2-S4 — Requirement deltas

Livrables :

```text
RequirementDeltaKind
RequirementDeltaId
RequirementDelta
OpenSpecRequirementDeltaReader
```

Invariants :

```text
RequirementDeltaKind != TemporalState
RequirementDeltaId != RequirementId
normalized delta != applied delta
```

Preuve : `PR #13 / ADR-0025 / 70/70`.

## M2-S5 — ExternalReference

Livrables :

```text
ExternalReferenceId
ExternalReferenceTarget
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolutionReason
ExternalReferenceResolutionEvent
ResolvedExternalTarget
ExternalReferenceResolver
ExternalReferenceResolverResult
ExternalReferenceResolverRegistry
ExternalReferenceResolutionService
```

Invariants :

```text
DomainIdentity != ExternalReference
ExternalReference peut exister sans resolver
NO_RESOLVER est explicite
resolver indisponible != panne MORPHEUS
cible supprimée != suppression de référence
historique et provenance sont conservés
```

Preuve :

```text
PR #15
ADR-0026
76/76 PASS
BUILD SUCCESS
```

---

# 5. Fiche de travail — M2-S6

## 5.1 Problème à résoudre

Aujourd'hui plusieurs readers OpenSpec produisent des sous-ensembles corrects, mais le contrat public de lecture ne dit pas encore explicitement :

```text
ce qui a été lu
ce qui était absent
ce qui n'est pas supporté
ce qui a échoué
ce qui n'a été lu que partiellement
```

Une collection vide ne doit jamais être ambiguë.

## 5.2 Contrat cible

Candidate :

```text
ProviderReadRequest
├── workspace / source
├── requestedCategories
└── policy

SpecificationContentReader
    ↓
ProviderReadResult
├── NormalizedProjectContent
├── categoryReports
└── diagnostics
```

Rapport par catégorie :

```text
ReadCategoryReport
├── category
├── status
├── itemCount
└── diagnosticIds / message éventuel
```

## 5.3 Fixture partielle

`openspec-partial` doit démontrer au minimum :

- source reconnue ;
- certaines catégories lisibles ;
- catégories absentes distinguées de catégories non supportées ;
- lecture exploitable malgré contenu incomplet ;
- `PARTIAL_INGESTION` uniquement lorsqu'une partie demandée n'a pas pu être produite ;
- aucun faux succès silencieux.

## 5.4 AcceptanceCriterion

Décision à formaliser dans S6 :

```text
Scenario != AcceptanceCriterion
```

MORPHEUS ne crée un `AcceptanceCriterion` que si la source/provider expose une sémantique explicite de critère d'acceptation.

Pour OpenSpec M2 actuel :

```text
READ_ACCEPTANCE_CRITERIA non annoncé
Scenario ne déclenche aucune conversion implicite
```

## 5.5 Critères de sortie S6

- un seul point d'entrée de lecture provider-facing ;
- statut explicite par catégorie demandée ;
- `READ / ABSENT / UNSUPPORTED / FAILED / PARTIAL` testés ;
- `openspec-partial` exercée en Java ;
- diagnostics déterministes ;
- contenu partiel exploitable sans ambiguïté ;
- politique AcceptanceCriterion testée ;
- aucun changement de temporalité M3 ;
- build complet vert.

---

# 6. Fiche suivante — M2-S7 anti-lock-in

Objectif :

```text
OpenSpec reader ─────┐
                      ├──> ProviderReadResult
Synthetic reader ────┘     NormalizedProjectContent
```

Preuves obligatoires :

```text
mêmes contrats applicatifs
mêmes concepts MORPHEUS
aucun type OpenSpec dans le domaine
aucun branchement OpenSpec dans l'application
identités provider-scoped distinctes
```

---

# 7. Fiche de clôture — M2-S8

Audit :

- checklist issue #9 ;
- ADR-0022 à ADR-0026+ ;
- fixtures M0 pertinentes ;
- diagnostics ;
- second provider ;
- frontières M2/M3/M4.

Question de persistance :

> Faut-il persister les entités normalisées avant M3, ou introduire cette persistance avec les versions/snapshots M3 ?

Aucune table métier supplémentaire ne sera créée sans décision explicite.

Livrables :

```text
docs/VALIDATION_M2.md
README mis à jour
ROADMAP mise à jour
issue #9 fermée
M3 autorisée ou refusée avec raisons
```

---

# 8. Checklist bloquante avant M3

| Condition | État | Slice |
|---|---|---|
| domaine provider-neutral | ✅ | S1 |
| provenance et evidence | ✅ | S1 |
| identité persistante | ✅ | S2 |
| changement normalisé | ✅ | S3 |
| deltas normalisés | ✅ | S4 |
| ExternalReference | ✅ | S5 |
| résolution externe optionnelle | ✅ | S5 |
| lecture unifiée | 🚧 | S6 |
| ingestion partielle explicite | 🚧 | S6 |
| politique AcceptanceCriterion | 🚧 | S6 |
| second provider | ⬜ | S7 |
| décision persistance métier | ⬜ | S8 |
| VALIDATION_M2.md | ⬜ | S8 |

---

# 9. Règle de mise à jour

Après chaque gate vert :

```text
1. inscrire le résultat exact dans l'ADR
2. mettre la PR Ready
3. merger
4. déplacer NOW vers le slice suivant
5. mettre à jour le tableau de progression
6. mettre à jour la checklist bloquante M3
```
