# Tâches : déclarer l'ADR de chaque table de faits qu'un ADR énonce

## 1. Table de faits sous un ADR par référence

- [ ] 1.1 Écrire le test sur registre temporaire (à écrire) : une table de faits qui déclare un ADR absent, non accepté
  ou qui ne la nomme pas est refusée en nommant la table et l'ADR — exigence : `adapter-internal-boundaries` › Une
  table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR ; vérification : échoue avant 1.2
- [ ] 1.2 Ajouter `Kind.tableOfFactsUnder(String)` et remplacer `everyDebtCounterIsNamedByTheAcceptedDecisionRecordItDeclares`
  par `everyListIsNamedByTheAcceptedDecisionRecordItDeclares`, qui parcourt toute liste portant une décision sans
  énumération — même exigence ; vérification : test de 1.1 vert, compte de méthodes `@Test` de la classe non diminué
- [ ] 1.3 Déclarer les deux `CONFINED_FAMILIES` sous `ARCH-0165`, et écrire leurs références `TestClass.CHAMP` dans la
  décision 3 et la section Compliance d'`ARCH-0165` — même exigence ; vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=ProductionClassListsArchitectureTest`, `archgate check` sans
  `briefingWarnings` nouveau

## 2. Contrôle inverse

- [ ] 2.1 Écrire le test sur registre temporaire (à écrire) : un ADR accepté qui nomme une table de faits non déclarée,
  deux ADR qui nomment la même table, une mention de la seule classe de test, un ADR archivé — exigence :
  `adapter-internal-boundaries` › Un ADR accepté qui nomme une table de faits par sa référence DOIT être déclaré par
  elle ; vérification : échoue avant 2.2 sur les deux premiers cas, passe sur les deux autres
- [ ] 2.2 Ajouter la méthode du contrôle inverse sur le dépôt réel — même exigence ; vérification : elle échoue sur
  `ARCH-0162` / `ONE_STORE_PER_OPERATION` et `ARCH-0115` / `MAIN_USERS` tant que 2.3 n'est pas fait
- [ ] 2.3 Déclarer `ONE_STORE_PER_OPERATION` sous `ARCH-0162` et `MAIN_USERS` sous `ARCH-0115`, après avoir relu que
  chaque ADR énonce bien la table (et pas seulement la cite) — même exigence ; vérification : méthode de 2.2 verte
- [ ] 2.4 Planter la violation : retirer la déclaration de `MAIN_USERS`, constater l'échec qui nomme `ARCH-0115` et la
  table, revenir en arrière ; consigner ici et dans la PR — vérification : trace consignée

## 3. Documentation

- [ ] 3.1 Mettre à jour `docs/developer/specifications/adapter-internal-boundaries.md` (exigences, diagramme de
  `design.md`, matrice) et le paragraphe de `.claude/rules/architecture.md` sur les tables de faits — vérification :
  pages relues, `RepositoryDocumentationCoherenceTest` et `GovernanceSurfaceCoherenceTest` verts

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate declare-the-decision-record-of-every-stated-table-of-facts --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité (exigence → scénario → décision → tâche → preuve) — vérification : chaque
  scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verdicts vides dans
  `target/enforcement-verdict.txt`, aucune violation Archgate

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée ; noter `AUD-ARC-21` comme corrigé à la revérification
  suivante.
