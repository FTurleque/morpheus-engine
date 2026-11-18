# Tâches : refuser une assertion d'ordre dont une ancre manque

## 1. Aide et règle

- [ ] 1.1 Écrire `TextOrderTest` (à écrire) : ancre de gauche absente, ancre de droite absente, ordre inverse, ordre
  juste — exigence : `architecture-rule-vacuity-guard` › A textual order assertion of the suite SHALL fail when one of
  its anchors is absent ; vérification : ne compile pas avant 1.2
- [ ] 1.2 Écrire `TextOrder.assertBefore` (D1) — vérification : `TextOrderTest` vert
- [ ] 1.3 Écrire `AnEmptyArchitectureRuleFailsTest#noOrderAssertionComparesAPositionOutsideTheHelper` (à écrire, D2 ;
  fichier de gouvernance, justification et intention dans la PR) — vérification : rouge sur l'arbre actuel, nomme au
  moins les quatre sites vides

## 2. Migration des sites

- [ ] 2.1 Migrer `RemoteAccessJournalArchitectureTest:69`, `DependencyCheckWorkflowContractTest:499` et `:811`,
  `AuditHardeningWorkflowContractTest:127`, puis les sites gardés à la main (D1) — exigence : idem ; vérification :
  règle de 1.3 verte, chaque classe migrée verte, aucune méthode `@Test` retirée
- [ ] 2.2 Casser chaque site vide une fois (D3) — vérification : quatre échecs qui nomment l'ancre, trace dans la PR
- [ ] 2.3 Mettre à jour `docs/developer/specifications/architecture-rule-vacuity-guard.md` — vérification :
  `RepositoryDocumentationCoherenceTest` vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate refuse-an-order-assertion-whose-anchor-is-missing --strict` — vérification : aucun
  avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a une preuve
  existante, manuelle nommée, ou « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et lire
  `morpheus-architecture-tests/target/enforcement-verdict.txt` — vérification : vert, aucune violation ni
  désynchronisation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
