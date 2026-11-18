# Tâches : décider chaque invariant de persistance une seule fois, hors des stores (AUD-QUA-33)

Les groupes 2 à 7 sont des lots livrables séparément, dans cet ordre ; chacun laisse le build vert et fait baisser la
liste de dette du gate. Aucun message de refus, aucun type d'exception ni aucun ordre de contrôle ne change.

## 1. Décision du mainteneur

- [ ] 1.1 Trancher Q1 (où vit un contrôle remonté), Q2 (forme du gate et place du plafond), Q3 (frontière entre
  indépendant et propre au stockage, cas à arbitrer) et Q4 (ADR ou non) — vérification : réponses écrites dans
  design.md (D1 à D4), [QUESTION] remplacées par [DÉCISION]
- [ ] 1.2 Arrêter la classification message par message des 65 messages communs (D3), en commençant par relancer la
  mesure `comm -12` sur l'arbre du lot — vérification : table jointe à design.md, chaque message classé
  « propre au stockage » ou « à remonter »

## 2. Lot 1 — le gate

- [ ] 2.1 Écrire `PersistenceInvariantOwnershipTest` (à écrire) : mesure des littéraux communs d'au moins 25 caractères
  hors fragments de concaténation, table `STORAGE_OWNED` avec catégorie, liste `AWAITING_A_MOVE` exacte dans les deux
  sens, plafond (D2) — exigences : `persistence-invariant-ownership` › A storage-independent refusal SHALL NOT be written
  in a store, › The refusals awaiting their move SHALL only decrease ; justification de PR (test d'architecture) ;
  vérification : violation plantée (un message nouveau copié dans un store mémoire et son jumeau) constatée rouge puis
  retirée, trace dans la Javadoc ; liste et plafond égaux à la mesure
- [ ] 2.2 Si Q2 le retient, ajouter la clé du plafond à `config/debt-counter-ratchets.properties` — justification de PR
  (gouvernance) ; vérification : le test lit la clé et échoue si la liste la dépasse
- [ ] 2.3 Marquer le test `@CoherenceCheck` seulement s'il ne lit aucune source de production (il en lit : ne pas le
  marquer) — vérification : `CoherenceCheckMarkerTest`
- [ ] 2.4 Ajouter la règle à `.claude/rules/architecture.md` (une ligne, le nom du test) et créer
  `docs/developer/specifications/persistence-invariant-ownership.md` (gabarit de `docs/developer/specifications/README.md`)
  avec son entrée d'index — vérification : `GovernanceSurfaceCoherenceTest`, `RepositoryDocumentationCoherenceTest`

## 3. Lot 2 — politiques

- [ ] 3.1 Écrire les tests des contrôles remontés dans `morpheus-application` (pas de révision et de version, budgets,
  cibles et versions d'audit, cohérence d'identité) — exigence : `persistence-invariant-ownership` › A
  storage-independent invariant SHALL give the same refusal in both stores ; vérification : ne compilent pas avant 3.2
- [ ] 3.2 Écrire les contrôles sur `PolicyPack`, `PolicyBudgets` (D1) et les appeler depuis `MemoryPolicyPackStore` et
  `SqlitePolicyPackStore` à la place du code en ligne, ordre conservé — vérification : 3.1 vert,
  `PolicyPersistenceParityTest` et les tests de `policy-store-refusals` verts sans modification
- [ ] 3.3 Retirer les messages remontés d'`AWAITING_A_MOVE` et baisser le plafond dans le même commit — vérification :
  `PersistenceInvariantOwnershipTest`
- [ ] 3.4 Mettre à jour `docs/developer/specifications/policy-store-refusals.md` (où l'ordre est écrit) — vérification :
  `RepositoryDocumentationCoherenceTest`

## 4. Lot 3 — vues enregistrées

- [ ] 4.1 Écrire les tests des contrôles remontés (pas de révision, portée immuable, identité, budget `QueryBudgets`)
  dans leur module, et le cas « skips a revision » dans `SavedViewPersistenceParityTest` s'il manque — exigence :
  `persistence-invariant-ownership` › A storage-independent invariant SHALL give the same refusal in both stores ;
  vérification : rouges avant 4.2
- [ ] 4.2 Remonter les contrôles et les appeler depuis `MemorySavedViewStore` et `SqliteSavedViewStore` ; réduire la
  liste et le plafond — vérification : 4.1 vert, `PersistenceInvariantOwnershipTest` vert

## 5. Lot 4 — portfolio et état de synchronisation

- [ ] 5.1 Écrire `PortfolioFreshness` (test de monotonie, dans `morpheus-domain`) et `SyncStatePersistenceParityTest`
  (à écrire) — exigence : `persistence-invariant-ownership` › A storage-independent invariant SHALL give the same
  refusal in both stores ; vérification : rouges avant 5.2
- [ ] 5.2 Remonter la monotonie de fraîcheur et les bornes temporelles de synchronisation ; appeler depuis les quatre
  stores ; réduire la liste et le plafond — vérification : 5.1 vert, `PortfolioPersistenceParityTest` vert

## 6. Lot 5 — cycle de vie, références externes, traçabilité, contenu métier

- [ ] 6.1 Écrire les tests des contrôles remontés et un test de parité par famille qui n'en a pas (à écrire) —
  exigence : `persistence-invariant-ownership` › A storage-independent invariant SHALL give the same refusal in both
  stores ; vérification : rouges avant 6.2
- [ ] 6.2 Remonter les contrôles classés « à remonter » (D3), traiter les deux messages de résultat de cycle de vie selon
  Q3 ; réduire la liste et le plafond — vérification : 6.1 vert, tests existants des stores verts

## 7. Lot 6 — connaissance de spécification

- [ ] 7.1 Coordonner avec `publish-a-project-through-the-provider-it-was-registered-with` (même fichier,
  `putProject`) : mener ce lot avant ou après son lot de persistance, jamais en parallèle — vérification : branche de
  base relue avant de commencer
- [ ] 7.2 Écrire les tests des contrôles remontés (états réservés à l'activation, appartenance au même projet,
  correspondance de version de spécification) et un test de parité de la famille (à écrire) — exigence :
  `persistence-invariant-ownership` › A storage-independent invariant SHALL give the same refusal in both stores ;
  vérification : rouges avant 7.3
- [ ] 7.3 Remonter les contrôles et les appeler depuis `MemorySpecificationKnowledgeStore` et
  `SqliteSpecificationKnowledgeStore` ; vider `AWAITING_A_MOVE` ou le réduire à ce que Q3 laisse — vérification : 7.2
  vert, `PersistenceInvariantOwnershipTest` vert

## 8. Vérification d'ensemble

- [ ] 8.1 Lancer `openspec validate decide-each-persistence-invariant-once-outside-the-stores --strict` — vérification :
  aucun avertissement
- [ ] 8.2 Compléter la matrice de traçabilité de `docs/developer/specifications/persistence-invariant-ownership.md`
  (exigence → scénario → conception ou diagramme → tâche → preuve) — vérification : chaque exigence a une preuve
- [ ] 8.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts, aucun
  seuil de module ni crédit emprunté en défaut (`config/m21-per-module-coverage-floors.properties`,
  `BorrowedCoverageGateTest`), `morpheus-architecture-tests/target/enforcement-verdict.txt` sans violation
- [ ] 8.4 Relancer la mesure `comm -12` de la preuve de l'audit — vérification : seuls les messages de `STORAGE_OWNED`
  restent communs

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec consolidée `persistence-invariant-ownership` et sa page durable.
