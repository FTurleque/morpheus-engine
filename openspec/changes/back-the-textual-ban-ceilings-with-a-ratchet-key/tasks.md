# Tâches : adosser les deux plafonds d'interdits textuels à une clé de cliquet

## 1. Fichier de cliquets et comparaison des plafonds

- [ ] 1.1 Écrire les tests de reproduction (à écrire) dans `EnforcementMechanismAccountingTest` : sur un fichier
  temporaire, une constante différente de sa clé, une clé absente et une clé orpheline sont refusées en nommant la
  constante, la clé et les valeurs — exigence : `enforcement-mechanism-accounting` › Chaque plafond d'une population
  d'interdits textuels égale une clé d'un fichier de cliquets ; vérification : les tests échouent avant 1.2
- [ ] 1.2 Créer `config/enforcement-ceiling-ratchets.properties` (en-tête : clés qui ne font que baisser, relèvement
  justifié en PR) avec les deux clés à la valeur lue dans les constantes au moment d'appliquer — même exigence ;
  vérification : les tests de 1.1 passent
- [ ] 1.3 Faire comparer sa constante à sa clé par `TypeBansAwaitingABytecodeRuleTest` et par
  `EnforcementMechanismAccountingTest`, et faire refuser les clés orphelines par ce dernier — même exigence ;
  vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=TypeBansAwaitingABytecodeRuleTest+EnforcementMechanismAccountingTest`
- [ ] 1.4 Planter la violation : porter `AWAITING_A_RULE_CEILING` à 1 sans toucher au fichier, constater l'échec qui
  nomme la constante et la clé, revenir en arrière ; consigner les trois étapes ici et dans la PR — vérification :
  trace consignée
- [ ] 1.5 Vérifier que `CoherenceCheckMarkerTest` passe sans changer de marqueur — exigence :
  `enforcement-mechanism-accounting` › A test class that reads only non-compiled artefacts SHALL be marked as a
  coherence check and SHALL stay in the gate module ; vérification : test vert
- [ ] 1.6 Mettre à jour `.claude/rules/architecture.md` (section « Un interdit textuel de type écrit son intention ») et
  `docs/developer/specifications/enforcement-mechanism-accounting.md` (exigence, matrice) — vérification : pages
  relues, `GovernanceSurfaceCoherenceTest` et `RepositoryDocumentationCoherenceTest` verts

## 2. Vérification d'ensemble

- [ ] 2.1 Lancer `openspec validate back-the-textual-ban-ceilings-with-a-ratchet-key --strict` — vérification : aucun
  avertissement
- [ ] 2.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → décision → tâche → preuve) —
  vérification : chaque scénario a une preuve existante ou est marqué « revue »
- [ ] 2.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et lire `target/enforcement-verdict.txt` — vérification :
  verdicts vides, `architectureTestsMinimum` tenu
- [ ] 2.4 Remplir dans la PR la section « Justification d'une modification de gouvernance » pour le nouveau fichier de
  `config/` — vérification : section remplie

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable ; noter `AUD-TST-25` comme corrigé à la
  revérification suivante.
