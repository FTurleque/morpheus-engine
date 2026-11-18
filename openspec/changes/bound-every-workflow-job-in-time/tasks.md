# Tâches : borner chaque job de workflow dans le temps

## 1. Mesure et contrat

- [ ] 1.1 Mesurer la durée du job `verify` par voie sur les runs réussis des trente derniers jours (design.md,
  Performance) — exigence : `workflow-job-time-limit` › Every job of a workflow SHALL declare its own time limit ;
  vérification : maximum de chaque voie, date et runs consignés pour le commentaire de 2.1
- [ ] 1.2 Écrire `AuditHardeningWorkflowContractTest#everyJobOfEveryWorkflowDeclaresItsOwnTimeLimit` (à écrire, D2 ;
  fichier de gouvernance, justification de PR) — exigence : `workflow-job-time-limit` › Every job of a workflow SHALL
  declare its own time limit ; vérification : rouge sur l'arbre actuel, le message nomme `ci.yml` et `verify` seulement

## 2. Workflow et documentation

- [ ] 2.1 Ajouter `timeout-minutes` au job `verify` de `ci.yml`, avec le commentaire qui cite la mesure de 1.1 (D1) —
  vérification : test 1.2 vert ; planter puis retirer un job sans limite et une limite au seul niveau d'un `step`,
  constater les deux refus, consigner la trace dans la PR
- [ ] 2.2 Créer `docs/developer/specifications/workflow-job-time-limit.md` depuis le gabarit de
  `docs/developer/specifications/README.md` et l'ajouter à l'index de ce README — vérification :
  `RepositoryDocumentationCoherenceTest` vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate bound-every-workflow-job-in-time --strict` — vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a une preuve
  existante, manuelle nommée, ou « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=AuditHardeningWorkflowContractTest+D2RepositoryHardeningArchitectureTest`
  — vérification : verts
- [ ] 3.4 Constater sur la pull request du changement que les deux checks `exact-head` passent sous la limite —
  vérification : durées des deux jobs relevées dans la PR

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
