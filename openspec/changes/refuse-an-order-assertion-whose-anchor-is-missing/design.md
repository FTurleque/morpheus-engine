# Conception : refuser une assertion d'ordre dont une ancre manque

## Contexte

- [EXISTANT VÉRIFIÉ] Sites qui passent à vide (revérifiés sur `1a539c22`) :
  `RemoteAccessJournalArchitectureTest.java:69`, `DependencyCheckWorkflowContractTest.java:811` et `:499`,
  `AuditHardeningWorkflowContractTest.java:127`.
- [EXISTANT VÉRIFIÉ] Sites déjà gardés, par `contains` ou par `>= 0` avant la comparaison :
  `DependencyCheckWorkflowContractTest.java:348-349`, `RemoteAccessJournalArchitectureTest.java:57` et `:66`,
  `WindowsReleaseSigningContractTest.java:251`, `AuditRemediationContractTest.java:48-50`. Ils sont corrects mais
  écrivent la garde à la main, chacun à sa façon.
- [EXISTANT VÉRIFIÉ] La capacité `architecture-rule-vacuity-guard` tient déjà l'intention voisine pour ArchUnit
  (`archRule.failOnEmptyShould`, `AnEmptyArchitectureRuleFailsTest`) : une règle qui ne voit rien ne passe pas.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `architecture-rule-vacuity-guard` | A textual order assertion of the suite SHALL fail when one of its anchors is absent | D1, D2, D3 |

## Composants et frontières

- `com.morpheus.architecture.TextOrder` (classe à créer dans le paquet existant `com.morpheus.architecture` des tests
  d'architecture) : `assertBefore(String text, String first, String second, String what)` qui échoue avec
  « <ancre> introuvable dans <what> » si `first` ou `second` manque, puis exige `indexOf(first) < indexOf(second)`.
- Les classes de test qui portent une comparaison d'ordre dans une assertion, migrées.
- Une méthode de règle dans `AnEmptyArchitectureRuleFailsTest`, qui porte déjà les gardes contre l'assertion vide.

Sans objet pour les autres rubriques de composants : aucun module de production.

## Modèle de domaine et invariants

Sans objet.

## Traitements et interactions

Sans objet : contrôles statiques.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet.

## Persistance et intégrité

Sans objet.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet : les contrats migrés gardent leur intention de sécurité (rôle avant journal, scan avant identification).

## Gestion des erreurs et reprise

Le message de l'aide nomme l'ancre absente ; le message d'ordre existant de chaque site est conservé en argument.

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Sans objet.

## Compatibilité, migration et retour arrière

Le nombre de méthodes `@Test` ne bouge pas, sauf la méthode de règle ajoutée. Retour arrière : réintroduire les
comparaisons nues.

## Décisions

### D1 : une aide unique qui refuse `-1`
- **Nature** : [DÉCISION]
- **Exigences servies** : A textual order assertion of the suite SHALL fail when one of its anchors is absent
- **Choix** : `TextOrder.assertBefore`, utilisée par les quatre sites et par les sites déjà gardés à la main.
- **Alternatives écartées** : ajouter `assertTrue(idx >= 0)` devant chaque site (action de l'audit) — corrige les
  quatre, laisse la prochaine comparaison nue possible ; laisser les sites gardés tels quels — la règle de D2 ne
  pourrait plus distinguer une comparaison gardée d'une nue.
- **Compromis** : une petite abstraction de test, justifiée par plus de trois sites et par la règle qui s'y adosse.
- **ADR** : non concerné.

### D2 : refuser la comparaison nue
- **Nature** : [DÉCISION]
- **Exigences servies** : idem, scénario « A new bare comparison is refused ».
- **Choix** : intention « aucune assertion de la suite ne compare la position d'un texte hors de l'aide » ; mécanisme
  textuel sur les sources de test du module : une instruction `assert…(` dont la condition contient `indexOf(` et un
  opérateur `<`, `>`, `<=` ou `>=` est refusée hors de `TextOrder`. Les `indexOf` de découpage (hors assertion) ne sont
  pas visés.
- **Alternatives écartées** : règle ArchUnit — l'opérateur de comparaison n'est pas visible dans les dépendances ; pas
  de règle — la dérive s'est déjà produite quatre fois.
- **Compromis** : une comparaison calculée dans une variable puis assertée échappe au motif ; la revue la lit.
- **ADR** : non concerné.

### D3 : casser chaque contrat une fois
- **Nature** : [DÉCISION]
- **Exigences servies** : idem, scénarios des quatre sites.
- **Choix** : pour chaque site migré, retirer l'ancre de gauche du fichier lu (copie temporaire ou édition locale),
  constater l'échec qui la nomme, revenir en arrière ; consigner la trace dans la PR (`.claude/rules/architecture.md`,
  « Casser la règle pour prouver qu'elle tient »).
- **Alternatives écartées** : tests fabriqués par site — les contrats lisent les vrais fichiers.
- **Compromis** : preuve manuelle, consignée.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Quatrième site vide | `DependencyCheckWorkflowContractTest.java:499` | audit (trois sites) | inclus |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| A textual order assertion … | The role check … renamed ; build step … Sonar ; step of the security workflow | contrats migrés, violation plantée par site (D3) | manuel |
| idem | Anchors present in the right order pass | `./mvnw test -pl morpheus-architecture-tests` | existant après migration |
| idem | A new bare comparison is refused | `AnEmptyArchitectureRuleFailsTest#noOrderAssertionComparesAPositionOutsideTheHelper` | à écrire |
| idem | (aide) | `TextOrderTest` : ancre de gauche absente, de droite absente, ordre inverse, ordre juste | à écrire |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/architecture-rule-vacuity-guard.md` (exigence, traçabilité). Aucun diagramme.

## Questions ouvertes

Aucune.
