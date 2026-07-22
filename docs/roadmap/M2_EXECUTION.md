# M2 — Plan d'exécution détaillé

Statut : **M2 en cours — 6 slices validés sur 8 ; S7 actif**

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
  S7   🚧 second provider / anti-lock-in
  S8   ⬜ validation finale
M3     ⏳ bloqué par M2
```

Progression de pilotage :

```text
M2 : [███████████████░░░░░] 6 / 8 slices validés
```

Cette barre mesure les slices de gouvernance, pas la charge restante.

---

# 2. NOW / NEXT / LATER

## NOW — M2-S7

```text
M2-S7 — Second provider synthétique / preuve anti-lock-in
Branche : m2/synthetic-provider-anti-lockin
PR      : Draft à ouvrir
ADR     : ADR-0029 proposée
Base    : main @ 84/84 tests
```

Question :

> Le même contrat applicatif et le même domaine MORPHEUS peuvent-ils être produits par un second format sans introduire une seule dépendance OpenSpec dans le domaine ou l'application ?

Architecture cible :

```text
OpenSpec source ─────> OpenSpec adapter ──────┐
                                              │
                                              ├──> SpecificationContentReader
                                              │        ↓
Synthetic JSON ──────> Synthetic adapter ─────┘    ProviderReadResult
                                                       ↓
                                              NormalizedProjectContent
```

Livrables :

```text
morpheus-provider-synthetic
SyntheticSpecificationProvider
SyntheticSpecificationContentReader
SyntheticJsonParser (adapter-internal)
ProviderAntiLockInTest
ADR-0029
```

Preuves obligatoires :

```text
même port SpecificationProvider
même port SpecificationContentReader
même ProviderReadResult
même NormalizedProjectContent
même ReadCategory vocabulary
aucun type OpenSpec dans domain/application
aucun type synthetic JSON dans domain/application
même external key + provider différent => DomainIdentity différente
```

Fixture :

```text
experiments/m0/fixtures/synthetic-basic/morpheus-spec.json
```

Oracle principal :

```text
Specification : billing
Requirement   : billing/invoice-retention
Scenario      : Retain invoice
Change        : extend-retention
```

Gate attendu avant acceptation :

```text
84 tests baseline S6
+ 3 SyntheticSpecificationProviderTest
+ 4 SyntheticSpecificationContentReaderTest
+ 3 ProviderAntiLockInTest
----------------------------------------------
94 tests attendus
```

## NEXT — M2-S8

```text
M2-S8 — Audit final + décision de persistance + VALIDATION_M2.md
```

Travail prévu :

```text
audit issue #9
audit ADR-0022..ADR-0029
audit fixtures M0 pertinentes
décision explicite persistance métier avant M3
VALIDATION_M2.md
README + ROADMAP
fermeture issue #9
autorisation ou refus de M3
```

## LATER

```text
M3 — TemporalState / lifecycle / versions / snapshots
```

M3 ne démarre pas avant la preuve de sortie M2.

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
| **M2-S7** | **second provider anti-lock-in** | **🚧 ACTIF** | à ouvrir | ADR-0029 | base 84 |
| M2-S8 | validation et clôture M2 | ⬜ À FAIRE | — | revue globale | — |

ADR-0027 est transversale et fixe la stratégie de distribution `native-first / container-supported` ; elle n'est pas un slice M2.

---

# 4. Ce que MORPHEUS sait réellement faire aujourd'hui

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
local preferred à capacité équivalente
remote opt-in
sélection déterministe
```

## 4.2 OpenSpec courant

```text
openspec/specs/**/spec.md
  ↓
OpenSpecCurrentSpecificationReader
  ↓
Specification
Requirement
Scenario
Evidence
Provenance
```

## 4.3 Changements OpenSpec

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

Les deltas :

```text
ADDED
MODIFIED
REMOVED
```

restent normalisés mais **non appliqués**.

## 4.4 Identité

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

Invariant :

```text
DomainIdentity != externalId
DomainIdentity != SourceLocator
DomainIdentity != ExternalReference
```

SQLite persiste les bindings via :

```text
V003__entity_identity_bindings.sql
```

## 4.5 Références externes

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

## 4.6 Lecture provider unifiée

Depuis S6 :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Résultat :

```text
ProviderReadResult
├── content?
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

Fixture `openspec-partial` prouvée :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

## 4.7 AcceptanceCriterion

Règle M2 maintenant formelle :

```text
Scenario != AcceptanceCriterion
```

OpenSpec actuel :

```text
READ_ACCEPTANCE_CRITERIA non annoncé
ACCEPTANCE_CRITERIA = UNSUPPORTED
```

Aucune conversion automatique.

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

Prouve ChangeProposal, Constraint, DesignDecision, ImplementationTask.

## M2-S4

```text
PR #13
ADR-0025
70/70 PASS
```

Prouve deltas ADDED/MODIFIED/REMOVED et continuité du RequirementId logique.

## M2-S5

```text
PR #15
ADR-0026
76/76 PASS
```

Prouve ExternalReference et résolution optionnelle.

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

---

# 6. M2-S7 — plan détaillé

## 6.1 Pourquoi un vrai module

Un simple mock dans un test prouverait seulement que l'interface est mockable.

S7 introduit donc :

```text
morpheus-provider-synthetic
```

comme adapter réel compilé dans le reactor, mais **verification-only**.

Il ne devient pas une fonctionnalité utilisateur et ne devra pas être injecté dans le runtime CLI par défaut.

## 6.2 Frontières

```text
morpheus-domain              -X-> provider-openspec
morpheus-domain              -X-> provider-synthetic
morpheus-application         -X-> provider-openspec
morpheus-application         -X-> provider-synthetic

provider-openspec            -> domain + application
provider-synthetic           -> domain + application
```

ArchUnit couvre déjà `com.morpheus.provider..` de manière générique.

## 6.3 Identité cross-provider

Test obligatoire :

```text
external key = requirement:auth-session/session-expiration
```

Avec le même resolver :

```text
ProviderId openspec       -> DomainIdentity A
ProviderId synthetic-json -> DomainIdentity B
A != B
```

Aucune fusion implicite cross-provider.

## 6.4 Consumer neutre

Une même méthode de test reçoit :

```java
SpecificationContentReader
```

et lit successivement :

```text
OpenSpecSpecificationContentReader
SyntheticSpecificationContentReader
```

sans `instanceof`, sans `switch(providerId)`, sans structure source spécifique.

## 6.5 Critères de sortie S7

- module synthétique compilé ;
- source JSON réellement lue ;
- provider réellement probé ;
- domaine MORPHEUS produit ;
- mêmes ports application utilisés ;
- même vocabulaire `ReadCategory` ;
- identités provider-scoped prouvées ;
- aucune modification du domaine nécessaire ;
- ArchUnit vert ;
- build complet vert.

---

# 7. M2-S8 — plan de clôture

## 7.1 Audit fonctionnel

Vérifier la porte de sortie :

> Une source supportée peut être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, et un second provider démontre l'absence de verrouillage OpenSpec.

## 7.2 Persistance métier

Décision explicite requise :

> Persister les entités normalisées en M2, ou attendre M3 afin d'introduire simultanément versions et snapshots ?

Aucune table métier supplémentaire avant cette décision.

## 7.3 Livrables

```text
docs/VALIDATION_M2.md
README.md mis à jour
docs/ROADMAP.md mis à jour
docs/roadmap/M2_EXECUTION.md finalisé
issue #9 fermée si et seulement si gate final vert
M3 autorisée ou refusée explicitement
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
| second provider | 🚧 | S7 |
| décision persistance métier | ⬜ | S8 |
| VALIDATION_M2.md | ⬜ | S8 |

---

# 9. Règle de gouvernance

Après chaque gate vert :

```text
1. inscrire le résultat exact dans l'ADR
2. mettre la PR Ready
3. merger
4. mettre à jour issue #9
5. déplacer NOW vers le slice suivant
6. mettre à jour la checklist M3
```
