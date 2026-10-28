---
id: ARCH-0137
title: "Lectures métier et recherche sur le snapshot publié"
domain: architecture
rules: false
---

# Lectures métier et recherche sur le snapshot publié

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0122, ADR-0043

## Context

MORPHEUS répond à des questions métier (une exigence, sa trace, un changement et son contexte, les contraintes,
décisions, tâches et critères d'acceptation d'un changement) et recherche des exigences par leur texte, à partir de
ce qu'il a ingéré. Un projet porte plusieurs snapshots de connaissance, dont un seul est `ACTIVE` ; les snapshots
`RETIRED` restent l'historique publié, et les états `BUILDING`, `VALIDATING`, `READY` et `FAILED` sont des étapes
techniques de publication (ADR-0143). Le stockage durable versionné des `Requirement` existe déjà :
`VersionedRequirementStore`, `RequirementVersionRecord`, occurrences `CURRENT` / `PROPOSED`, rattachement à un
`KnowledgeSnapshot` et à une `SpecificationVersion`.

Une lecture qui verrait un snapshot technique exposerait un contenu jamais publié. Une lecture qui confondrait « aucun
snapshot actif » et « entité absente » ferait conclure à tort qu'une exigence ou un changement n'existe pas. Une
lecture qui rendrait vide un snapshot publié dont la projection métier manque confondrait un store incomplet avec une
absence métier.

Une recherche lexicale déterministe suffit au produit ; la recherche sémantique n'est pas requise (ADR-0004).
`find_requirements` doit être produit sans second index ni table dédiée, et sans confondre filtrage lexical et
classement de pertinence, qui est le rôle de NEXUS.

Options écartées :

- lire le dernier snapshot construit, quel que soit son état : expose des candidats non validés ;
- réactiver un snapshot `RETIRED` pour le lire : modifie l'état publié pour une simple lecture ;
- rendre une page ou un résultat vide quand la projection manque : masque une corruption ;
- reconstituer les exigences affectées par un changement à partir de son texte, de titres ou de clés : inférence non
  déterministe, contraire à ADR-0004 ;
- pour la recherche : recherche sémantique, classement pondéré, FTS SQLite, index Lucene, stemming, fuzzy matching.

## Decision

### 1. Sélection du snapshot

Une lecture métier a deux formes possibles :

- la forme par projet, que toute lecture offre, lit exclusivement le snapshot `ACTIVE` du projet ;
- la forme par snapshot explicite, offerte quand la lecture donne accès à l'historique (recherche d'exigences, trace
  d'une exigence, contexte d'un changement, spécification, analyses de qualité), n'accepte que `ACTIVE` ou `RETIRED`
  et refuse `BUILDING`, `VALIDATING`, `READY` et `FAILED` par une `KnowledgeStoreException` qui nomme le snapshot et
  son état. Un identifiant de snapshot inconnu est refusé de la même façon.

Un snapshot `RETIRED` se lit sans être réactivé. Un snapshot technique non publié n'est pas une source de requête
métier.

### 2. Pas d'`ACTIVE` n'est pas une entité absente

La forme par projet rend `Optional.empty()` quand le projet n'a pas de snapshot `ACTIVE`. Quand le snapshot publié
est trouvé, le résultat porte ses métadonnées (`KnowledgeSnapshotMetadata`) et l'absence de l'entité y est
explicite : `SnapshotItemResult.item` vide pour un getter, `ChangeContextResult.change` vide pour le contexte d'un
changement, page vide avec `totalMatches = 0` pour une liste filtrée par un `ChangeId` inconnu. Aucun objet n'est
fabriqué. `trace(requirement)` fait exception : son résultat est vide aussi quand l'exigence n'a pas d'occurrence
`CURRENT` (point 5).

### 3. Projection manquante = échec explicite

Un snapshot publié sans projection `SnapshotBusinessContent` lève une `KnowledgeStoreException` (« published snapshot
has no business-content projection »).

### 4. Getters et listes

`BusinessContentQueryService` porte les getters et les listes. Une spécification est adressée par son
`SpecificationId`, un projet pouvant en porter plusieurs. Les listes (changements ; contraintes, décisions de
conception, tâches d'un changement ; critères d'acceptation du projet, d'un changement ou d'une exigence) sont
filtrées, triées par identité de domaine, puis paginées (point 9). Un `Scenario` n'est jamais présenté comme un
critère d'acceptation : les critères sont ceux du modèle (ADR-0081).

### 5. `trace(requirement)`

`TraceRequirementService` porte la trace, exposée par `TraceRequirementQueryService` qui lui délègue sans rien
ajouter. La racine est toujours `REQUIREMENT` + `RequirementId`, et la trace n'a lieu que si une occurrence `CURRENT`
de cette identité existe dans le snapshot adressé ; sinon le résultat est vide. La profondeur est validée strictement
positive avant toute recherche de snapshot. La traversée est `BIDIRECTIONAL` et bornée (ADR-0040) : elle montre les
liens entrants et sortants sans persister d'arête inverse. Le résultat porte le snapshot, l'occurrence `CURRENT`, le
sous-graphe et une vue externe (`ExternalTraceabilityView`) par lien du sous-graphe qui cible une
`EXTERNAL_REFERENCE`, triée par `TraceabilityLinkId`. Aucun lien n'est modifié.

### 6. Contexte d'un changement

`ChangeContextQueryService` agrège, pour un seul snapshot publié, le changement, ses liens `AFFECTS` **directs
sortants** vers une exigence, les exigences `CURRENT` que ces liens résolvent (jamais `PROPOSED`, jamais par
correspondance de texte, de titre ou de clé), ses contraintes, décisions de conception et tâches filtrées par
`ChangeId`, un sous-graphe borné `BIDIRECTIONAL` enraciné sur le changement, et une vue externe par référence externe
du sous-graphe. Les liens `AFFECTS` bruts restent dans le résultat, de sorte qu'une cible sans occurrence `CURRENT`
reste visible. Les exigences affectées sont calculées par `AFFECTS` même si le filtre de relations de l'appelant
exclut `AFFECTS` : ce filtre gouverne le sous-graphe, pas les faits de l'agrégat. Une tâche n'est jamais reliée au
changement par une arête synthétique.

### 7. Références externes

Une lecture réutilise `ExternalTraceabilityQueryService.inspect(...)` et ne déclenche aucune résolution externe :
`REFERENCE_UNVALIDATED`, `REFERENCE_UNRESOLVED`, `REFERENCE_RESOLVED`, `REFERENCE_STALE` et `BROKEN_REFERENCE`
restent visibles tels quels (ADR-0142).

### 8. Recherche lexicale des exigences

`RequirementQueryService` (`com.morpheus.application.query`), neutre vis-à-vis des providers et des backends, offre
les deux formes du point 1 :

```text
findActive(projectId, query, pageRequest)
findSnapshot(snapshotId, query, pageRequest)
```

- **Occurrences requêtables** : seulement `TemporalState.CURRENT`. Les `PROPOSED` ne sont jamais incluses :
  `PROPOSED never leaks into CURRENT`.
- **Corpus** : strictement `key` (si présent), `title` et `statement`. La provenance est rendue dans le `Requirement`
  mais n'est pas un texte de recherche.
- **Normalisation** (`RequirementSearchQuery`), identique pour la requête et chaque champ : `strip` Unicode,
  minuscules via `Locale.ROOT`, découpage sur les blancs Unicode Java, termes vides ignorés. Aucun stemming, fuzzy
  matching, distance d'édition, embedding sémantique, expansion par LLM ni tokenisation propre à un provider.
- **Termes** : tous doivent être présents dans le corpus normalisé (`term1 AND term2 AND ...`), chacun par
  correspondance de sous-chaîne déterministe. Une requête vide rend tous les requirements `CURRENT` du snapshot
  adressé.
- **Ordre** : le résultat global, avant pagination, est ordonné uniquement par `RequirementId`. Il n'existe ni score
  ni classement global : `lexical filtering != ranking NEXUS`.
- **Backend** : la recherche réutilise `SpecificationKnowledgeStore` et `VersionedRequirementStore`, sans table
  SQLite ni index dédié.

### 9. Pagination

`PageRequest(offset, limit)` exige `offset >= 0` et `1 <= limit <= PageRequest.MAX_LIMIT`. La pagination est
appliquée **après** filtrage et tri déterministe. `SnapshotPage` porte le snapshot, les éléments, la requête de page,
`totalMatches` et `hasMore` ; `RequirementSearchPage` porte `items`, la requête de page, `totalMatches` et `hasMore`,
et refuse à la construction un `hasMore` incohérent avec les bornes et le total. Le vocabulaire de page exposé par les
transports suit ADR-0151.

### 10. Déterminisme et parité

Toute collection rendue est triée explicitement par identité (`RequirementId`, `ConstraintId`, `DesignDecisionId`,
`TaskId`, `TraceabilityLinkId`). La logique de lecture est entièrement dans `morpheus-application`, au-dessus des
ports `SpecificationKnowledgeStore`, `SnapshotBusinessContentStore`, `VersionedRequirementStore`,
`TraceabilityStore` et `ExternalReferenceStore` : le store mémoire et le store SQLite rendent le même résultat, y
compris après fermeture et réouverture de la base.

La même politique de snapshot gouverne l'analyse de changement (ADR-0138) et les analyses de qualité (ADR-0124).

## Do's and Don'ts

### Do

- Offrir pour chaque lecture métier une forme par projet (`ACTIVE`) et, quand l'historique est lisible, une forme par
  snapshot explicite limitée à `ACTIVE` et `RETIRED`.
- Rendre `Optional.empty()` pour « pas de snapshot `ACTIVE` » et un résultat porteur du snapshot pour « entité
  absente ».
- Lever une `KnowledgeStoreException` quand un snapshot publié n'a pas de projection métier.
- Trier toute liste par identité de domaine avant de la paginer avec `PageRequest`.
- Borner `limit` par `PageRequest.MAX_LIMIT` et rejeter un `offset` négatif.
- Résoudre les exigences affectées par les seuls liens `AFFECTS` directs sortants et l'occurrence `CURRENT`.
- Garder dans le résultat les liens `AFFECTS` dont la cible ne se résout pas.
- Normaliser requête et corpus de recherche de la même façon (`strip`, `Locale.ROOT`, blancs Unicode), et exiger
  tous les termes (ET logique) en correspondance de sous-chaîne.
- Rendre le même résultat avec le store mémoire et le store SQLite, y compris après réouverture.

### Don't

- Ne jamais lire ni interroger un snapshot `BUILDING`, `VALIDATING`, `READY` ou `FAILED`.
- Ne jamais réactiver un snapshot `RETIRED` pour le lire.
- Ne jamais rendre une occurrence `PROPOSED` dans une lecture métier ou une recherche.
- Ne jamais déduire une exigence affectée du texte, du titre ou de la clé.
- Ne jamais présenter un `Scenario` comme un critère d'acceptation.
- Ne jamais déclencher une résolution externe ni modifier un lien pendant une lecture.
- Ne jamais rendre une page vide pour masquer une projection manquante.
- Ne pas utiliser la provenance comme texte de recherche.
- Ne pas introduire de score, de stemming, de fuzzy matching ni d'index dédié dans la recherche lexicale.

## Consequences

### Positives

- Un consommateur sait toujours de quel snapshot vient une réponse et pourquoi elle est vide.
- L'historique publié est lisible sans toucher à l'état d'activation.
- Les lectures sont indépendantes du backend : un même jeu de ports suffit, sans nouvelle structure de persistance.
- La recherche est simple, explicable et reproductible.

### Négatives

- Chaque lecture répète la vérification d'état du snapshot dans son service.
- Les deltas d'exigence ne sont pas restitués par le contexte d'un changement : seuls les liens `AFFECTS` publiés le
  sont.
- Pas de classement de pertinence : l'ordre par identité ne met pas en tête le meilleur résultat.
- Correspondance de sous-chaîne seule : pas de tolérance aux fautes ni aux variantes morphologiques.

### Risques

- Une nouvelle lecture qui oublierait la vérification d'état exposerait un snapshot technique : aucune règle
  transverse ne l'interdit, chaque service le prouve par son test de contrat.
- Une tâche dont le changement n'a aucun lien `AFFECTS` n'apparaît reliée à aucune exigence ; c'est voulu, et c'est
  ADR-0124 qui le signale.
- Confondre le filtrage lexical avec un classement NEXUS ; l'ordre par `RequirementId` et l'absence de score le
  préviennent.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceRequirementFinalValidationTest.java` :
  parité mémoire/SQLite, isolation de l'`ACTIVE`, `RETIRED` lisible, `READY` et snapshots techniques refusés, filtres
  de relation, exigence absente, réouverture SQLite avec références `UNRESOLVED` et `BROKEN_REFERENCE`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/BusinessContentQueryContractTest.java` et
  `BusinessContentQueryBackendParityTest.java` (même répertoire) : `ACTIVE` par défaut, historique, distinction
  pas d'`ACTIVE` / entité absente, ordre et pagination, parité des stores.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeContextQueryContractTest.java` : agrégat
  mono-snapshot, `AFFECTS` directs et `CURRENT` seuls, cible cassée conservée, filtre de trace sans perte des faits,
  références externes non résolues visibles, parité et réouverture SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RequirementQueryContractTest.java` : parité
  mémoire / SQLite, absence de fuite `PROPOSED` / `RETIRED` / `READY` dans `findActive`, acceptation de `RETIRED` et
  rejet des snapshots techniques, recherche insensible à la casse en ET sans fuzzy, pagination après tri par
  identité, bornes invalides rejetées et absence d'`ACTIVE` explicite, réouverture SQLite.
- Constructeurs compacts de `PageRequest` et `RequirementSearchPage` : bornes et cohérence de `hasMore`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PagedResponseVocabularyArchitectureTest.java` :
  vocabulaire de page des réponses des transports.
- Revue : toute nouvelle lecture métier applique la politique de snapshot ci-dessus ; aucune règle d'architecture ne
  le vérifie de façon transverse.

## References

- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — local-first, pas de LLM dans le cœur.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — distinction courant / proposé.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des exigences.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié et rollback logique.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — traçabilité par snapshot.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée et déterministe.
- [ADR-0142](DATA-0142-references-externes.md) — références externes par snapshot.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — projection du contenu métier.
- [ADR-0142](DATA-0142-references-externes.md) — résolution externe sans mutation.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — taxonomie de traçabilité.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité, findings et faits tri-état.
- [ADR-0151](BE-0151-pagination-des-collections-et-journaux.md) — pagination des collections et journaux.
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — analyse de changement.
- `contracts/public-surfaces.tsv` (ligne `requirement.find`) — surfaces CLI, MCP et HTTP de la recherche.
- Preuves : [VALIDATION_M4](../../project/validation/VALIDATION_M4.md),
  [VALIDATION_M5](../../project/validation/VALIDATION_M5.md).
