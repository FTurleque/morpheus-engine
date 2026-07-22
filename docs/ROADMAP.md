# Feuille de route — MORPHEUS

Statut : **Roadmap active — C0, M0 et M1 validés ; M2 en cours**

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
| **M2** | **Ingestion et modèle normalisé** | **🚧 EN COURS** | 4 slices validés, 70/70 tests |
| M3 | État temporel, lifecycle, snapshots, versions | ⏳ BLOQUÉ PAR M2 | ouverture après `VALIDATION_M2.md` |
| M4 | Traçabilité | ⏳ PLANIFIÉ | après modèle temporel stable |
| M5 | Requêtes et contexte compact | ⏳ PLANIFIÉ | après M4 |
| M6 | Qualité / couverture | ⏳ PLANIFIÉ | après primitives de requête |
| M7 | Synchronisation incrémentale | ⏳ PLANIFIÉ | après snapshots stables |
| M8 | Analyse des changements | ⏳ PLANIFIÉ | après M3/M4 |
| M9 | CLI stabilisée | ⏳ PLANIFIÉ | après cœur fonctionnel |
| M10 | MCP | ⏳ PLANIFIÉ | après API applicative stable |
| M11 | API | ⏳ PLANIFIÉ | framework différé jusque-là |
| M12 | MINOS | ⏳ PLANIFIÉ | intégration optionnelle |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS reste autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Références :

- [`VALIDATION_C0.md`](VALIDATION_C0.md)
- [`VALIDATION_M0.md`](VALIDATION_M0.md)
- [`VALIDATION_M1.md`](VALIDATION_M1.md)
- [`adr/README.md`](adr/README.md)
- issue M2 : `#9`

---

## 1.2 Tableau de bord M2

M2 est découpée en **8 slices de pilotage**. Ce découpage sert à visualiser l'avancement ; il ne représente pas une estimation linéaire de l'effort.

| Slice | Contenu | Statut | PR | ADR | Gate observé |
|---|---|---|---|---|---|
| M2-S1 | Domaine normalisé courant + provenance/evidence | ✅ VALIDÉ | #10 | ADR-0022 | 48/48 |
| M2-S2 | Identité persistante provider-scoped | ✅ VALIDÉ | #11 | ADR-0023 | 58/58 |
| M2-S3 | ChangeProposal / Constraint / DesignDecision / Task | ✅ VALIDÉ | #12 | ADR-0024 | 64/64 |
| M2-S4 | Requirement deltas ADDED/MODIFIED/REMOVED | ✅ VALIDÉ | #13 | ADR-0025 | 70/70 |
| **M2-S5** | **ExternalReference + résolution optionnelle** | **🚧 PROCHAIN / EN DÉMARRAGE** | à créer | ADR-0026 candidate | gate à définir |
| M2-S6 | Contrat de lecture unifié + sources partielles + diagnostics + catégories non supportées | ⬜ À FAIRE | — | à décider | — |
| M2-S7 | Second provider synthétique + preuve anti-lock-in | ⬜ À FAIRE | — | si nécessaire | — |
| M2-S8 | Revue de sortie M2 + décision persistance métier + `VALIDATION_M2.md` | ⬜ À FAIRE | — | revue ADR | — |

**Position actuelle : 4 slices validés sur 8 ; le slice M2-S5 démarre.**

---

## 1.3 Ce que M2 sait déjà faire

```text
OpenSpec schema=spec-driven
        ↓
provider-internal readers
        ↓
anti-corruption boundary
        ↓
NormalizedProjectContent
```

Le modèle de production contient déjà :

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

Les identités sont provider-neutral et UUIDv7. Les mappings externes sont persistés via :

```text
(providerId, entityType, externalId)
              ↓
PersistentEntityIdentityResolver
              ↓
DomainIdentity UUIDv7
```

La fixture `openspec-basic` produit actuellement :

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

Le requirement `auth-session/session-expiration` est déjà prouvé comme pouvant exister :

```text
baseline content
+
MODIFIED delta content
```

avec le **même `RequirementId`** mais deux contenus distincts, sans application implicite du delta.

---

## 1.4 Ce qu'il manque réellement pour fermer M2

### M2-S5 — ExternalReference et résolution optionnelle

À livrer :

```text
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolver
ResolverRegistry
résolution optionnelle
UNVALIDATED / UNRESOLVED / RESOLVED / STALE
NO_RESOLVER explicite
```

Invariants :

```text
DomainIdentity != ExternalReference
ExternalReference peut exister sans système cible
absence de MINOS/GitHub/Jira != panne MORPHEUS
resolver externe != dépendance du domaine
```

### M2-S6 — Contrat de lecture et ingestion partielle

À livrer :

- une entrée de lecture provider cohérente au-delà de `probe` ;
- déclaration explicite des catégories supportées / absentes ;
- résultat partiel non ambigu ;
- diagnostics `PARTIAL_INGESTION`, `UNRESOLVED_REFERENCE`, `BROKEN_REFERENCE`, etc. lorsque justifiés ;
- politique explicite pour `AcceptanceCriterion` : **jamais dérivé automatiquement d'un Scenario** ;
- fixture `openspec-partial` portée dans les tests Java.

### M2-S7 — Second provider / anti-lock-in

À livrer :

```text
OpenSpec provider ──┐
                    ├──> même forme de domaine MORPHEUS
Synthetic provider ─┘
```

Preuve attendue : aucun `if (openspec)` dans le domaine ou les contrats applicatifs publics.

### M2-S8 — Fermeture M2

À faire avant M3 :

- audit de l'issue #9 ;
- audit des ADR M2 ;
- décider si une persistance métier supplémentaire est réellement nécessaire avant M3 ;
- porter les invariants M0 restants pertinents ;
- créer `docs/VALIDATION_M2.md` ;
- mettre README et roadmap à jour ;
- fermer l'issue #9 uniquement si la porte de sortie est démontrée.

---

## 1.5 Porte de sortie M2

M2 est terminé uniquement lorsque :

> **Une source supportée peut être ingérée et normalisée dans un modèle MORPHEUS provider-neutral avec identités stables, provenance, preuves, références externes et diagnostics, et un second provider démontre que le modèle n'est pas verrouillé sur OpenSpec.**

Checklist de sortie :

| Condition | État |
|---|---|
| domaine courant provider-neutral | ✅ |
| provenance + evidence | ✅ |
| identité stable/persistante | ✅ |
| changements / contraintes / décisions / tâches | ✅ |
| deltas ADDED/MODIFIED/REMOVED | ✅ |
| ExternalReference | ⬜ |
| résolution externe optionnelle | ⬜ |
| source partielle + diagnostics explicites | ⬜ |
| politique AcceptanceCriterion explicite | ⬜ |
| second provider anti-lock-in | ⬜ |
| validation finale M2 | ⬜ |

**M3 ne démarre pas avant que cette checklist soit satisfaite ou qu'une ADR explique explicitement un report de portée.**

---

# 2. Principes de séquencement

Les règles suivantes gouvernent tous les jalons :

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
- migrations V001/V002/V003 ;
- tests d'architecture.

Preuve finale :

```text
42/42 tests PASS
BUILD SUCCESS
```

Porte : **VALIDÉE — M2 AUTORISÉE**.

---

# 6. M2 — Ingestion et modèle normalisé 🚧

## Objectif

Transformer les sources supportées en concepts MORPHEUS indépendants du provider.

## Domaine cible M2

```text
ProjectSpecification
Specification
Requirement
RequirementDelta
ChangeProposal
Constraint
Scenario
DesignDecision
AcceptanceCriterion   (uniquement si sémantique explicite)
ImplementationTask
Evidence
Provenance
ExternalReference
```

## Frontière

```text
source externe
    ↓
SpecificationProvider / reader adapter
    ↓
provider facts internes
    ↓
normalisation
    ↓
MORPHEUS normalized domain
```

Aucun type OpenSpec ne doit traverser `com.morpheus.domain` ni les contrats applicatifs publics.

## Ce qui reste hors M2

```text
TemporalState complet                  -> M3
SpecificationVersion complet           -> M3
promotion/application des deltas       -> M3
TraceabilityLink / AFFECTS              -> M4
recherche métier                         -> M5
CLI stabilisée                           -> M9
MCP / API                                -> M10/M11
```

---

# 7. M3 — État temporel, lifecycle, snapshots et versions ⏳

## Objectif

Reconstruire l'état de référence, les évolutions proposées et l'historique sans ambiguïté.

## Candidats de slices M3

### M3-S1 — TemporalState et versions

```text
CURRENT
PROPOSED
HISTORICAL
SpecificationVersion
```

### M3-S2 — ChangeLifecycleState

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

### M3-S3 — Composition des snapshots

- construire ;
- valider ;
- publier ;
- activer atomiquement ;
- conserver predecessor ;
- interdire une visibilité partielle.

### M3-S4 — Application/promotion des deltas

- `ADDED` ;
- `MODIFIED` ;
- `REMOVED` ;
- maintien simultané current/proposed avant promotion ;
- aucune promotion implicite à `COMPLETED`.

### M3-S5 — Historique / archives / comparaison

- `UNCHANGED` ;
- `MOVED/RENAMED` si identité suffisante ;
- rétention ;
- historique explicable.

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

À livrer :

- direction canonique ;
- inverse éventuel ;
- origine ;
- résolution ;
- confiance ;
- preuves ;
- références cassées ;
- chemins de traçabilité ;
- change → requirement ;
- requirement → scenario/criterion/task ;
- design decision → change.

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

Inclut : recherche lexicale, pagination, limites, JSON compact, warnings et provenance.

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

Inclut :

- comparaison current/proposed ;
- exigences ajoutées/modifiées/supprimées ;
- contraintes affectées ;
- décisions ;
- critères ;
- changements dépendants ;
- chemins explicatifs ;
- limites explicites des inférences.

L'analyse du code reste MINOS.

---

# 13. M9 — CLI stabilisée ⏳

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

Aucune logique métier essentielle dans les handlers MCP.

---

# 15. M11 — API ⏳

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

MORPHEUS fournit : intention, requirements, contraintes, décisions, critères, tâches, provenance et chemins.

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
7. mettre à jour ce tableau de bord
```

La ligne **M2-S5** est la prochaine ligne active.