# Tâches : dessiner la vue en blocs depuis les dépendances de compilation des POM

## 1. Contrôle d'abord

- [ ] 1.1 Écrire le test `@CoherenceCheck` qui lit les POM et les diagrammes Mermaid de `05-vue-blocs.md` (D1, D2) —
  exigences : `reactor-module-documentation` › Une arête dessinée entre deux modules de la vue en blocs DOIT être une
  dépendance de compilation, › Un module de la vue en blocs DOIT atteindre par ses arêtes tout ce que ses POM lui font
  atteindre ; vérification : rouge sur la page actuelle, nomme les trois arêtes provider → SDK et les treize arêtes
  manquantes ; `CoherenceCheckMarkerTest` vert ; justification de PR (test d'architecture ajouté)
- [ ] 1.2 Couvrir les scénarios de bord (dépendance de test, page sans diagramme, libellé ambigu) par des pages plantées
  dans un répertoire temporaire — vérification : chaque cas échoue avec son message

## 2. Page

- [ ] 2.1 Redessiner §5.1 et §5.4 de `docs/architecture/arc42/05-vue-blocs.md` (D1, D3), en suivant l'état de
  `AUD-ARC-27` au moment de l'application — vérification : test de 1.1 vert ; diagrammes rendus (validation Mermaid)
- [ ] 2.2 Ajouter les deux exigences et leur matrice à `docs/developer/specifications/reactor-module-documentation.md`
  — vérification : `RepositoryDocumentationCoherenceTest` vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate draw-the-block-view-from-the-compile-dependencies-of-the-poms --strict` —
  vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice exigence → scénario → D1-D3 → tâche → test — vérification : chaque scénario a sa preuve
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dgroups=coherence` — vérification : vert ;
  `morpheus-architecture-tests/target/enforcement-verdict.txt` sans désynchronisation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
