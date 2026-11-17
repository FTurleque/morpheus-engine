# Tâches : vérifier les sommes de contrôle de chaque artefact Maven résolu

## 1. Décision du mainteneur

- [ ] 1.1 Faire trancher D2 (release sans cache, ou sommes de confiance) par le mainteneur et l'inscrire dans design.md
  — vérification : D2 passe à [DÉCISION] ; si l'option b est retenue, la seconde exigence du spec delta est réécrite et
  `openspec validate verify-the-checksums-of-every-resolved-maven-artifact --strict` repasse

## 2. Sommes strictes

- [ ] 2.1 Écrire le test de cohérence de D3 (volet `.mvn/maven.config`) et constater son échec sur l'arbre courant —
  exigence : `maven-artifact-integrity` › Toute résolution Maven du dépôt DOIT échouer sur une somme de contrôle absente
  ou fausse ; vérification : test à écrire, rouge avant 2.2. Fichier de gouvernance : justification dans la PR
- [ ] 2.2 Créer `.mvn/maven.config` avec `--strict-checksums`, puis lancer `./mvnw -Dmaven.repo.local=<dossier vide du
  scratchpad> clean verify` sous Linux et sous Windows — vérification : test de 2.1 vert ; build à cache vide vert sur
  les deux plateformes, ou liste des artefacts sans somme remontée au mainteneur avant fusion
- [ ] 2.3 Rejouer l'essai d'altération sur un miroir `file:` jetable déclaré dans un `settings.xml` du scratchpad,
  dépôt local vide — exigence : même exigence, scénario « Un artefact altéré sur le dépôt distant est refusé au
  téléchargement » ; vérification : la résolution échoue et nomme l'artefact (relevé dans la PR)

## 3. Build de release (option retenue en 1.1)

- [ ] 3.1 Écrire le volet `release.yml` du test de D3 et constater son échec sur `cache: maven` des deux jobs —
  exigence : `maven-artifact-integrity` › Le build de release NE DOIT PAS restaurer un cache Maven rempli par une autre
  exécution ; vérification : test à écrire, rouge avant 3.2
- [ ] 3.2 Retirer `cache: maven` des jobs de release (option a) — vérification : test de 3.1 vert ;
  `AuditHardeningWorkflowContractTest` et `ProductReleaseContractTest` verts ; durée du build de release relevée sur la
  première exécution
- [ ] 3.3 Créer `docs/developer/specifications/maven-artifact-integrity.md` depuis le gabarit de
  `docs/developer/specifications/README.md`, l'ajouter à l'index, décrire le réglage dans
  `docs/developer/BUILD_AND_TEST.md` — vérification : `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate verify-the-checksums-of-every-resolved-maven-artifact --strict` — vérification :
  aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est
  marqué « preuve manquante »
- [ ] 4.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts

## Workflow follow-up

- Le change `attest-release-artifacts-in-a-job-that-runs-no-repository-code` (AUD-DEP-33) touche aussi `release.yml` :
  appliquer les deux dans des lots successifs.
- Archiver le changement après la revue, sur demande explicite.
