# Validation M2 — MORPHEUS

Statut : **CANDIDATE — gate final M2-S8 requis avant clôture**

Date : 22 juillet 2026

---

# 1. Décision candidate

La phase **M2 — Ingestion et modèle normalisé** est candidate à la validation.

Question de sortie :

> **Une source supportée peut-elle être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, tandis qu'un second provider démontre que le modèle n'est pas verrouillé sur OpenSpec ?**

Réponse démontrée par S1-S7 :

```text
OUI — sous les frontières M2/M3/M4 documentées
```

La clôture reste conditionnée au gate local final de S8.

---

# 2. Progression des preuves

| Slice | Sujet | PR | ADR | Gate |
|---|---|---|---|---|
| M2-S1 | domaine courant + provenance/evidence | #10 | ADR-0022 | 48/48 PASS |
| M2-S2 | identité persistante provider-scoped | #11 | ADR-0023 | 58/58 PASS |
| M2-S3 | changement / contraintes / décisions / tâches | #12 | ADR-0024 | 64/64 PASS |
| M2-S4 | requirement deltas | #13 | ADR-0025 | 70/70 PASS |
| M2-S5 | ExternalReference + résolution optionnelle | #15 | ADR-0026 | 76/76 PASS |
| M2-S6 | lecture unifiée + partiel + diagnostics | #17 | ADR-0028 | 84/84 PASS |
| M2-S7 | second provider anti-lock-in | #18 | ADR-0029 | 94/94 PASS |
| M2-S8 | audit final + décision persistance | à ouvrir | ADR-0030 | **gate final requis** |

ADR-0027 est transversale : distribution **native-first / container-supported**.

---

# 3. Domaine normalisé validé

M2 stabilise les concepts de production suivants :

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

`AcceptanceCriterion` reste un concept du domaine cible, mais **aucune instance n'est dérivée automatiquement d'un `Scenario`**. Un provider doit exposer une sémantique explicite avant que cette catégorie soit produite.

Invariant :

```text
Scenario != AcceptanceCriterion
```

---

# 4. OpenSpec — provider de référence

Le provider OpenSpec supporté en M2 :

```text
schema = spec-driven
mode   = local / read-only
```

Flux :

```text
openspec/specs/**/spec.md
    ↓ current reader

openspec/changes/*/proposal.md
openspec/changes/*/design.md
openspec/changes/*/tasks.md
    ↓ change metadata reader

openspec/changes/*/specs/**/spec.md
    ↓ requirement delta reader

ensemble
    ↓
OpenSpecSpecificationContentReader
    ↓
ProviderReadResult
    ↓
NormalizedProjectContent
```

Le provider n'invente pas de version de format absente et rejette explicitement les schémas inconnus.

---

# 5. Oracle `openspec-basic`

Le graphe agrégé prouvé contient :

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

Deltas :

```text
1 MODIFIED
2 ADDED
```

Un test séparé prouve également `REMOVED` sans inventer de statement absent.

Invariant critique :

```text
baseline RequirementId
        ==
MODIFIED delta RequirementId
```

mais :

```text
baseline content != delta content
```

Le delta est normalisé mais non appliqué en M2.

---

# 6. Identité stable

Résolution :

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

Garanties :

```text
DomainIdentity != EntityVersion
DomainIdentity != SourceLocator
DomainIdentity != ExternalReference
externalId != DomainIdentity
provider namespace fait partie de la résolution
continuité d'identité explicite uniquement
aucune fusion par titre/chemin/similarité
```

Les bindings survivent à une fermeture/réouverture SQLite.

---

# 7. Provenance et evidence

Chaque entité importée porte une `Provenance` reliée à une `Evidence` :

```text
entity
  ↓
Provenance
  ↓ EvidenceId
Evidence
  ↓
SourceLocator + SourceRange + hash optionnel
```

Le graphe normalisé rejette les références vers des evidences absentes.

---

# 8. Changements et deltas

M2 distingue :

```text
ChangeProposal
RequirementDelta
```

et interdit de confondre structure de changement et temporalité :

```text
RequirementDeltaKind != TemporalState
change structure != ChangeLifecycleState
normalized delta != applied delta
```

`CURRENT / PROPOSED / HISTORICAL` et le lifecycle complet restent M3.

---

# 9. ExternalReference

États validés :

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
resolver adapter externe
```

Garanties :

```text
référence valide sans resolver
NO_RESOLVER explicite
indisponibilité externe non fatale
cible supprimée -> STALE
historique de résolution conservé
```

Aucune dépendance MINOS/GitHub/Jira n'entre dans le domaine.

---

# 10. Lecture provider unifiée et ingestion partielle

M2 distingue désormais :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Le résultat de lecture fournit un statut explicite par catégorie :

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

La fixture `openspec-partial` prouve :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

Le contenu valide reste exploitable malgré une lecture partielle.

---

# 11. Anti-lock-in OpenSpec

S7 introduit un module de vérification compilé :

```text
morpheus-provider-synthetic
```

Architecture prouvée :

```text
OpenSpec source ─────┐
                     ├──> SpecificationContentReader
Synthetic JSON ──────┘          ↓
                         ProviderReadResult
                               ↓
                      NormalizedProjectContent
```

Le même consumer lit les deux formats sans `instanceof`, sans `switch(providerId)` et sans structure source spécifique.

Une même external key est namespacée par provider :

```text
(openspec, requirement, X) != (synthetic-json, requirement, X)
```

Aucun changement de `morpheus-domain` ou `morpheus-application` n'a été requis pour accueillir le second provider.

---

# 12. Persistance — décision M2-S8

Décision candidate ADR-0030 :

> **La persistance complète des entités normalisées est introduite en M3 avec `TemporalState`, `SpecificationVersion` et le membership `KnowledgeSnapshot`, pas à la fin de M2.**

M2 persiste déjà :

```text
projects
knowledge snapshot metadata
provider-scoped entity identity bindings
schema migration ledger
```

M2 ne crée pas encore :

```text
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
external_references
provenance/evidence
```

Raison : créer ces tables sans leur ownership version/snapshot obligerait à figer un schéma immédiatement remodelé par M3.

Conséquence acceptée : le contenu métier normalisé complet reste reconstructible depuis les sources jusqu'à M3 ; les identités stables restent persistées.

---

# 13. Frontière M2 -> M3

M3 doit prendre en charge :

```text
TemporalState
CURRENT / PROPOSED / HISTORICAL
SpecificationVersion complet
KnowledgeSnapshot complet
membership contenu -> snapshot/version
activation atomique du contenu observable
ChangeLifecycleState complet
application/promotion des deltas
rétention/comparaison de snapshots
premières migrations métier versionnées
```

M2 n'implémente aucune de ces responsabilités par anticipation.

---

# 14. Hors périmètre confirmé

```text
TraceabilityLink / graphe de traçabilité complet -> M4
recherche métier / context query               -> M5
sync incrémentale complète                      -> M7
analyse des changements                         -> M8
CLI stabilisée / packaging natif                -> M9
MCP                                              -> M10
API                                              -> M11
MINOS / NEXUS / JARVIS                          -> M12-M14
```

La distribution est néanmoins gouvernée par ADR-0027 :

```text
Native-first
Container-supported
```

---

# 15. Limites et warnings connus

Non bloquants pour M2 :

1. Xerial SQLite sous JDK 24 signale que `System::load` nécessitera à terme `--enable-native-access=ALL-UNNAMED` ; à traiter avant stabilisation runtime/CLI.
2. ArchUnit émet un warning SLF4J NOP ; aucun logger n'est ajouté uniquement pour le masquer.
3. le provider synthétique est `verification-only`, pas un format public supporté.
4. le contenu métier normalisé complet n'est pas encore persisté ; ADR-0030 le place en M3.

---

# 16. Checklist de sortie M2

| Condition | État avant gate final |
|---|---|
| domaine provider-neutral | ✅ |
| provenance + evidence | ✅ |
| identité stable provider-scoped | ✅ |
| identité persistée | ✅ |
| OpenSpec current | ✅ |
| changement normalisé | ✅ |
| deltas ADDED/MODIFIED/REMOVED | ✅ |
| ExternalReference | ✅ |
| résolution externe optionnelle | ✅ |
| lecture provider unifiée | ✅ |
| source partielle explicite | ✅ |
| diagnostics structurés | ✅ |
| politique AcceptanceCriterion | ✅ |
| second provider anti-lock-in | ✅ |
| décision persistance métier | ✅ candidate ADR-0030 |
| gate final M2 | ⏳ |

---

# 17. Gate final requis

Commande officielle :

```text
.\mvnw.cmd clean test
```

Baseline attendue après S7 :

```text
94 tests
Failures = 0
Errors   = 0
BUILD SUCCESS
```

Le gate doit être exécuté sur la branche M2-S8 après les seuls changements de gouvernance/documentation.

---

# 18. Porte de sortie

Si le gate final reste vert :

```text
M2 = VALIDÉE
ADR-0030 = ACCEPTÉE — M2
issue #9 = FERMÉE
M3 = AUTORISÉE
```

Sinon :

```text
M2 reste ouverte
M3 reste bloquée
```
