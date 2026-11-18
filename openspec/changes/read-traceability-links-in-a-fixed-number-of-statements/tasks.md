# Tâches : lire les liens de traçabilité en un nombre fixe de requêtes

## 1. Reproduire avant de corriger

- [ ] 1.1 Écrire `SqliteTraceabilityReadCostTest` (à écrire, `morpheus-store-sqlite`, paquet `com.morpheus.store.sqlite.adapter`, patron de `SqliteTraceabilityBatchWriteCostTest`) : `prepareStatement` comptés pour une lecture sortante puis entrante de 3 liens × 1 preuve et de 300 liens × 4 preuves — exigence : `traceability-link-read-cost` › Reading the links of an endpoint SHALL cost a number of statements that does not depend on the links returned ; vérification : rouge sur `HEAD`, message qui nomme les deux comptes
- [ ] 1.2 Dans le même test, comparer chaque lien lu (preuves comprises) à ce qui a été écrit, y compris un lien partagé par deux snapshots — exigence : `traceability-link-read-cost` › A read of endpoint links SHALL return every link with all its evidence ; vérification : vert sur `HEAD`
- [ ] 1.3 Ajouter à `SqliteTraceabilityStorePersistenceTest` les cas snapshot inconnu (refus `snapshot not found for traceability link: `) et snapshot connu sans lien (liste vide) — exigence : idem ; vérification : vert sur `HEAD` (référence)

## 2. La correction

- [ ] 2.1 Lire les preuves des liens retournés par une seconde requête de la même sélection, ordonnée par `(link_id, evidence_id)`, regroupée en mémoire (design.md, D1) — exigences : les deux de `traceability-link-read-cost` ; vérification : 1.1, 1.2 verts
- [ ] 2.2 N'appeler `requireSnapshot` que sur une lecture vide (D2) — vérification : 1.1 et 1.3 verts
- [ ] 2.3 Ajouter un `EXPLAIN QUERY PLAN` de la lecture des preuves qui refuse un tri temporaire et un parcours sans index — vérification : vert
- [ ] 2.4 Casser la règle une fois : rétablir `evidence(linkId)` dans la boucle — vérification : 1.1 rouge en nommant les comptes, puis revert ; trace dans la Javadoc du test ou la PR
- [ ] 2.5 Écrire `TraceabilityLinkReadParityTest` (à écrire, `morpheus-architecture-tests`) : mêmes liens à preuves multiples dans les stores mémoire et SQLite, lectures dans les deux sens comparées — exigence : › A read of endpoint links SHALL return every link with all its evidence ; vérification : vert ; justification de PR pour un test ajouté sous `morpheus-architecture-tests`

## 3. Documentation durable

- [ ] 3.1 Créer `docs/developer/specifications/traceability-link-read-cost.md` (gabarit de `docs/developer/specifications/README.md`, diagramme de design.md) et l'ajouter à la section « Persistance SQLite et stores » de l'index — vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate read-traceability-links-in-a-fixed-number-of-statements --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-store-sqlite -Dtest=SqliteTraceabilityReadCostTest,SqliteTraceabilityBatchWriteCostTest,SqliteTraceabilityStorePersistenceTest`, puis `./mvnw clean verify` — vérification : vert sur Windows et Linux
- [ ] 4.4 Lancer `scripts\validate.cmd m19` et `bash ./scripts/validate-m19.sh` (sans `-Version`) — vérification : budget de traversée tenu, métrique avant / après citée dans la PR

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
