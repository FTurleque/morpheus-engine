# Tâches : inscrire dans les specs ce que la frontière HTTP fait déjà

Aucune tâche n'écrit de code : chaque exigence ajoutée est déjà tenue par un test existant. Les tâches confirment ces
preuves par une exécution réelle avant l'archivage.

## 1. Lecture du chemin

- [ ] 1.1 Exécuter `./mvnw test -pl morpheus-api -am -Dtest=MorpheusHttpPathParserTest -Dsurefire.failIfNoSpecifiedTests=false` — exigences : `http-path-decoding` › Un seul séparateur final est ignoré ; › Le préfixe de l'API est reconnu segment par segment ; › Un segment qui n'est pas de l'UTF-8 valide est refusé ; vérification : les sept méthodes citées dans `design.md` passent
- [ ] 1.2 Exécuter `./mvnw test -pl morpheus-architecture-tests -Dtest=HttpPathReadingArchitectureTest` — exigence : `http-path-decoding` (tous les lecteurs passent par le parseur) ; vérification : verdict vert
- [ ] 1.3 Mettre à jour `docs/developer/specifications/http-path-decoding.md` : comportements passés de « Écarts » aux exigences, traçabilité complétée — vérification : page relue, liens contrôlés par `RepositoryDocumentationCoherenceTest`

## 2. Journal d'accès

- [ ] 2.1 Exécuter `./mvnw test -pl morpheus-api -am -Dtest='RemoteAccessJournal*Test' -Dsurefire.failIfNoSpecifiedTests=false` — exigences : `remote-access-journal` › Une écriture est consignée avant d'être servie ; › Une écriture dont l'acceptation ne peut être consignée est refusée ; › Le serveur distant ne démarre pas sans son journal ; vérification : `RemoteAccessJournalTest` et `RemoteAccessJournalHttpTest` passent
- [ ] 2.2 Décider s'il faut un test du refus au démarrage du serveur distant entier (la preuve actuelle est au niveau du journal) — exigence : `remote-access-journal` › Le serveur distant ne démarre pas sans son journal ; vérification : décision écrite dans `design.md`, et test à écrire le cas échéant
- [ ] 2.3 Mettre à jour `docs/developer/specifications/remote-access-journal.md` : exigences ajoutées, traçabilité — vérification : page relue, liens contrôlés

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate state-what-the-http-boundary-already-does --strict` — vérification : aucun avertissement
- [ ] 3.2 Relire la matrice de traçabilité des deux pages durables (exigence → scénario → diagramme → tâche → preuve) — vérification : chaque exigence ajoutée a une preuve existante ou est marquée « preuve manquante »
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=RepositoryDocumentationCoherenceTest` — vérification : aucun lien cassé

## Workflow follow-up

- Trancher les deux questions ouvertes de `design.md` avant l'archivage.
- Archiver le changement sur demande explicite du mainteneur, puis relire les deux specs principales consolidées.
