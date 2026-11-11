# ADR-0122 — Les lectures métier ne voient que le snapshot publié

> **Archivée.** Cette décision est consolidée dans [ADR-0137](../ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0137**
- Date : 2026-10-24
- Remplace : ADR-0042, ADR-0045, ADR-0046

## Context

MORPHEUS répond à des questions métier (une exigence, sa trace, un changement et son contexte, les contraintes,
décisions, tâches et critères d'acceptation d'un changement) à partir de ce qu'il a ingéré. Un projet porte plusieurs
snapshots de connaissance, dont un seul est `ACTIVE` ; les snapshots `RETIRED` restent l'historique publié, et les
états `BUILDING`, `VALIDATING`, `READY` et `FAILED` sont des étapes techniques de publication (ADR-0120).

Une lecture qui verrait un snapshot technique exposerait un contenu jamais publié. Une lecture qui confondrait « aucun
snapshot actif » et « entité absente » ferait conclure à tort qu'une exigence ou un changement n'existe pas. Une
lecture qui rendrait vide un snapshot publié dont la projection métier manque confondrait un store incomplet avec une
absence métier.

Options écartées :

- lire le dernier snapshot construit, quel que soit son état : expose des candidats non validés ;
- réactiver un snapshot `RETIRED` pour le lire : modifie l'état publié pour une simple lecture ;
- rendre une page ou un résultat vide quand la projection manque : masque une corruption ;
- reconstituer les exigences affectées par un changement à partir de son texte, de titres ou de clés : inférence non
  déterministe, contraire à ADR-0004.

## Decision

1. **Sélection du snapshot.** Une lecture métier a deux formes possibles :
   - la forme par projet, que toute lecture offre, lit exclusivement le snapshot `ACTIVE` du projet ;
   - la forme par snapshot explicite, offerte quand la lecture donne accès à l'historique (trace d'une exigence,
     contexte d'un changement, spécification, analyses de qualité), n'accepte que `ACTIVE` ou `RETIRED` et refuse
     `BUILDING`, `VALIDATING`, `READY`
     et `FAILED` par une `KnowledgeStoreException` qui nomme le snapshot et son état. Un identifiant de snapshot
     inconnu est refusé de la même façon.
   Un snapshot `RETIRED` se lit sans être réactivé.
2. **Pas d'`ACTIVE` n'est pas une entité absente.** La forme par projet rend `Optional.empty()` quand le projet n'a
   pas de snapshot `ACTIVE`. Quand le snapshot publié est trouvé, le résultat porte ses métadonnées
   (`KnowledgeSnapshotMetadata`) et l'absence de l'entité y est explicite : `SnapshotItemResult.item` vide pour un
   getter, `ChangeContextResult.change` vide pour le contexte d'un changement, page vide avec `totalMatches = 0` pour
   une liste filtrée par un `ChangeId` inconnu. Aucun objet n'est fabriqué. `trace(requirement)` fait exception :
   son résultat est vide aussi quand l'exigence n'a pas d'occurrence `CURRENT` (point 5).
3. **Projection manquante = échec explicite.** Un snapshot publié sans projection `SnapshotBusinessContent` lève une
   `KnowledgeStoreException` (« published snapshot has no business-content projection »).
4. **Getters et listes** (`BusinessContentQueryService`). Une spécification est adressée par son `SpecificationId`,
   un projet pouvant en porter plusieurs. Les listes (changements ; contraintes, décisions de conception, tâches d'un
   changement ; critères d'acceptation du projet, d'un changement ou d'une exigence) sont filtrées, triées par
   identité de domaine, puis paginées par `PageRequest` (offset positif ou nul, limite bornée par
   `PageRequest.MAX_LIMIT`). `SnapshotPage` porte le snapshot, les éléments, la requête de page, `totalMatches` et
   `hasMore`. Un `Scenario` n'est jamais présenté comme un critère d'acceptation : les critères sont ceux du modèle
   (ADR-0081).
5. **`trace(requirement)`** (`TraceRequirementService`, exposé par `TraceRequirementQueryService` qui lui délègue
   sans rien ajouter). La racine est toujours `REQUIREMENT` + `RequirementId`, et la trace n'a lieu que si une
   occurrence `CURRENT` de cette identité existe dans le snapshot adressé ; sinon le résultat est vide. La profondeur
   est validée strictement positive avant toute recherche de snapshot. La traversée est `BIDIRECTIONAL` et bornée
   (ADR-0040) : elle montre les liens entrants et sortants sans persister d'arête inverse. Le résultat porte le
   snapshot, l'occurrence `CURRENT`, le sous-graphe et une vue externe (`ExternalTraceabilityView`) par lien du
   sous-graphe qui cible une `EXTERNAL_REFERENCE`, triée par `TraceabilityLinkId`. Aucun lien n'est modifié.
6. **Contexte d'un changement** (`ChangeContextQueryService`). Pour un seul snapshot publié, le résultat agrège le
   changement, ses liens `AFFECTS` **directs sortants** vers une exigence, les exigences `CURRENT` que ces liens
   résolvent (jamais `PROPOSED`, jamais par correspondance de texte, de titre ou de clé), ses contraintes, décisions
   de conception et tâches filtrées par `ChangeId`, un sous-graphe borné `BIDIRECTIONAL` enraciné sur le changement,
   et une vue externe par référence externe du sous-graphe. Les liens `AFFECTS` bruts restent dans le résultat, de
   sorte qu'une cible sans occurrence `CURRENT` reste visible. Les exigences affectées sont calculées par `AFFECTS`
   même si le filtre de relations de l'appelant exclut `AFFECTS` : ce filtre gouverne le sous-graphe, pas les faits
   de l'agrégat. Une tâche n'est jamais reliée au changement par une arête synthétique.
7. **Références externes.** Une lecture réutilise `ExternalTraceabilityQueryService.inspect(...)` et ne déclenche
   aucune résolution externe : `REFERENCE_UNVALIDATED`, `REFERENCE_UNRESOLVED`, `REFERENCE_RESOLVED`,
   `REFERENCE_STALE` et `BROKEN_REFERENCE` restent visibles tels quels (ADR-0071).
8. **Déterminisme et parité.** Toute collection rendue est triée explicitement par identité (`RequirementId`,
   `ConstraintId`, `DesignDecisionId`, `TaskId`, `TraceabilityLinkId`). La logique de lecture est entièrement dans
   `morpheus-application`, au-dessus des ports `SpecificationKnowledgeStore`, `SnapshotBusinessContentStore`,
   `VersionedRequirementStore`, `TraceabilityStore` et `ExternalReferenceStore` : le store mémoire et le store SQLite
   rendent le même résultat, y compris après fermeture et réouverture de la base.

La même politique de snapshot gouverne la recherche lexicale (ADR-0043), l'analyse de changement (ADR-0056) et les
analyses de qualité (ADR-0124).

## Do's and Don'ts

### Do

- Offrir pour chaque lecture métier une forme par projet (`ACTIVE`) et, quand l'historique est lisible, une forme par
  snapshot explicite limitée à `ACTIVE` et `RETIRED`.
- Rendre `Optional.empty()` pour « pas de snapshot `ACTIVE` » et un résultat porteur du snapshot pour « entité
  absente ».
- Lever une `KnowledgeStoreException` quand un snapshot publié n'a pas de projection métier.
- Trier toute liste par identité de domaine avant de la paginer avec `PageRequest`.
- Résoudre les exigences affectées par les seuls liens `AFFECTS` directs sortants et l'occurrence `CURRENT`.
- Garder dans le résultat les liens `AFFECTS` dont la cible ne se résout pas.

### Don't

- Ne jamais lire un snapshot `BUILDING`, `VALIDATING`, `READY` ou `FAILED`.
- Ne jamais réactiver un snapshot `RETIRED` pour le lire.
- Ne jamais rendre une occurrence `PROPOSED` dans une lecture métier.
- Ne jamais déduire une exigence affectée du texte, du titre ou de la clé.
- Ne jamais présenter un `Scenario` comme un critère d'acceptation.
- Ne jamais déclencher une résolution externe ni modifier un lien pendant une lecture.
- Ne jamais rendre une page vide pour masquer une projection manquante.

## Consequences

### Positives

- Un consommateur sait toujours de quel snapshot vient une réponse et pourquoi elle est vide.
- L'historique publié est lisible sans toucher à l'état d'activation.
- Les lectures sont indépendantes du backend : un même jeu de ports suffit.

### Négatives

- Chaque lecture répète la vérification d'état du snapshot dans son service.
- Les deltas d'exigence ne sont pas restitués par le contexte d'un changement : seuls les liens `AFFECTS` publiés le
  sont.

### Risques

- Une nouvelle lecture qui oublierait la vérification d'état exposerait un snapshot technique : aucune règle
  transverse ne l'interdit, chaque service le prouve par son test de contrat.
- Une tâche dont le changement n'a aucun lien `AFFECTS` n'apparaît reliée à aucune exigence ; c'est voulu, et c'est
  ADR-0124 qui le signale.

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
- Revue : toute nouvelle lecture métier applique la politique de snapshot ci-dessus ; aucune règle d'architecture ne
  le vérifie de façon transverse.

## References

- [ADR-0004](../SEC-0004-local-first-no-llm-core.md)
- [ADR-0034](0034-versioned-requirement-persistence.md)
- [ADR-0038](../DATA-0038-snapshot-scoped-traceability-persistence.md)
- [ADR-0040](../BE-0040-bounded-deterministic-traceability-traversal.md)
- [ADR-0041](0041-snapshot-external-reference-traceability.md)
- [ADR-0043](0043-deterministic-requirement-lexical-query.md)
- [ADR-0044](0044-snapshot-business-content-projection.md)
- [ADR-0056](0056-deterministic-current-proposed-change-analysis.md)
- [ADR-0071](0071-live-external-resolution-without-snapshot-mutation.md)
- [ADR-0081](../DATA-0081-first-class-acceptance-verification-evidence.md)
- [ADR-0118](../ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md)
- [ADR-0120](0120-snapshots-versionnes-et-activation-atomique.md)
- [ADR-0123](../ARCH-0123-vues-compactes-et-json-canonique.md)
- [ADR-0124](../ARCH-0124-qualite-findings-et-faits-tri-etat.md)
- Preuves de jalon : [VALIDATION_M4](../../../project/validation/VALIDATION_M4.md),
  [VALIDATION_M5](../../../project/validation/VALIDATION_M5.md)
