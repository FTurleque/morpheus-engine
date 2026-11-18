# Tâches : gérer dans le POM racine tout plugin Maven qu'un workflow appelle

## 1. Règle générale

- [ ] 1.1 Écrire le test de cohérence de D2 (paquet `d2`, `@CoherenceCheck`) et constater qu'il échoue sur l'arbre
  courant en nommant `ci.yml` et l'appel Sonar — exigence : `dependency-version-management` › Un plugin Maven qu'un
  workflow ou un validateur appelle par coordonnées DOIT avoir sa version décidée par le POM racine ; vérification :
  test à écrire, rouge avant 2.1 ; violation plantée sur la version OWASP d'un validateur consignée dans la Javadoc.
  Fichier de gouvernance : justification dans la PR

## 2. Scanner Sonar

- [ ] 2.1 Ajouter la propriété `sonar.maven.plugin.version` et l'entrée de `<pluginManagement>`, retirer la version de
  l'appel de `ci.yml`, faire lire la propriété par `AuditHardeningWorkflowContractTest` (D1) — vérification : test de
  1.1 et `AuditHardeningWorkflowContractTest` verts ; `./mvnw help:effective-pom` montre la version gérée
- [ ] 2.2 Constater, sur la première exécution du job `sonar` de `main` après fusion, que le scanner résolu est celui
  du POM — vérification : journal du job (ligne de chargement du plugin)
- [ ] 2.3 Mettre à jour `.claude/rules/build.md` (table des versions, phrase sur la déclaration des plugins) et
  `docs/developer/specifications/dependency-version-management.md` (exigence, traçabilité) — vérification :
  `GovernanceSurfaceCoherenceTest`, `RepositoryDocumentationCoherenceTest`

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate manage-every-maven-plugin-a-workflow-invokes-in-the-root-pom --strict` —
  vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve
- [ ] 3.3 Lancer `./mvnw dependency:analyze` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : zéro
  avertissement, verts

## Workflow follow-up

- Le change `send-the-sonar-token-only-to-the-configured-host` (AUD-DEP-37) touche le même job : appliquer les deux dans
  des lots successifs.
- Archiver le changement après la revue, sur demande explicite.
