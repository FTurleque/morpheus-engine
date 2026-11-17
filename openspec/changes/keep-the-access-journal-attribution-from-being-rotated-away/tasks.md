# Tâches : empêcher qu'un flot de lectures chasse l'attribution du journal d'accès

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md (rétention séparée, borne de débit par principal, ou risque documenté seulement) — vérification : décision écrite dans la PR et reportée dans design.md
- [ ] 1.2 Ajuster le delta `specs/remote-access-journal/spec.md` à la décision et relancer `openspec validate keep-the-access-journal-attribution-from-being-rotated-away --strict` — vérification : aucun avertissement
- [ ] 1.3 Si A ou B : proposer l'ADR consolidé qui remplace ADR-0113 (`Proposée`, jamais `Acceptée` sans le mainteneur), avec sa ligne d'index ; si C : clarifier ADR-0113 sans changer son sens — vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest`

## 2. Reproduction (option A)

- [ ] 2.1 Écrire `RemoteAccessJournalTest#aFloodOfReadsDoesNotRotateAWayAWriteRecord` et `#theWriteRetentionRotatesWithinItsBound`, bornes réduites par `RemoteAccessJournal.open` — exigence : `remote-access-journal` › Un flot de lectures ne chasse pas l'enregistrement d'une écriture ; vérification : le premier est rouge avant 3.1

## 3. Correctif (option A)

- [ ] 3.1 Séparer la rétention des écritures de celle des lectures dans `RemoteAccessJournal` (deux `RotatingOperationalLogFile`, refus `ACCESS_JOURNAL_UNAVAILABLE` sur le fichier des écritures) — vérification : 2.1 vert, `RemoteAccessJournalHttpTest` et `RemoteAccessJournalArchitectureTest` verts

## 4. Documentation durable

- [ ] 4.1 Écrire les rétentions, ce qui les fait tourner et la collecte continue dans `SECURITY.md` et `docs/user/TEAM_REMOTE_SERVER.md` (et les notes de version si un fichier s'ajoute) — exigence : `remote-access-journal` › Le risque de rotation du journal par un principal authentifié est écrit ; vérification : `UserDocumentationSpeaksOfTheInstalledProductTest`, `RepositoryDocumentationCoherenceTest`
- [ ] 4.2 Mettre à jour `docs/developer/specifications/remote-access-journal.md` (exigences, diagramme de design.md, traçabilité) — vérification : `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate keep-the-access-journal-attribution-from-being-rotated-away --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-api`, `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- L'acceptation d'un ADR consolidé est une décision du mainteneur, séparée de l'archivage.
