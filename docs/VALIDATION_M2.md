# Validation M2 — MORPHEUS

Statut : **M2 VALIDÉE — M3 autorisée**

Date : 22 juillet 2026

---

# 1. Décision

La phase **M2 — Ingestion et modèle normalisé** est validée.

Question de sortie :

> **Une source supportée peut-elle être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, tandis qu'un second provider démontre que le modèle n'est pas verrouillé sur OpenSpec ?**

Réponse :

```text
OUI — sous les frontières M2/M3/M4 documentées
```

M3 est autorisée.

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
| M2-S8 | audit final + décision persistance | #19 | ADR-0030 | 94/94 PASS |

ADR-0027 est transversale : distribution **native-first / container-supported**.

---

# 3. Domaine normalisé validé

M2 stabilise :

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

`AcceptanceCriterion` reste un concept du domaine cible, mais aucune instance n'est dérivée automatiquement d'un `Scenario`.

Invariant :

```text
Scenario != AcceptanceCriterion
```

---

# 4. OpenSpec — provider de référence

Provider M2 :

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

Un schéma inconnu est rejeté explicitement et aucune version de format absente n'est inventée.

Oracle `openspec-basic` :

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
ADDED
MODIFIED
REMOVED
```

Invariant critique :

```text
baseline RequirementId == MODIFIED delta RequirementId
baseline content       != MODIFIED delta content
```

Le delta est normalisé mais non appliqué en M2.

---

# 5. Identité stable

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
continuité explicite uniquement
aucune fusion par titre/chemin/similarité
```

Les bindings persistent après fermeture/réouverture SQLite.

---

# 6. Provenance et evidence

```text
entity
  ↓
Provenance
  ↓ EvidenceId
Evidence
  ↓
SourceLocator + SourceRange + hash optionnel
```

Le contenu importé reste rattaché à sa preuve source.

---

# 7. ExternalReference

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

# 8. Lecture provider unifiée

M2 sépare :

```text
SpecificationProvider.probe()
        !=
SpecificationContentReader.read()
```

Statuts de lecture :

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

Fixture `openspec-partial` :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

Le contenu valide reste exploitable malgré la lecture partielle.

---

# 9. Anti-lock-in OpenSpec

S7 introduit le module compilé :

```text
morpheus-provider-synthetic
```

Il est `verification-only`.

Architecture prouvée :

```text
OpenSpec source ─────┐
                     ├──> SpecificationContentReader
Synthetic JSON ──────┘          ↓
                         ProviderReadResult
                               ↓
                      NormalizedProjectContent
```

Le même consumer lit les deux formats sans `instanceof`, sans branche provider-specific et sans type source dans les contrats applicatifs.

Une même external key reste provider-scoped :

```text
(openspec, requirement, X) != (synthetic-json, requirement, X)
```

Aucun changement de `morpheus-domain` ou `morpheus-application` n'a été requis pour accueillir le second provider.

---

# 10. Décision de persistance — ADR-0030

Décision acceptée :

> **La persistance complète des entités normalisées est introduite en M3 avec `TemporalState`, `SpecificationVersion` et le membership `KnowledgeSnapshot`, pas à la fin de M2.**

M2 persiste :

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

Raison : créer ces tables sans ownership version/snapshot figerait un schéma immédiatement remodelé par M3.

Conséquence acceptée : le contenu métier normalisé complet reste reconstructible depuis les sources jusqu'à M3 ; les identités stables restent persistées.

---

# 11. Frontière M2 -> M3

M3 possède :

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

M4 conserve la traçabilité métier complète (`TraceabilityLink`, `AFFECTS`, traversées de graphe, etc.).

---

# 12. Gate final M2-S8

Commande officielle :

```text
.\mvnw.cmd clean test
```

Environnement observé :

```text
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats par module :

```text
Domain                                   4 tests
Application                             38 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             6 tests
Architecture tests                      13 tests
-----------------------------------------------
TOTAL                                   94/94 PASS
Failures                                    0
Errors                                      0
Skipped                                     0
BUILD SUCCESS
```

Gate terminé le 22 juillet 2026 à 22:38:52 +02:00.

S8 ne modifie aucun fichier Java, POM ou migration SQL : le gate final valide donc le même produit M2 que celui démontré en S7, avec uniquement la gouvernance de sortie finalisée.

---

# 13. Warnings connus non bloquants

1. Xerial SQLite sous JDK 24 signale que `System::load` nécessitera à terme `--enable-native-access=ALL-UNNAMED` ; à traiter avant stabilisation runtime/CLI.
2. ArchUnit émet un warning SLF4J NOP ; aucun logger n'est ajouté uniquement pour le masquer.
3. le provider synthétique est `verification-only`, pas un format public supporté.
4. le contenu métier normalisé complet n'est pas encore persisté ; ADR-0030 le place en M3.

---

# 14. Checklist de sortie M2

| Condition | État |
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
| décision persistance métier | ✅ ADR-0030 |
| gate final M2 | ✅ 94/94 |

---

# 15. Porte de sortie

```text
M2 = VALIDÉE
ADR-0030 = ACCEPTÉE — M2
M2-S8 = VALIDÉ
M3 = AUTORISÉE
```

La fermeture administrative de l'issue #9 et le merge de la PR #19 matérialisent cette décision dans GitHub.
