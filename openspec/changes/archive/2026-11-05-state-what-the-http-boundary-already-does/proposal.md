# Proposition : inscrire dans les specs ce que la frontière HTTP fait déjà

## Why

La relecture du 2026-11-03 de quatre specs de la frontière HTTP
(`docs/developer/specifications/`, lot « Frontière HTTP locale et distante ») a trouvé des comportements que le code
implémente et que des tests fixent, mais qu'aucune exigence n'énonce. Une exigence de `remote-access-journal` renvoie
même à « la politique de l'ADR » sans la dire. Tant qu'un comportement n'est que testé, rien ne dit s'il est voulu :
un correctif peut le changer sans qu'aucune spec ne bouge.

## What Changes

- `http-path-decoding` : trois exigences ajoutées, qui énoncent le `/` final unique ignoré, la reconnaissance du
  préfixe de l'API par segments avec son refus nommé, et le refus d'un segment qui n'est pas de l'UTF-8 valide.
- `remote-access-journal` : trois exigences ajoutées, qui énoncent l'enregistrement `accepted` écrit avant de servir
  une écriture, la politique quand il ne peut pas l'être (celle de SEC-0113 §7), et le refus de démarrer sans journal.
- Aucune exigence existante n'est modifiée, retirée ni traduite. Aucun code, aucune surface publique, aucun contrat
  ne change.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `http-path-decoding` : ajout d'exigences qui décrivent un comportement existant et testé.
- `remote-access-journal` : ajout d'exigences qui décrivent un comportement existant, testé et décidé par SEC-0113.

## Périmètre et exclusions

**Inclus :** les six comportements listés, chacun déjà fixé par un test nommé dans `design.md`.

**Exclus :**
- l'acceptation d'un préfixe encodé (`/%61pi/v1/health`), testée mais dont l'intention n'est pas établie (question
  ouverte) ;
- la non-consignation d'une requête authentifiée vers une route inconnue ou une méthode refusée (SEC-0113 §4),
  laissée à la décision du mainteneur ;
- le découpage des exigences existantes trop longues et la traduction des specs existantes.

## Acteurs et surfaces concernés

Client du serveur local (loopback) et client du serveur distant, sur toutes les routes de `/api/v1` ; opérateur du
serveur distant, qui lit le journal et le compteur `remote.access.journal.lost`.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le `/` final unique est ignoré, deux `/` finaux donnent 404 | existant vérifié | `MorpheusHttpPathParserTest#onlyOneTrailingSlashIsIgnored`, `#childPathsPreserveOrderAndIgnoreOneTrailingSlash` |
| Le préfixe est comparé par segments ; refus `unknown API route: <chemin brut>` | existant vérifié | `MorpheusHttpPathParserTest#thePrefixIsMatchedBySegmentNotByText`, `#wrongPrefixIsRejectedWithExistingNotFoundContract` |
| Un segment non UTF-8 est refusé | existant vérifié | `MorpheusHttpPathParserTest#aMalformedEscapeIsRefused` (`%ff`, `%C3`) |
| `accepted` avant de servir une écriture ; 503 si impossible ; lecture servie | existant vérifié et décision | `RemoteAccessJournalTest`, `RemoteAccessJournalHttpTest`, SEC-0113 §5 et §7 |
| Le serveur distant ne démarre pas sans journal | existant vérifié et décision | `RemoteAccessJournalTest#aJournalThatCannotBeOpenedIsARefusalThatNamesNoLocation`, SEC-0113 §7 |
| Ces comportements sont voulus | proposition à valider | ce changement |

## Impact

- Specs : `openspec/specs/http-path-decoding/spec.md`, `openspec/specs/remote-access-journal/spec.md` (à
  l'archivage seulement).
- Documentation durable : `docs/developer/specifications/http-path-decoding.md` et `remote-access-journal.md`,
  sections « Écarts » et « Traçabilité ».
- ADR : SEC-0113 couvre déjà la politique du journal ; aucun nouvel ADR.
- Code et tests : aucun changement.

## Questions ouvertes

- Le préfixe encodé (`/%61pi/v1/...`) doit-il rester accepté ? À trancher par le mainteneur avant toute exigence.
- La non-consignation d'un 404 ou d'un 405 authentifié doit-elle devenir une exigence ?
