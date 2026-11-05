# Conception : inscrire dans les specs ce que la frontière HTTP fait déjà

Conception allégée : le changement n'ajoute que des exigences qui décrivent un comportement existant et testé. Aucun
code, aucune donnée persistée, aucun contrat ne change.

## Contexte

Motivation : voir `proposal.md`, section Why. État vérifié le 2026-11-03 sur `836fffa5` :

- `[EXISTANT VÉRIFIÉ]` `MorpheusHttpPathParser.segments` (`morpheus-api/src/main/java/com/morpheus/api/transport/`)
  retire un seul `/` final, découpe avant de décoder, décode en UTF-8 strict (`CodingErrorAction.REPORT`), compare le
  préfixe aux segments décodés et lève `ApiFailure.notFound("unknown API route: " + rawPath)` ou
  `ApiFailure.notFound("invalid API path")`.
- `[EXISTANT VÉRIFIÉ]` `RemoteAccessJournal.accepted` n'écrit que pour une requête que
  `MorpheusRemoteHttpServer.usesPrivilegedConcurrency` classe comme écriture ou administration, et rend `false` quand
  l'écriture échoue ; `serve` répond alors 503 `ACCESS_JOURNAL_UNAVAILABLE`. `RemoteAccessJournal.beside` lève une
  `IllegalStateException` préfixée par `ACCESS_JOURNAL_UNAVAILABLE` quand le fichier ne peut pas être ouvert ; le
  constructeur du serveur distant l'appelle.
- `[DÉCISION]` SEC-0113 §5 et §7 fixent la double ligne d'une écriture, le refus 503 et le refus de démarrer.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques |
|---|---|---|
| `http-path-decoding` | Un seul séparateur final est ignoré | D1, vérification |
| `http-path-decoding` | Le préfixe de l'API est reconnu segment par segment | D1, D2, vérification |
| `http-path-decoding` | Un segment qui n'est pas de l'UTF-8 valide est refusé | D1, vérification |
| `remote-access-journal` | Une écriture est consignée avant d'être servie | D1, D3, vérification |
| `remote-access-journal` | Une écriture dont l'acceptation ne peut être consignée est refusée | D1, D3, vérification |
| `remote-access-journal` | Le serveur distant ne démarre pas sans son journal | D1, vérification |

Composants, modèle, états, contrats, persistance, transactions, observabilité, performance, migration : sans objet,
aucun code ni contrat ne change. Les diagrammes de séquence et d'états de la frontière sont maintenus dans la
documentation durable (voir plus bas) et ne sont pas dupliqués ici.

## Décisions

### D1 : ajouter des exigences plutôt que modifier les existantes
- **Nature** : [DÉCISION]
- **Exigences servies** : les six exigences ajoutées.
- **Choix** : sections `ADDED` en français ; les exigences existantes, en anglais, restent intactes.
- **Alternatives écartées** : `MODIFIED` des exigences existantes pour y insérer les nouveaux cas, écarté parce qu'il
  obligerait à réécrire un texte normatif existant (et à choisir sa langue) ; traduire les specs existantes, écarté
  parce que le titre d'une exigence est sa clé de synchronisation et que les deltas actifs l'apparient par ce titre.
- **Compromis** : deux langues coexistent dans une spec principale après archivage.
- **ADR** : non concerné.

### D2 : ne pas spécifier l'acceptation d'un préfixe encodé
- **Nature** : [PROPOSITION]
- **Exigences servies** : `http-path-decoding` › Le préfixe de l'API est reconnu segment par segment.
- **Choix** : l'exigence dit que la comparaison porte sur les segments décodés, ce qui est vrai ; aucun scénario
  n'affirme que `/%61pi/v1/health` doit être accepté, bien que le code l'accepte
  (`MorpheusHttpPathParserTest#thePrefixIsMatchedBySegmentNotByText`).
- **Alternatives écartées** : en faire un scénario, ce qui figerait une intention que personne n'a énoncée.
- **Compromis** : un correctif qui refuserait le préfixe encodé ne contredirait aucun scénario ; il contredirait le
  texte de l'exigence (comparaison sur les segments décodés) et devrait donc passer par un changement de spec.
- **ADR** : non concerné.

### D3 : reprendre la politique de SEC-0113 sans la redéfinir
- **Nature** : [DÉCISION]
- **Exigences servies** : les deux premières exigences ajoutées à `remote-access-journal`.
- **Choix** : les exigences énoncent la politique déjà décidée par SEC-0113 §5 et §7 ; l'exigence existante « A
  record that cannot be written SHALL have a decided outcome » reste en place.
- **Alternatives écartées** : modifier l'exigence existante pour y écrire la politique, écarté par D1.
- **Compromis** : deux exigences parlent du même sujet, l'une générale, l'autre précise.
- **ADR** : SEC-0113, déjà accepté ; aucun nouvel ADR.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| R5 de `remote-access-journal` renvoie à une politique sans la dire | spec principale | SEC-0113 §7 et code | exigence ajoutée (D3) |
| Comportements testés absents des specs | `MorpheusHttpPathParserTest`, `RemoteAccessJournalTest` | specs principales | exigences ajoutées |
| Préfixe encodé accepté sans intention écrite | `MorpheusHttpPathParserTest` | aucune spec | question ouverte (D2) |

## Stratégie de vérification

Niveau de preuve : les tests du lecteur de chemin exercent `MorpheusHttpPathParser` directement ; que le serveur local
et la politique distante passent tous par lui est tenu par `HttpPathReadingArchitectureTest` et
`RemoteAuthorizationLocalRoutingParityTest`.

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Un seul séparateur final est ignoré | Un séparateur final ne change pas la route | `MorpheusHttpPathParserTest#childPathsPreserveOrderAndIgnoreOneTrailingSlash` | existant |
| idem | Deux séparateurs finaux sont refusés | `MorpheusHttpPathParserTest#onlyOneTrailingSlashIsIgnored` | existant |
| Le préfixe de l'API est reconnu segment par segment | Un préfixe collé au segment suivant n'est pas le préfixe | `MorpheusHttpPathParserTest#thePrefixIsMatchedBySegmentNotByText` (`/api/v1health`) | existant |
| idem | Un séparateur encodé ne forme pas le préfixe | même test (`/api%2Fv1/health`) | existant |
| idem | Un préfixe étranger est nommé dans le refus | `MorpheusHttpPathParserTest#wrongPrefixIsRejectedWithExistingNotFoundContract` | existant |
| Un segment qui n'est pas de l'UTF-8 valide est refusé | Un octet isolé hors UTF-8 est refusé | `MorpheusHttpPathParserTest#aMalformedEscapeIsRefused` (`%ff`, `%C3`) | existant |
| idem | Un caractère encodé valide est lu quelle que soit la casse | `MorpheusHttpPathParserTest#anEscapeIsReadAsUtf8WhateverItsCase` | existant |
| Une écriture est consignée avant d'être servie | Une écriture laisse deux enregistrements | `RemoteAccessJournalTest#aReadLeavesOneRecordAndAWriteOrAnAdministrationTwo` ; `RemoteAccessJournalHttpTest#anAuthorizedRequestIsAttributedARefusedOneTooAndAnUnauthenticatedOneLeavesNothing` | existant |
| idem | Une lecture laisse un seul enregistrement | mêmes tests | existant |
| idem | La consultation d'une route d'administration est une lecture | `RemoteAccessJournalTest#aRecordThatCannotBeWrittenIsCountedRefusesAWriteAndLetsAReadThrough` (un `GET` `ADMIN` est accepté sans écriture) | existant, preuve indirecte |
| Une écriture dont l'acceptation ne peut être consignée est refusée | Une écriture n'est pas servie sans son enregistrement | `RemoteAccessJournalHttpTest#whenTheJournalCannotBeWrittenAWriteIsRefusedByNameAndAReadIsStillServed` | existant |
| idem | Une lecture reste servie | même test ; compteur : `RemoteAccessJournalTest#aRecordThatCannotBeWrittenIsCountedRefusesAWriteAndLetsAReadThrough` | existant |
| Le serveur distant ne démarre pas sans son journal | Un répertoire inutilisable empêche le démarrage | `RemoteAccessJournalTest#aJournalThatCannotBeOpenedIsARefusalThatNamesNoLocation` (au niveau du journal) | existant ; le refus au démarrage du serveur entier n'a pas de test propre |
| idem | Le câblage ouvre le journal à la construction du serveur | `RemoteAccessJournalArchitectureTest#theRemoteServerOpensItsJournalBesideTheIdentityFileWhenItIsConstructed` | nouveau (2026-11-04) |

## Décision de la tâche 2.2 (2026-11-04)

- `[DÉCISION]` Pas de test du refus au démarrage du serveur entier. Essayé : un répertoire à la place du fichier de
  journal n'empêche pas l'ouverture (`RotatingOperationalLogFile.openFile` ne durcit que le répertoire et crée le
  fichier s'il manque), donc `MorpheusRemoteHttpServer.start` aboutit ; l'autre échec possible exige un répertoire
  refusé au durcissement, non reproductible portablement alors que le fichier d'identités vient d'y être lu.
- `[DÉCISION]` Ajout à la place de `RemoteAccessJournalArchitectureTest#theRemoteServerOpensItsJournalBesideTheIdentityFileWhenItIsConstructed` :
  l'ouverture est unique et se fait dans le constructeur du serveur distant, à côté du fichier d'identités. Prouvé par
  casse : remplacer l'appel par un autre fait échouer le test.

## Documentation durable et diagrammes à mettre à jour

- `docs/developer/specifications/http-path-decoding.md` : déplacer les trois comportements de « Écarts » vers les
  exigences, compléter la traçabilité.
- `docs/developer/specifications/remote-access-journal.md` : idem pour les trois exigences ; la séquence de la façade
  et le diagramme d'états restent valides.

## Questions ouvertes

- Le préfixe encodé doit-il rester accepté (D2) ? Une réponse « non » ouvrirait un changement de code distinct.
- La non-consignation d'un 404 ou d'un 405 authentifié (SEC-0113 §4, testée par `RemoteAccessJournalHttpTest`)
  doit-elle devenir une exigence ? Elle peut s'ajouter plus tard sans changer ce changement.
