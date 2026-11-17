# Tâches : n'envoyer le jeton Sonar qu'à l'hôte configuré

## 1. Reproduction

- [ ] 1.1 Écrire le test d'exécution de D3 (rapport planté dans un module, rapport à hôte étranger, hôte à préfixe
  trompeur, `curl` factice en tête de `PATH`) et constater qu'il échoue sur le script courant — exigences :
  `sonar-quality-gate-classification` › Le classificateur DOIT lire le rapport de tâche au chemin exact du module racine,
  et › Le classificateur NE DOIT PAS envoyer le jeton Sonar hors de l'hôte configuré ; vérification : test à écrire,
  rouge avant 2.1, ignoré avec sa raison sans `bash`
- [ ] 1.2 Ajouter à `SonarQualityGateClassificationContractTest` les assertions du chemin exact, de la constante d'hôte
  et de son égalité avec `-Dsonar.host.url` de `ci.yml` (D2) — vérification : rouges avant 2.1. Fichier de
  gouvernance : justification dans la PR

## 2. Correctif

- [ ] 2.1 Lire `report-task.txt` au chemin exact (D1) et refuser par une erreur nommée tout `ceTaskUrl` ou `serverUrl`
  hors de l'hôte attendu, avant la première requête — vérification : tests de 1.1 et 1.2 verts ; le script reste ASCII
  et s'exécute sous `bash -n`
- [ ] 2.2 Créer `docs/developer/specifications/sonar-quality-gate-classification.md` depuis le gabarit de
  `docs/developer/specifications/README.md` et l'ajouter à l'index — vérification : `RepositoryDocumentationCoherenceTest`
- [ ] 2.3 Sur la première exécution du job `sonar` de `main` qui passe par le classificateur (ou une exécution
  `workflow_dispatch`), constater que le rapport est trouvé à la racine — vérification : journal du job

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate send-the-sonar-token-only-to-the-configured-host --strict` — vérification : aucun
  avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est
  marqué « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest='SonarQualityGateClassificationContractTest,AuditHardeningWorkflowContractTest'`
  puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
