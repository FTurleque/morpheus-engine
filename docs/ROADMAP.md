# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0, M0 et M1 validés ; M2 en validation finale**

Date de dernière mise à jour : 22 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves. Un jalon n'est pas considéré terminé parce que son code existe : il doit disposer de contrats stables, de tests, d'ADR cohérentes et d'une preuve de sortie explicite.

---

# 1. Où en sommes-nous exactement ?

## 1.1 Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | `VALIDATION_C0.md` |
| M0 | Faisabilité technique | ✅ VALIDÉ | `VALIDATION_M0.md` |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | `VALIDATION_M1.md`, 42/42 tests |
| **M2** | **Ingestion et modèle normalisé** | **🚧 VALIDATION FINALE** | 7/8 slices, dernière baseline 94/94 |
| M3 | État temporel, lifecycle, snapshots, versions | ⏳ BLOQUÉ PAR GATE M2-S8 | ouverture après `VALIDATION_M2.md` |
| M4 | Traçabilité | ⏳ PLANIFIÉ | après modèle temporel stable |
| M5 | Requêtes et contexte compact | ⏳ PLANIFIÉ | après M4 |
| M6 | Qualité / couverture | ⏳ PLANIFIÉ | après primitives de requête |
| M7 | Synchronisation incrémentale | ⏳ PLANIFIÉ | après snapshots stables |
| M8 | Analyse des changements | ⏳ PLANIFIÉ | après M3/M4 |
| M9 | CLI stabilisée et distribution native | ⏳ PLANIFIÉ | après cœur fonctionnel |
| M10 | MCP | ⏳ PLANIFIÉ | natif stdio d'abord |
| M11 | API / headless | ⏳ PLANIFIÉ | Docker officiel si justifié |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS reste autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références :

- [`VALIDATION_C0.md`](VALIDATION_C0.md)
- [`VALIDATION_M0.md`](VALIDATION_M0.md)
- [`VALIDATION_M1.md`](VALIDATION_M1.md)
- [`VALIDATION_M2.md`](VALIDATION_M2.md) — candidat jusqu'au gate final
- [`roadmap/M2_EXECUTION.md`](roadmap/M2_EXECUTION.md)
- [`roadmap/DEPLOYMENT.md`](roadmap/DEPLOYMENT.md)
- [`adr/README.md`](adr/README.md)
- issue M2 : `#9`

---

## 1.2 Tableau de bord M2

M2 est découpée en **8 slices de pilotage**.

| Slice | Contenu | Statut | PR | ADR | Gate observé |
|---|---|---|---|---|---|
| M2-S1 | Domaine normalisé courant + provenance/evidence | ✅ VALIDÉ | #10 | ADR-0022 | 48/48 |
| M2-S2 | Identité persistante provider-scoped | ✅ VALIDÉ | #11 | ADR-0023 | 58/58 |
| M2-S3 | ChangeProposal / Constraint / DesignDecision / Task | ✅ VALIDÉ | #12 | ADR-0024 | 64/64 |
| M2-S4 | Requirement deltas ADDED/MODIFIED/REMOVED | ✅ VALIDÉ | #13 | ADR-0025 | 70/70 |
| M2-S5 | ExternalReference + résolution optionnelle | ✅ VALIDÉ | #15 | ADR-0026 | 76/76 |
| M2-S6 | Lecture unifiée + sources partielles + diagnostics | ✅ VALIDÉ | #17 | ADR-0028 | 84/84 |
| M2-S7 | Second provider synthétique + preuve anti-lock-in | ✅ VALIDÉ | #18 | ADR-0029 | 94/94 |
| **M2-S8** | **Audit final + décision persistance + `VALIDATION_M2.md`** | **🚧 ACTIF** | à ouvrir | ADR-0030 | gate final 94 attendu |

Position actuelle :

```text
M2 : [██████████████████░░] 7 / 8 slices validés
```

ADR-0027 est transversale : distribution **native-first / container-supported**.

---

## 1.3 Porte de sortie M2

M2 est terminé uniquement lorsque :

> **Une source supportée peut être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, et un second provider démontre que le modèle n'est pas verrouillé sur OpenSpec.**

Checklist :

| Condition | État |
|---|---|
| domaine courant provider-neutral | ✅ |
| provenance + evidence | ✅ |
| identité stable provider-scoped | ✅ |
| identité persistée | ✅ |
| changements / contraintes / décisions / tâches | ✅ |
| deltas ADDED/MODIFIED/REMOVED | ✅ |
| ExternalReference | ✅ |
| résolution externe optionnelle | ✅ |
| source partielle + diagnostics explicites | ✅ |
| politique AcceptanceCriterion explicite | ✅ |
| second provider anti-lock-in | ✅ |
| décision persistance métier | ✅ candidate ADR-0030 |
| validation finale M2 | ⏳ gate S8 |

M3 ne démarre pas avant le dernier gate et la validation explicite de `VALIDATION_M2.md`.

---

# 2. Principes de séquencement

```text
Documenter d'abord
Décider ensuite
Implémenter après
Prouver avant de valider
```

Et :

```text
OpenSpec-first, not OpenSpec-locked
MORPHEUS owns intent/specification semantics
MINOS owns code intelligence
NEXUS owns context selection/ranking/compression
JARVIS owns orchestration
```

Invariants transverses :

```text
DomainIdentity != EntityVersion != SourceLocator != ExternalReference
Scenario != AcceptanceCriterion par défaut
provider facts != MORPHEUS domain
backend details != domain
```

---

# 3. C0 — Cadrage fonctionnel et architectural ✅

## Objectif

Définir précisément ce que MORPHEUS doit fournir avant le développement fonctionnel.

## Livré

- cahier des charges ;
- position dans l'écosystème ;
- périmètre MVP ;
- modèle de domaine ;
- cycle de vie des changements ;
- cas d'usage prioritaires ;
- stratégie `SpecificationProvider` ;
- négociation de capacités ;
- étude OpenSpec ;
- stratégie `SpecificationKnowledgeStore` ;
- stratégie d'identité ;
- snapshots/versionnement ;
- modèle de traçabilité ;
- critères de validation ;
- ADR structurantes ;
- matrice d'expérimentation M0.

Porte : **VALIDÉE**.

---

# 4. M0 — Faisabilité technique ✅

## Objectif

Valider les choix structurants par expérimentation réelle.

## Preuves principales

- OpenSpec reference provider ;
- second provider synthétique expérimental ;
- normalisation expérimentale ;
- UUIDv7 ;
- séparation CURRENT / PROPOSED / HISTORICAL ;
- lifecycle ;
- snapshots ;
- traçabilité ;
- store mémoire ;
- SQLite candidat ;
- graph DB non nécessaire au MVP ;
- recherche lexicale suffisante ;
- diagnostics ;
- contexte compact ;
- incrémental ;
- références externes optionnelles.

Porte : **ADOPTER AVEC CONTRAINTES — VALIDÉE**.

---

# 5. M1 — Discovery, providers et fondation store ✅

## Objectif

Détecter les sources de spécification et sélectionner un provider compatible de manière déterministe.

## Livré

- registre local des projets ;
- workspace discovery explicit-first ;
- fallback `.git` structurel sans binaire Git ;
- `SpecificationProviderRegistry` ;
- probes/capabilities ;
- required/preferred capabilities ;
- local-first ;
- remote opt-in ;
- diagnostics structurés ;
- provider OpenSpec `spec-driven` read-only ;
- `SourceLocator` provider-neutral ;
- UUIDv7 ;
- memory store de référence ;
- SQLite derrière le port ;
- migrations V001/V002 puis V003 pour les identity bindings ;
- tests d'architecture.

Preuve finale :

```text
42/42 tests PASS
BUILD SUCCESS
```

Porte : **VALIDÉE — M2 AUTORISÉE**.

---

# 6. M2 — Ingestion et modèle normalisé 🚧 VALIDATION FINALE

## Objectif

Transformer les sources supportées en concepts MORPHEUS indépendants du provider.

## Domaine stabilisé

```text
ProjectSpecification
Specification
Requirement
RequirementDelta
ChangeProposal
Constraint
Scenario
DesignDecision
ImplementationTask
Evidence
Provenance
ExternalReference
```

`AcceptanceCriterion` reste produit uniquement lorsqu'une sémantique provider explicite le justifie.

## Frontière provider

```text
source externe
    ↓
SpecificationProvider.probe()
    ↓
SpecificationContentReader.read()
    ↓
ProviderReadResult
    ↓
anti-corruption boundary
    ↓
NormalizedProjectContent
```

Aucun type OpenSpec ni Synthetic ne traverse `com.morpheus.domain` ou les contrats applicatifs.

## Lecture explicite

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

## Oracle OpenSpec

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

La baseline et un delta `MODIFIED` peuvent partager le même `RequirementId` sans partager le même contenu.

## Source partielle

`openspec-partial` :

```text
CURRENT_SPECIFICATIONS = READ      1
REQUIREMENTS           = READ      2
SCENARIOS              = PARTIAL   1
CHANGES                = ABSENT    0
PARTIAL_INGESTION
```

## ExternalReference

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

La résolution externe reste optionnelle et une panne du système cible ne rend pas MORPHEUS indisponible.

## Anti-lock-in

```text
OpenSpec source ─────┐
                     ├──> SpecificationContentReader
Synthetic JSON ──────┘     ProviderReadResult
```

Le provider synthétique est `verification-only` ; il a prouvé l'architecture sans modifier le domaine ou l'application.

## Persistance à la sortie M2

Déjà persisté :

```text
projects
knowledge snapshot metadata
provider-scoped identity bindings
schema migration ledger
```

ADR-0030 propose de différer les tables métier complètes à M3 afin de les concevoir directement avec :

```text
TemporalState
SpecificationVersion
KnowledgeSnapshot
snapshot/version membership
```

## Ce qui reste hors M2

```text
TemporalState complet                  -> M3
SpecificationVersion complet           -> M3
KnowledgeSnapshot complet              -> M3
promotion/application des deltas       -> M3
premières tables métier versionnées    -> M3
TraceabilityLink / AFFECTS             -> M4
recherche métier                       -> M5
CLI stabilisée                         -> M9
MCP / API                              -> M10/M11
```

## Porte M2-S8

```text
.\mvnw.cmd clean test
94 tests attendus
```

Après gate vert :

```text
VALIDATION_M2.md = VALIDÉE
ADR-0030          = ACCEPTÉE
issue #9          = CLOSED
M3                = AUTORISÉE
```

---

# 7. M3 — État temporel, lifecycle, snapshots, versions ⏳

## Objectif

Reconstruire l'état de référence, les évolutions proposées et l'historique sans ambiguïté, puis persister ce contenu avec une ownership version/snapshot explicite.

## M3-S1 — TemporalState et versions

```text
CURRENT
PROPOSED
HISTORICAL
SpecificationVersion
```

À prouver :

- une même identité logique peut exister dans plusieurs versions ;
- version logique != snapshot technique ;
- aucune source directory ne devient implicitement un `TemporalState`.

## M3-S2 — ChangeLifecycleState

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

Règle : `COMPLETED` ne promeut jamais automatiquement une spécification en `CURRENT`.

## M3-S3 — Composition et persistance des snapshots

- construire ;
- valider ;
- publier ;
- activer atomiquement ;
- conserver predecessor ;
- interdire une visibilité partielle ;
- introduire les premières migrations métier ;
- rattacher chaque occurrence persistée à sa version/snapshot.

## M3-S4 — Application/promotion des deltas

- `ADDED` ;
- `MODIFIED` ;
- `REMOVED` ;
- maintien simultané current/proposed avant promotion ;
- aucune promotion implicite à `COMPLETED`.

## M3-S5 — Historique / archives / comparaison

- `UNCHANGED` ;
- `MOVED/RENAMED` si identité suffisante ;
- rétention ;
- historique explicable ;
- rebuild depuis sources.

## Porte de sortie M3

`get_current_specification` ne doit jamais contenir implicitement un delta seulement proposé, même pendant une resynchronisation.

---

# 8. M4 — Traçabilité ⏳

## Objectif

Relier les éléments de spécification et expliquer leur origine.

## Périmètre

```text
TraceabilityLink
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
DEPENDS_ON
AFFECTS
DECIDED_BY
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

À livrer : direction canonique, inverse éventuel, origine, résolution, confiance, preuves, références cassées et chemins de traçabilité.

Porte : un `trace <requirement>` interne doit produire un sous-graphe normalisé et explicable.

---

# 9. M5 — Requêtes et contexte compact ⏳

## Objectif

Rendre MORPHEUS directement interrogeable par humains, scripts et agents.

Primitives prévues :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
```

Inclut recherche lexicale, pagination, limites, JSON compact, warnings et provenance.

---

# 10. M6 — Qualité et couverture ⏳

À détecter :

- requirements orphelins ;
- tâches sans requirement ;
- critères d'acceptation non reliés ;
- critères non vérifiés ;
- changements incomplets ;
- décisions sans justification ;
- références cassées ;
- couverture de traçabilité ;
- blocages de transition ;
- diagnostics déterministes vs heuristiques.

---

# 11. M7 — Synchronisation incrémentale et fraîcheur ⏳

## Périmètre

- empreintes ;
- source revisions ;
- fichiers ajoutés/modifiés/supprimés ;
- mouvements/renommages ;
- archives ;
- `INCREMENTAL_READ` ;
- invalidation ;
- watcher local ;
- format/version modifié ;
- fallback full rebuild ;
- métriques de fraîcheur.

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

---

# 12. M8 — Analyse des changements ⏳

## Objectif

Analyser l'étendue fonctionnelle/documentaire d'un changement.

Inclut comparaison current/proposed, exigences ajoutées/modifiées/supprimées, contraintes affectées, décisions, critères, changements dépendants, chemins explicatifs et limites explicites des inférences.

L'analyse du code reste MINOS.

---

# 13. M9 — CLI stabilisée et distribution native ⏳

Commandes candidates :

```text
morpheus project add
morpheus project list
morpheus sync
morpheus specs
morpheus requirements
morpheus change get
morpheus change list
morpheus change status
morpheus constraints
morpheus acceptance
morpheus decisions
morpheus tasks
morpheus trace
morpheus context
morpheus versions
morpheus inspect
morpheus health
```

Distribution à prouver selon ADR-0027 :

```text
archive portable
runtime Java embarqué
jlink / jpackage ou équivalent à évaluer
installateur Windows/Linux approprié
CLI locale sans Docker obligatoire
```

Les mutations restent hors périmètre sans ADR d'écriture acceptée.

---

# 14. M10 — Serveur MCP ⏳

Outils candidats :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Cible de distribution :

```text
stdio natif d'abord
mode headless / conteneur seulement si le transport réseau le justifie
```

Aucune logique métier essentielle dans les handlers MCP.

---

# 15. M11 — API / headless ⏳

## Périmètre

- projets ;
- spécifications ;
- requirements ;
- changements ;
- contraintes ;
- critères ;
- traçabilité ;
- versions ;
- contexte ;
- synchronisation ;
- diagnostics ;
- DTO stables.

Le framework serveur reste différé jusqu'à ce jalon.

Cible de distribution : image Docker officielle **si** le mode headless/API est démontré utile, avec workspace montable en lecture seule et données persistantes externalisées.

---

# 16. M12 — Intégration MINOS ⏳

## Objectif

Relier intention et code sans fusionner les domaines.

```text
ExternalReference(system=MINOS, ...)
```

Périmètre : symboles, fichiers, modules, tests, Requirement → code, ChangeProposal → code, AcceptanceCriterion → tests, références non résolues conservées, indisponibilité MINOS tolérée.

---

# 17. M13 — Intégration NEXUS ⏳

MORPHEUS fournit intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins.

NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

---

# 18. M14 — Orchestration JARVIS ⏳

MORPHEUS peut exposer :

```text
change state
allowed transitions
blocking conditions
acceptance status
unresolved references
specification context
```

JARVIS décide de la séquence d'actions. MORPHEUS ne contient aucune logique JARVIS.

---

# 19. Explorations futures

Non engagées dans la roadmap principale :

- génération assistée par LLM ;
- recherche sémantique / embeddings ;
- contradictions avancées ;
- Jira / GitHub Issues / trackers ;
- éditeur visuel ;
- collaboration temps réel ;
- providers distants ;
- composition multi-provider de production ;
- fédération multi-projets ;
- event sourcing complet ;
- conformité automatique code ↔ spécification ;
- mutations orchestrées de specs par agents.

---

# 20. Règle de pilotage

À chaque slice :

```text
1. documenter l'invariant / ADR
2. implémenter le plus petit vertical slice
3. ajouter les preuves contractuelles
4. exécuter .\mvnw.cmd clean test
5. accepter l'ADR uniquement après preuve
6. merger
7. mettre à jour le tableau de bord
```

La ligne **M2-S8** est la seule ligne M2 encore active.
