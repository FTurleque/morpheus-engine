# Tâches : borner les arêtes du runtime vers l'adaptateur du store SQLite

Prérequis : le sprint 5 de l'audit du 17/11/2026 (`AUD-PRF-21` à `AUD-PRF-26`) est appliqué ; il modifie les mêmes
fichiers de `morpheus-store-sqlite`, et la liste d'arêtes se mesure sur l'arbre où ce changement s'applique.

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md : option A (réemploi de `RUNTIME_ENTRY_POINTS`), B (liste d'arêtes sous un ADR
  consolidé qui remplace ARCH-0158, recommandée) ou C (troisième paquet) — vérification : décision consignée dans
  design.md ; si C est retenue, la spec delta et les tâches sont révisées avant toute autre tâche
- [ ] 1.2 Rédiger l'ADR consolidé à partir de `docs/architecture/adr/gabarit/template.md`, au numéro qui suit le plus
  haut attribué (registre et archive vérifiés le jour même), statut `Proposée`, qui reprend les décisions d'ARCH-0158
  et nomme la liste par `SqliteStorePackagesArchitectureTest.<CHAMP>` — exigence : `sqlite-store-packages` › Les arêtes
  du runtime vers l'adaptateur forment une liste exacte qui ne fait que rétrécir ; vérification : `AdrIndexCoherenceTest`,
  `AdrBriefingBudgetTest`, acceptation par le mainteneur avant fusion

## 2. Règle et liste

- [ ] 2.1 Écrire la méthode de `SqliteStorePackagesArchitectureTest` qui refuse une dépendance du runtime vers
  l'adaptateur hors des paires listées et compare la liste au bytecode dans les deux sens (D2, D3) — exigence :
  `sqlite-store-packages` › Le runtime du store SQLite ne dépend de son adaptateur que par des arêtes nommées ;
  vérification : verte sur le code en vigueur ; violation plantée dans une classe du runtime puis dans
  `SqliteServerMaintenance`, échec nommé constaté et consigné dans la Javadoc de la méthode et la PR, puis retiré
- [ ] 2.2 Écrire le doublon textuel sur les sources du runtime, hors commentaires (D3) — exigence : même exigence,
  scénario « Une arête qui ne laisse qu'une mention dans le texte » ; vérification : échec constaté sur une mention
  plantée, puis retirée
- [ ] 2.3 Déclarer la liste dans `ProductionClassListsArchitectureTest` comme compteur de dette sous l'ADR de 1.2
  (`debtCounterUnder` ou `debtCounterEnumeratedBy` selon que l'ADR énumère la liste), passer les deux listes
  d'ARCH-0158 sous ce même ADR, et ajouter la clé à `config/debt-counter-ratchets.properties` (justification de
  gouvernance dans la PR) — exigence : `sqlite-store-packages` › Les arêtes du runtime vers l'adaptateur forment une
  liste exacte qui ne fait que rétrécir ; vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=ProductionClassListsArchitectureTest`
- [ ] 2.4 Archiver ARCH-0158 (`git mv` vers `docs/architecture/adr/archive/`, statut `Remplacée par ADR-NNNN`, bandeau),
  une fois le nouvel ADR accepté, et suivre l'ancien chemin dans tout le dépôt — vérification : `AdrIndexCoherenceTest`,
  `RepositoryDocumentationCoherenceTest`, `OpenSpecDecisionRecordCitationTest`

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/sqlite-store-packages.md` (exigences, diagramme de design.md,
  traçabilité), `docs/developer/specifications/adapter-internal-boundaries.md`, `docs/architecture/adr/README.md`,
  `docs/architecture/arc42/09-decisions.md` et la section du store SQLite de `.claude/rules/architecture.md` —
  vérification : `RepositoryDocumentationCoherenceTest`, `GovernanceSurfaceCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate bound-the-runtime-to-adapter-edges-of-the-sqlite-store --strict` — vérification :
  aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D3 → tâche → test) —
  vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests` après un `./mvnw clean verify` — vérification : vert,
  `architectureTestsMinimum` de `config/m21-quality-ratchets.properties` non dépassé à la baisse ; `archgate check`
  sur un arbre propre

## Workflow follow-up

- Archiver le changement après la revue et l'acceptation de l'ADR, sur demande explicite.
- Après l'archivage, relire `openspec/specs/sqlite-store-packages/spec.md` et sa page durable.
