# ADR-0043 — Recherche lexicale déterministe et pagination des requirements

> **Archivée.** Cette décision est consolidée dans [ADR-0137](../ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0137**
- Date : 2026-07-23

## Context

Une recherche lexicale déterministe suffit au produit ; la recherche sémantique n'est pas requise (ADR-0004). Le
stockage durable versionné des `Requirement` existe déjà : `VersionedRequirementStore`, `RequirementVersionRecord`,
occurrences `CURRENT` / `PROPOSED`, rattachement à un `KnowledgeSnapshot` et à une `SpecificationVersion`.

`find_requirements` doit donc être produit sans second index ni table dédiée, et sans confondre filtrage lexical et
classement de pertinence (rôle de NEXUS).

## Decision

### Service

Un service applicatif neutre vis-à-vis des providers et des backends, `RequirementQueryService`
(`com.morpheus.application.query`), offre deux entrées explicites :

```text
findActive(projectId, query, pageRequest)
findSnapshot(snapshotId, query, pageRequest)
```

- `findActive` sélectionne uniquement le snapshot `ACTIVE` du projet ; l'absence de snapshot actif est une réponse
  vide explicite.
- `findSnapshot` accepte uniquement un snapshot `ACTIVE` ou `RETIRED` et rejette `BUILDING`, `VALIDATING`, `READY`,
  `FAILED` ainsi qu'un snapshot inconnu. Il permet une lecture historique explicite d'un snapshot `RETIRED` sans le
  réactiver. Un snapshot technique non publié n'est pas une source de requête métier.

### Occurrences requêtables

La recherche ne porte que sur les occurrences `TemporalState.CURRENT`. Les `PROPOSED` ne sont jamais incluses :
`PROPOSED never leaks into CURRENT`.

### Corpus et normalisation

Le corpus d'un requirement est strictement `key` (si présent), `title` et `statement`. La provenance est retournée
dans le `Requirement` mais n'est pas un texte de recherche.

Pour chaque requête et chaque champ : `strip` Unicode, minuscules via `Locale.ROOT`, découpage sur les blancs Unicode
Java, termes vides ignorés (`RequirementSearchQuery`). Aucun stemming, fuzzy matching, distance d'édition, embedding
sémantique, expansion par LLM ni tokenisation propre à un provider.

### Sémantique des termes

Tous les termes doivent être présents dans le corpus normalisé (`term1 AND term2 AND ...`), chacun par correspondance
de sous-chaîne déterministe. Une requête vide retourne tous les requirements `CURRENT` du snapshot adressé.

### Ordre

Le résultat global, avant pagination, est ordonné uniquement par `RequirementId`. Il n'existe ni score ni classement
global : `lexical filtering != ranking NEXUS`.

### Pagination

`PageRequest(offset, limit)` exige `offset >= 0` et `1 <= limit <= PageRequest.MAX_LIMIT`. `RequirementSearchPage`
conserve `items`, la requête de page (`offset`, `limit`), `totalMatches` et `hasMore`, et refuse à la construction un
`hasMore` incohérent avec les bornes et le total. La pagination est appliquée **après** filtrage et tri déterministe.
Le vocabulaire de page exposé par les transports suit ADR-0131.

### Backend

La recherche réutilise `SpecificationKnowledgeStore` et `VersionedRequirementStore`, sans table SQLite dédiée. La
sémantique observable est identique avec `MemorySpecificationKnowledgeStore` et avec
`SqliteSpecificationKnowledgeStore` + `SqliteVersionedRequirementStore`.

### Hors du périmètre de cette décision

Recherche sémantique, classement pondéré, FTS SQLite, index Lucene, stemming, fuzzy matching, agrégation de contexte
et requêtes sur les autres familles métier.

## Do's and Don'ts

### Do

- Restreindre la recherche aux occurrences `CURRENT` d'un snapshot `ACTIVE` ou explicitement `RETIRED`.
- Normaliser requête et corpus de la même façon (`strip`, `Locale.ROOT`, blancs Unicode).
- Exiger tous les termes (ET logique) en correspondance de sous-chaîne.
- Trier par `RequirementId` avant de paginer.
- Borner `limit` par `PageRequest.MAX_LIMIT` et rejeter un `offset` négatif.
- Rendre le même résultat avec le store mémoire et le store SQLite, y compris après réouverture.

### Don't

- Ne pas inclure d'occurrence `PROPOSED` dans un résultat.
- Ne pas interroger un snapshot `BUILDING`, `VALIDATING`, `READY` ou `FAILED`.
- Ne pas réactiver un snapshot `RETIRED` pour le lire.
- Ne pas utiliser la provenance comme texte de recherche.
- Ne pas introduire de score, de stemming, de fuzzy matching ni d'index dédié dans cette recherche.

## Consequences

### Positives

- Recherche simple, explicable et reproductible.
- Aucune nouvelle structure de persistance.
- Parité mémoire / SQLite vérifiable.

### Négatives

- Pas de classement de pertinence : l'ordre par identité ne met pas en tête le meilleur résultat.
- Correspondance de sous-chaîne seule : pas de tolérance aux fautes ni aux variantes morphologiques.

### Risques

- Confondre ce filtrage avec un classement NEXUS ; l'ordre par `RequirementId` et l'absence de score le préviennent.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RequirementQueryContractTest.java` : parité
  mémoire / SQLite, absence de fuite `PROPOSED` / `RETIRED` / `READY` dans `findActive`, acceptation de `RETIRED` et
  rejet des snapshots techniques, recherche insensible à la casse en ET sans fuzzy, pagination après tri par
  identité, bornes invalides rejetées et absence d'`ACTIVE` explicite, réouverture SQLite.
- Constructeurs compacts de `PageRequest` et `RequirementSearchPage` : bornes et cohérence de `hasMore`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PagedResponseVocabularyArchitectureTest.java` :
  vocabulaire de page des réponses des transports.

## References

- [ADR-0004](../SEC-0004-local-first-no-llm-core.md) — local-first, pas de LLM dans le cœur.
- [ADR-0006](0006-current-vs-proposed-state.md) — distinction courant / proposé.
- [ADR-0120](0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0034](0034-versioned-requirement-persistence.md) — persistance versionnée des exigences.
- [ADR-0036](../DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié et rollback logique.
- [ADR-0131](0131-pagination-des-collections-et-journaux.md) — pagination des collections et journaux.
- [VALIDATION_M5](../../../project/validation/VALIDATION_M5.md) — preuve du jalon M5.
- `contracts/public-surfaces.tsv` (ligne `requirement.find`) — surfaces CLI, MCP et HTTP de la recherche.
