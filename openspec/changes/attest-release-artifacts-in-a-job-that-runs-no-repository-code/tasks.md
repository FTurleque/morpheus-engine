# Tâches : attester les artefacts de release dans un job qui n'exécute aucun code du dépôt

## 1. Contrat de workflow

- [ ] 1.1 Écrire dans `AuditHardeningWorkflowContractTest` la méthode qui découpe `release.yml` par job et refuse une
  permission d'attestation dans un job qui porte `run:` ou `actions/checkout`, ou au niveau global, avec l'assertion de
  non-vacuité (D2) — exigence : `release-attestation-isolation` › Seul un job qui n'exécute aucun code du dépôt DOIT
  porter les permissions d'attestation ; vérification : test à écrire, rouge sur l'arbre courant (les jobs
  `linux-release` et `windows-release` sont nommés). Fichier de gouvernance : justification dans la PR
- [ ] 1.2 Ajouter l'assertion `digest-mismatch: error` sur le téléchargement du job d'attestation — exigence :
  `release-attestation-isolation` › Le job d'attestation DOIT attester les octets téléversés par le build ;
  vérification : rouge tant que le job n'existe pas

## 2. Workflow de release

- [ ] 2.1 Retirer `id-token: write`, `attestations: write`, les étapes `actions/attest` et la copie des paquets des
  jobs `linux-release` et `windows-release` ; ajouter le job `attest` (D1) et faire dépendre `publish` de lui —
  vérification : tests de 1.1 et 1.2 verts ; `AuditHardeningWorkflowContractTest` et `ProductReleaseContractTest`
  verts (liste des actifs inchangée)
- [ ] 2.2 Rejouer le workflow sur un tag de répétition ou relire la première release suivante avec
  `scripts/verify-release-provenance.sh` et `.ps1` — vérification : chaque artefact vérifie son attestation
- [ ] 2.3 Créer `docs/developer/specifications/release-attestation-isolation.md` depuis le gabarit de
  `docs/developer/specifications/README.md` (exigences, diagramme, traçabilité), l'ajouter à l'index, et dire dans
  `docs/developer/BUILD_AND_TEST.md` et `docs/developer/PRODUCTION_INTEGRITY.md` quel job atteste — vérification :
  `RepositoryDocumentationCoherenceTest`

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate attest-release-artifacts-in-a-job-that-runs-no-repository-code --strict` —
  vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est
  marqué « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest='AuditHardeningWorkflowContractTest,ProductReleaseContractTest'`
  puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts

## Workflow follow-up

- Le change `verify-the-checksums-of-every-resolved-maven-artifact` (AUD-DEP-38) touche aussi `release.yml` : appliquer
  les deux dans des lots successifs.
- Archiver le changement après la revue, sur demande explicite.
