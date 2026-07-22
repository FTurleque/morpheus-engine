# M2 — Plan d'exécution détaillé

Statut : **M2 en cours — 4 slices validés sur 8**

Dernière mise à jour : 22 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md).

Il répond à quatre questions opérationnelles :

```text
Où en sommes-nous ?
Qu'est-ce qui est terminé ?
Qu'est-ce qui est actif maintenant ?
Qu'est-ce qui bloque le passage à M3 ?
```

---

# 1. Vue immédiate — NOW / NEXT / LATER

## NOW — slice actif

```text
M2-S5 — ExternalReference et résolution optionnelle
Branche : m2/external-references
PR      : à ouvrir
ADR     : ADR-0026 candidate
Gate    : à établir
```

Objectif :

> Une référence vers MINOS, GitHub, Jira ou tout autre système peut exister dans MORPHEUS sans rendre ce système obligatoire.

Livrables attendus :

```text
ExternalReferenceId
ExternalReferenceTarget
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolutionReason
ExternalReferenceResolutionEvent
ExternalReferenceResolver
ExternalReferenceResolverRegistry
ExternalReferenceResolutionService
```

Scénarios obligatoires :

```text
création sans resolver            -> UNVALIDATED
résolution sans resolver          -> UNRESOLVED / NO_RESOLVER
resolver disponible + cible       -> RESOLVED
cible disparue après résolution   -> STALE
cible réapparue                   -> RESOLVED
indisponibilité système cible     -> MORPHEUS reste fonctionnel
```

## NEXT — immédiatement après M2-S5

```text
M2-S6 — Contrat de lecture unifié, sources partielles et diagnostics
```

Livrables :

- contrat applicatif unique de lecture au-delà de `probe` ;
- catégories effectivement lues / absentes / non supportées ;
- résultat partiel explicite ;
- fixture `openspec-partial` exercée en Java ;
- diagnostics d'ingestion déterministes ;
- politique `AcceptanceCriterion` explicite.

## LATER — avant fermeture M2

```text
M2-S7 — Second provider synthétique / preuve anti-lock-in
M2-S8 — Audit final, décision de persistance métier, VALIDATION_M2.md
```

M3 reste bloquée tant que S5 à S8 ne sont pas validés ou explicitement reportés par ADR.

---

# 2. Tableau de progression M2

| Slice | Résultat attendu | Statut | PR | ADR | Gate | Dépend de |
|---|---|---:|---:|---|---:|---|
| M2-S1 | domaine courant + provenance/evidence | ✅ Validé | #10 | ADR-0022 | 48/48 | M1 |
| M2-S2 | identité persistante provider-scoped | ✅ Validé | #11 | ADR-0023 | 58/58 | S1 |
| M2-S3 | changement, contraintes, décisions, tâches | ✅ Validé | #12 | ADR-0024 | 64/64 | S1-S2 |
| M2-S4 | deltas ADDED/MODIFIED/REMOVED | ✅ Validé | #13 | ADR-0025 | 70/70 | S2-S3 |
| **M2-S5** | références externes optionnelles | **🚧 Actif** | à ouvrir | ADR-0026 candidate | à définir | S1-S2 |
| M2-S6 | lecture unifiée + partiel + diagnostics | ⬜ À faire | — | à décider | — | S1-S5 |
| M2-S7 | second provider anti-lock-in | ⬜ À faire | — | si nécessaire | — | S6 |
| M2-S8 | validation et clôture M2 | ⬜ À faire | — | revue globale | — | S1-S7 |

Progression de pilotage :

```text
M2 : [██████████░░░░░░░░░░] 4 / 8 slices validés
```

Cette barre représente le nombre de slices, pas une estimation proportionnelle de charge.

---

# 3. Ce qui est réellement disponible aujourd'hui

## 3.1 Discovery et sélection

```text
workspace path
    ↓
WorkspaceRootResolver
    ↓
SpecificationProviderRegistry
    ↓
OpenSpec probe / capability negotiation
```

Validé depuis M1.

## 3.2 Lecture OpenSpec actuelle

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
```

Agrégation :

```text
OpenSpecProjectContentReader
            ↓
NormalizedProjectContent
```

## 3.3 Modèle normalisé déjà produit

```text
ProjectSpecification
Specification
Requirement
Scenario
ChangeProposal
RequirementDelta
Constraint
DesignDecision
ImplementationTask
Provenance
Evidence
```

## 3.4 Identité

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

SQLite :

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

Aucune application implicite du delta n'a lieu en M2.

---

# 4. Fiches détaillées des slices terminés

## M2-S1 — Domaine courant

### Entrées

- `openspec/specs/**/spec.md`
- fixture `openspec-basic`

### Livrables

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

### Invariants

```text
provider facts != MORPHEUS domain
Scenario != AcceptanceCriterion
content normalization != temporal projection
```

### Preuve

```text
PR #10
ADR-0022
48/48 tests
```

## M2-S2 — Identité persistante

### Livrables

```text
EntityIdentityKey
EntityIdentityBinding
EntityIdentityStore
PersistentEntityIdentityResolver
MemoryEntityIdentityStore
SqliteEntityIdentityStore
V003__entity_identity_bindings.sql
```

### Invariants

```text
externalId != DomainIdentity
provider namespace fait partie de la résolution
continuité d'identité explicite uniquement
aucune fusion par titre/chemin/contenu
```

### Preuve

```text
PR #11
ADR-0023
58/58 tests
```

## M2-S3 — Métadonnées de changement

### Livrables

```text
ChangeProposal
Constraint
DesignDecision
ImplementationTask
OpenSpecChangeMetadataReader
```

### Invariants

```text
change structure != TemporalState
checkbox task != ChangeLifecycleState
texte anonyme != identité
```

### Preuve

```text
PR #12
ADR-0024
64/64 tests
```

## M2-S4 — Requirement deltas

### Livrables

```text
RequirementDeltaKind
RequirementDeltaId
RequirementDelta
OpenSpecRequirementDeltaReader
```

### Invariants

```text
RequirementDeltaKind != TemporalState
RequirementDeltaId != RequirementId
normalized delta != applied delta
```

### Preuve

```text
PR #13
ADR-0025
70/70 tests
```

---

# 5. Fiche active — M2-S5 ExternalReference

## 5.1 Pourquoi maintenant ?

MORPHEUS doit pouvoir relier une intention à une cible externe sans absorber le domaine de cette cible.

Exemples futurs :

```text
Requirement -> MINOS symbol
Requirement -> GitHub issue
ChangeProposal -> Jira ticket
AcceptanceCriterion -> test MINOS
```

La relation métier générique sera traitée en M4. M2 ne stabilise ici que la référence externe et sa résolution optionnelle.

## 5.2 Modèle cible

```text
ExternalReference
├── id : ExternalReferenceId
├── ownerId : DomainIdentity
├── target : ExternalReferenceTarget
├── resolutionState
├── resolutionReason?
├── resolvedTarget?
├── provenance?
└── history[]
```

Cible provider-neutral :

```text
ExternalReferenceTarget
├── system
├── project?
├── resourceType
├── externalId
└── revision?
```

## 5.3 États

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

Transitions minimales :

```text
UNVALIDATED --resolve/no resolver--> UNRESOLVED(NO_RESOLVER)
UNVALIDATED --resolve/found-------> RESOLVED
UNRESOLVED  --resolve/found-------> RESOLVED
RESOLVED    --resolve/missing-----> STALE
STALE       --resolve/found-------> RESOLVED
```

## 5.4 Architecture

```text
MORPHEUS domain
    ↓
ExternalReference
    ↓ optional
ExternalReferenceResolutionService
    ↓
ExternalReferenceResolverRegistry
    ↓
resolver adapter MINOS / GitHub / Jira / autre
```

Interdit :

```text
com.morpheus.domain -> classes MINOS
com.morpheus.domain -> SDK GitHub
com.morpheus.domain -> client Jira
```

## 5.5 Critères de sortie S5

- référence créée sans resolver ;
- `NO_RESOLVER` explicite ;
- résolution réussie ;
- cible supprimée -> `STALE` ;
- résolution différée ;
- historique des transitions conservé ;
- provenance conservée ;
- indisponibilité externe non fatale ;
- aucune dépendance directe vers une intégration concrète ;
- build complet vert.

---

# 6. Fiche suivante — M2-S6 Lecture unifiée et ingestion partielle

## Livrables prévus

```text
SpecificationContentReader
ProviderReadRequest
ProviderReadResult
ReadCategoryStatus
```

Catégories candidates :

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

Chaque catégorie devra distinguer :

```text
READ
ABSENT
UNSUPPORTED
FAILED
PARTIAL
```

Diagnostics candidats :

```text
PARTIAL_INGESTION
UNRESOLVED_REFERENCE
BROKEN_REFERENCE
OPTIONAL_CAPABILITY_UNAVAILABLE
```

Fixture principale :

```text
openspec-partial
```

---

# 7. Fiche suivante — M2-S7 Second provider

## Objectif

Démontrer que le modèle M2 est réellement provider-neutral.

```text
OpenSpec reader ─────┐
                     ├──> NormalizedProjectContent
Synthetic reader ────┘
```

Preuves obligatoires :

- même contrat de lecture ;
- mêmes concepts MORPHEUS ;
- aucun type OpenSpec dans le domaine ;
- aucune condition `if provider == openspec` dans l'application ;
- identités provider-scoped distinctes.

---

# 8. Fiche de clôture — M2-S8

## Audit

- checklist issue #9 ;
- ADR-0022 à ADR-0026+ ;
- tests de toutes les fixtures M0 pertinentes ;
- diagnostics ;
- second provider ;
- frontières M2/M3/M4.

## Décision de persistance

Question explicite :

> Faut-il persister les entités normalisées avant M3, ou la persistance doit-elle être introduite avec les versions/snapshots M3 ?

Aucune table métier ne sera créée sans cette décision.

## Livrables

```text
docs/VALIDATION_M2.md
README mis à jour
ROADMAP mise à jour
issue #9 fermée
M3 autorisée ou refusée avec raisons
```

---

# 9. Checklist bloquante avant M3

| Condition | État | Slice |
|---|---:|---|
| domaine provider-neutral | ✅ | S1 |
| provenance et evidence | ✅ | S1 |
| identité persistante | ✅ | S2 |
| changement normalisé | ✅ | S3 |
| deltas normalisés | ✅ | S4 |
| ExternalReference | 🚧 | S5 |
| résolution optionnelle | 🚧 | S5 |
| lecture unifiée | ⬜ | S6 |
| ingestion partielle explicite | ⬜ | S6 |
| politique AcceptanceCriterion | ⬜ | S6 |
| second provider | ⬜ | S7 |
| décision persistance métier | ⬜ | S8 |
| VALIDATION_M2.md | ⬜ | S8 |

---

# 10. Règle de mise à jour

Après chaque gate vert :

```text
1. inscrire le résultat exact dans l'ADR
2. mettre la PR Ready
3. merger
4. déplacer le marqueur NOW vers le slice suivant
5. mettre à jour le tableau de progression
6. mettre à jour la checklist bloquante M3
```
