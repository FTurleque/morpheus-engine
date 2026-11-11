# Conception : adosser les deux plafonds d'interdits textuels à une clé de cliquet

Conception allégée : changement d'outillage de la suite d'architecture, sans code de production.

## Contexte

- [EXISTANT VÉRIFIÉ] `TypeBansAwaitingABytecodeRuleTest` déclare `static final int AWAITING_A_RULE_CEILING` (ligne 48,
  Javadoc « never raised ») et l'asserte par égalité dans
  `theTextualBansOfATypeAwaitingARuleOnTheBytecodeDoNotGrow`. La classe lit des sources principales : elle ne porte pas
  `@CoherenceCheck`.
- [EXISTANT VÉRIFIÉ] `EnforcementMechanismAccountingTest` déclare `static final int UNEXPLAINED_TYPE_BAN_CEILING`
  (ligne 51) et l'asserte par égalité dans `theTextualBansOfATypeThatStateNoIntentDoNotGrow`. La classe porte
  `@CoherenceCheck` : elle ne lit que des sources de test.
- [EXISTANT VÉRIFIÉ] Patron à copier : `DependencyCheckSuppressionContractTest` (paquet `d2`) lit
  `config/dependency-check-suppression-ratchets.properties` (constantes `SUPPRESSION_RATCHETS`,
  `SUPPRESSED_DETECTIONS_KEY`) et compare la taille de sa table à la clé.
- [EXISTANT VÉRIFIÉ] `.github/pull_request_template.md:28` cite `config/*ratchets*.properties` parmi les fichiers de
  gouvernance à justifier : le nom `enforcement-ceiling-ratchets.properties` y entre sans modifier le gabarit.
- [EXISTANT VÉRIFIÉ] `config/debt-counter-ratchets.properties` ne déclare, par son en-tête, que les listes que
  `ProductionClassListsArchitectureTest.KINDS` déclare compteurs de dette.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `enforcement-mechanism-accounting` | Chaque plafond d'une population d'interdits textuels égale une clé d'un fichier de cliquets | D1, D2, D3 |

## Composants et frontières

Sans objet au-delà des deux classes de test et du fichier de cliquets : aucune frontière de module ne bouge.

## Décisions

### D1 : un fichier de cliquets propre
- **Nature** : [DÉCISION]
- **Exigences servies** : `enforcement-mechanism-accounting` › Chaque plafond d'une population d'interdits textuels égale une clé d'un fichier de cliquets
- **Choix** : `config/enforcement-ceiling-ratchets.properties`, deux clés `TypeBansAwaitingABytecodeRuleTest.AWAITING_A_RULE_CEILING`
  et `EnforcementMechanismAccountingTest.UNEXPLAINED_TYPE_BAN_CEILING`, avec un en-tête qui dit que ces clés ne font
  que baisser et qu'un relèvement se justifie en pull request.
- **Alternatives écartées** : (a) ajouter les clés à `config/debt-counter-ratchets.properties` — ce fichier est la
  table des compteurs de listes de `KINDS`, et son contrôle refuserait ou devrait tolérer deux clés qui ne sont pas des
  listes ; (b) remplacer les plafonds par un invariant « zéro » codé dans le test — le relever reste une édition du seul
  test, et cela réécrit une exigence vivante de la spec, ce que la règle `specs` du schéma interdit.
- **Compromis** : un fichier de plus sous `config/`.
- **ADR** : non concerné.

### D2 : lecture du fichier par chaque test, et contrôle des clés orphelines
- **Nature** : [DÉCISION]
- **Exigences servies** : même exigence, scénarios « Une clé manquante ou orpheline »
- **Choix** : chaque test lit sa clé et compare sa constante ; `EnforcementMechanismAccountingTest` (qui reste une
  vérification de cohérence : il lit un fichier de `config/`, pas une source principale) porte en plus le test qui
  exige que le fichier ne déclare que les deux clés connues. Une petite classe partagée n'est pas créée pour deux
  lectures de trois lignes (`.claude/rules/code-style.md`, abstraction prématurée).
- **Alternatives écartées** : comparer la clé au fichier de la branche de base — le build n'y a pas accès.
- **Compromis** : la règle « ne fait que baisser » reste tenue par la revue, mais un relèvement devient visible dans un
  fichier de gouvernance au lieu d'une constante Java.
- **ADR** : non concerné.

### D3 : la classification `@CoherenceCheck` ne change pas
- **Nature** : [DÉCISION]
- **Exigences servies** : `enforcement-mechanism-accounting` › A test class that reads only non-compiled artefacts SHALL be marked as a coherence check and SHALL stay in the gate module
- **Choix** : lire un `.properties` n'est ni évaluer une règle sur le bytecode, ni lire une source principale, ni charger
  une classe de production : `EnforcementMechanismAccountingTest` garde son marqueur, `TypeBansAwaitingABytecodeRuleTest`
  reste sans marqueur. `CoherenceCheckMarkerTest` le confirme.
- **Alternatives écartées** : aucune.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| La spec dit « The ceiling MUST NOT be raised » ; le build ne tient que l'égalité | `openspec/specs/enforcement-mechanism-accounting/spec.md` | `EnforcementMechanismAccountingTest:96` | ce changement adosse le plafond à un fichier de gouvernance ; la non-hausse reste à la revue |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Chaque plafond … égale une clé … | Un plafond relevé dans le seul test | violation plantée : constante portée à 1, échec constaté qui nomme constante et clé, retour arrière (consignés dans les tâches) | à écrire |
| idem | Un membre retiré, la clé laissée telle quelle | test sur exemple : constante 0, clé 1 → message qui demande d'abaisser la clé | à écrire |
| idem | Une clé manquante ou orpheline | test sur fichier temporaire (`@TempDir`) : clé absente, clé en trop | à écrire |
| idem | Un relèvement qui touche le fichier de gouvernance | revue de la PR (gabarit, section gouvernance) | manuel |

## Documentation durable et diagrammes à mettre à jour

- `docs/developer/specifications/enforcement-mechanism-accounting.md` : exigence ajoutée, matrice de traçabilité.
- `.claude/rules/architecture.md`, section « Un interdit textuel de type écrit son intention » : la phrase sur
  `UNEXPLAINED_TYPE_BAN_CEILING` et `AWAITING_A_RULE_CEILING` nomme le fichier de cliquets, sans recopier de valeur.
- Aucun diagramme : le changement n'a ni flux ni état nouveau.

## Questions ouvertes

Aucune.
