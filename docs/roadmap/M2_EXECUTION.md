# M2 — Plan d'exécution détaillé

Statut : **M2 en cours — 7 slices validés sur 8 ; S8 actif**

Dernière mise à jour : 22 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et constitue le tableau de bord opérationnel de M2.

---

# 1. Position actuelle

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     🚧 actif
  S1   ✅ domaine courant
  S2   ✅ identité persistante
  S3   ✅ modèle de changement
  S4   ✅ requirement deltas
  S5   ✅ ExternalReference
  S6   ✅ lecture unifiée / partiel / diagnostics
  S7   ✅ second provider / anti-lock-in
  S8   🚧 validation finale / décision persistance
M3     ⏳ bloqué jusqu'au gate final M2
```

Progression de pilotage :

```text
M2 : [██████████████████░░] 7 / 8 slices validés
```

Cette barre mesure les slices de gouvernance, pas la charge restante.

---

# 2. NOW / NEXT / LATER

## NOW — M2-S8

```text
M2-S8 — Audit final + décision de persistance + VALIDATION_M2.md
Branche : m2/final-validation
PR      : à ouvrir
ADR     : ADR-0030 proposée
Base    : main @ 94/94 tests
```

Objectif :

> Démontrer formellement que la porte de sortie M2 est satisfaite, décider la frontière de persistance avec M3 et autoriser M3 uniquement après un dernier gate complet vert.

Livrables :

```text
docs/VALIDATION_M2.md
ADR-0030
README.md mis à jour
docs/ROADMAP.md mis à jour
docs/roadmap/M2_EXECUTION.md finalisé
issue #9 synchronisée puis fermée après gate
```

Décision de persistance candidate :

```text
M2 :
  projects                         persistés
  snapshot metadata                persistées
  entity identity bindings         persistés
  normalized business content      reconstructible, non persisté

M3 :
  TemporalState
  SpecificationVersion
  KnowledgeSnapshot complet
  snapshot/version membership
  premières tables métier versionnées
```

Gate final attendu :

```text
94/94 PASS
Failures = 0
Errors   = 0
BUILD SUCCESS
```

## NEXT — si gate final vert

```text
M3 — État temporel, lifecycle, snapshots et versions
```

M3 démarre seulement après :

```text
VALIDATION_M2.md = VALIDÉE
ADR-0030 = ACCEPTÉE
issue #9 = fermée
```

## LATER

```text
M4  traçabilité
M5  requêtes / contexte compact
M6  qualité / couverture
M7  synchronisation incrémentale
M8  analyse des changements
M9  CLI stabilisée / distribution native
M10 MCP
M11 API / conteneur headless
```

---

# 3. Tableau de progression M2

| Slice | Résultat | Statut | PR | ADR | Gate |
|---|---|---|---|---|---|
| M2-S1 | domaine courant + provenance/evidence | ✅ VALIDÉ | #10 | ADR-0022 | 48/48 |
| M2-S2 | identité persistante provider-scoped | ✅ VALIDÉ | #11 | ADR-0023 | 58/58 |
| M2-S3 | changements / contraintes / décisions / tâches | ✅ VALIDÉ | #12 | ADR-0024 | 64/64 |
| M2-S4 | requirement deltas ADDED/MODIFIED/REMOVED | ✅ VALIDÉ | #13 | ADR-0025 | 70/70 |
| M2-S5 | ExternalReference + résolution optionnelle | ✅ VALIDÉ | #15 | ADR-0026 | 76/76 |
| M2-S6 | lecture unifiée + partiel + diagnostics | ✅ VALIDÉ | #17 | ADR-0028 | 84/84 |
| M2-S7 | second provider anti-lock-in | ✅ VALIDÉ | #18 | ADR-0029 | 94/94 |
| **M2-S8** | **audit final + décision persistance + validation** | **🚧 ACTIF** | à ouvrir | ADR-0030 | base 94 |

ADR-0027 est transversale et fixe la distribution `native-first / container-supported` ; elle n'est pas un slice M2.

---

# 4. Ce que MORPHEUS sait réellement faire après S7

## 4.1 Discovery et sélection

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

## 4.2 Lecture provider

Séparation validée :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

`probe()` répond à :

```text
la source est-elle reconnue ?
le schéma est-il supporté ?
quelles capabilities sont disponibles ?
```

`read()` répond à :

```text
qu'est-ce qui a réellement été produit ?
qu'est-ce qui était absent ?
qu'est-ce qui n'est pas supporté ?
qu'est-ce qui a échoué ?
qu'est-ce qui est partiel ?
```

## 4.3 Résultat de lecture

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

## 4.4 OpenSpec current

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

## 4.5 Changements OpenSpec

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

Ils sont normalisés mais **non appliqués** en M2.

## 4.6 Oracle OpenSpec principal

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

## 4.7 Identité

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

## 4.8 Provenance et evidence

```text
normalized entity
      ↓
Provenance
      ↓ EvidenceId
Evidence
      ↓
SourceLocator + SourceRange + hash?
```

Aucune entité importée ne doit devenir une vérité MORPHEUS sans preuve source.

## 4.9 ExternalReference

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

## 4.10 Source partielle

Fixture `openspec-partial` :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

Les éléments valides restent exploitables.

## 4.11 AcceptanceCriterion

Règle M2 formelle :

```text
Scenario != AcceptanceCriterion
```

OpenSpec actuel :

```text
READ_ACCEPTANCE_CRITERIA non annoncé
ACCEPTANCE_CRITERIA = UNSUPPORTED
```

Aucune conversion automatique.

## 4.12 Second provider / anti-lock-in

S7 compile un second adapter réel :

```text
morpheus-provider-synthetic
```

mais `verification-only`.

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

# 5. Preuves accumulées

## M2-S1

```text
PR #10
ADR-0022
48/48 PASS
```

Prouve domaine current provider-neutral + evidence/provenance.

## M2-S2

```text
PR #11
ADR-0023
58/58 PASS
```

Prouve identité provider-scoped persistante.

## M2-S3

```text
PR #12
ADR-0024
64/64 PASS
```

Prouve `ChangeProposal`, `Constraint`, `DesignDecision`, `ImplementationTask`.

## M2-S4

```text
PR #13
ADR-0025
70/70 PASS
```

Prouve deltas ADDED/MODIFIED/REMOVED et continuité du `RequirementId` logique.

## M2-S5

```text
PR #15
ADR-0026
76/76 PASS
```

Prouve `ExternalReference` et résolution optionnelle.

## M2-S6

```text
PR #17
ADR-0028
84/84 PASS
```

Prouve :

```text
READ / ABSENT / UNSUPPORTED / FAILED / PARTIAL
source partielle exploitable
diagnostics non ambigus
Scenario != AcceptanceCriterion
```

## M2-S7

```text
PR #18
ADR-0029
94/94 PASS
```

Prouve :

```text
second provider compilé
JSON réellement lu
mêmes ports applicatifs
même domaine MORPHEUS
consumer neutre
identités provider-scoped
ArchUnit vert
```

---

# 6. M2-S8 — plan de clôture

## 6.1 Audit fonctionnel

Porte à démontrer :

> Une source supportée peut être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, et un second provider démontre l'absence de verrouillage OpenSpec.

État avant gate final : **démontré par S1-S7**.

## 6.2 Décision de persistance

ADR-0030 propose :

```text
pas de nouvelles tables métier en M2
```

Pourquoi :

```text
M2 stabilise la structure
M3 possède la temporalité et les versions
ADR-0012 exige une publication cohérente par snapshot
ADR-0021 a volontairement différé les tables métier
```

Le premier schéma métier complet doit donc être conçu avec :

```text
TemporalState
SpecificationVersion
KnowledgeSnapshot
snapshot/version membership
```

## 6.3 Persistance réellement disponible avant M3

```text
projects                         ✅
knowledge snapshot metadata      ✅
entity identity bindings         ✅
schema migration ledger          ✅
normalized business entities     ⏳ M3
```

Le contenu métier reste reconstructible depuis les sources ; la stabilité d'identité est conservée.

## 6.4 Gate final

Aucune nouvelle fonctionnalité n'est introduite dans S8.

Le gate final vérifie que les changements de gouvernance n'ont pas altéré le build :

```text
.\mvnw.cmd clean test
```

Attendu :

```text
94/94 PASS
Failures = 0
Errors   = 0
BUILD SUCCESS
```

## 6.5 Après gate vert

```text
ADR-0030 -> ACCEPTÉE — M2
VALIDATION_M2.md -> VALIDÉE — M3 autorisée
M2-S8 -> ✅
M2 -> ✅ 8/8
issue #9 -> CLOSED
ROADMAP -> M3 devient prochain jalon
```

---

# 7. Frontière M2 / M3 / M4

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
| lecture unifiée | ✅ | S6 |
| ingestion partielle explicite | ✅ | S6 |
| politique AcceptanceCriterion | ✅ | S6 |
| second provider | ✅ | S7 |
| décision persistance métier | ✅ candidate ADR-0030 | S8 |
| VALIDATION_M2.md | ✅ candidate | S8 |
| gate final 94/94 | ⏳ | S8 |

---

# 9. Warnings non bloquants

```text
JDK 24 / Xerial SQLite native access
ArchUnit / SLF4J NOP provider
```

Ils restent à traiter selon leur jalon naturel ; aucun n'invalide M2.

---

# 10. Règle de gouvernance finale

```text
1. exécuter le gate S8
2. inscrire le résultat exact dans ADR-0030
3. finaliser VALIDATION_M2.md
4. finaliser README + ROADMAP
5. merger PR S8
6. fermer issue #9
7. autoriser M3
```
