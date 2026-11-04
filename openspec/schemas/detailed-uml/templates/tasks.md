# Tâches : <!-- titre court du changement -->

<!-- Format suivi par apply : `## <n>. <groupe>` puis `- [ ] X.Y <tâche>`. Chaque tâche nomme
     l'exigence servie et sa vérification. Chaque groupe livre ses tests, ses diagrammes et
     sa documentation durable. Un test à écrire est annoncé « à écrire ». -->

## 1. <!-- groupe : première unité livrable -->

- [ ] 1.1 <!-- action --> — exigence : `<capability-path>` › <!-- titre exact --> ; vérification : <!-- test nommé, commande ou artefact -->
- [ ] 1.2 <!-- test de reproduction ou de contrat, écrit avant le correctif --> — vérification : <!-- le test échoue avant, passe après -->
- [ ] 1.3 <!-- mise à jour de la page durable et de ses diagrammes --> — vérification : <!-- page et lien relus, diagramme rendu -->

## 2. <!-- groupe suivant -->

- [ ] 2.1 <!-- action --> — exigence : `<capability-path>` › <!-- titre exact --> ; vérification : <!-- preuve -->

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate <change> --strict` — vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception ou diagramme → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [ ] 3.3 Lancer les gates du dépôt touchés par le changement — vérification : <!-- commande exacte et verdict -->

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
