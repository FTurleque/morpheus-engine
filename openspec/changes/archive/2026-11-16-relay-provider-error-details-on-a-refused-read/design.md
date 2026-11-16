# Conception : relayer les détails d'une erreur de provider sur une lecture refusée

<!-- Conception allégée : texte d'un diagnostic et relais sur le chemin d'échec, sans surface ni persistance. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `OpenSpecSpecificationProvider.unsupportedSchema` rend un diagnostic `UNSUPPORTED_PROVIDER_SCHEMA`
  de message fixe, dont les détails portent `provider`, `schema`, `supportedSchema`, `reason` et, pour le motif
  `unsupported-parent`, `declaredParent`.
- [EXISTANT VÉRIFIÉ] `ProjectContentIngestion.errorsOf` ne garde que le message de chaque diagnostic `ERROR` ;
  `IncompleteProjectReadException` en relaie cinq au plus dans son texte et ne porte rien d'autre du provider.
- [EXISTANT VÉRIFIÉ] `CliCommandRun` rend toute `IllegalStateException` par `CliErrorLine.write` : une ligne
  `MORPHEUS error [4]: <message>`, ou l'objet `{exitCode, code, message}` sous `--json`.
- [EXISTANT VÉRIFIÉ] `MorpheusProjectCli.diagnosticLines` écrit les lignes `diagnostic=` d'un `sync` publié à partir
  de `BoundedDiagnostics.local` ; rien ne l'appelle sur un refus.
- [EXISTANT VÉRIFIÉ] Côté HTTP, le texte d'un refus passe par `BoundaryFailureMessage.safe` : un texte de plus de
  `ServerLocationDisclosure.MAX_RELAYED_LENGTH` caractères, ou qui nomme un emplacement, est remplacé **en entier**
  par le nom du type. Les détails d'un diagnostic n'atteignent un appelant distant que par la liste
  `BoundedDiagnostics.REMOTE_DETAIL_KEYS`, qui ne contient ni `reason` ni `declaredParent`.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `openspec-provider-reading` | The refusal of a project schema says what would make it readable | D1 |
| `project-ingestion-port` | A refused read SHALL carry the error diagnostics of the provider | D2 |
| `cli-argument-base` | The error line of a refused project read SHALL relay the provider's error diagnostics | D3 |

## Décisions

### D1 : le message du refus est composé avec les faits du refus
- **Nature** : [DÉCISION]
- **Exigences servies** : `openspec-provider-reading` › The refusal of a project schema says what would make it readable
- **Choix** : une phrase par motif, sujet `OpenSpec schema '<nom>'` et fichier `openspec/schemas/<nom>/schema.yaml` :
  `must declare 'derives: spec-driven'` (`no-declared-parent`), `must exist and declare …`
  (`schema-definition-missing`), `declares another parent; only … is read` (`unsupported-parent`). Le nom n'est écrit
  que s'il est un nom de schéma valide d'au plus 32 caractères ; sinon le sujet est `The declared OpenSpec schema` et
  le fichier `openspec/schemas/<schema>/schema.yaml`. Le parent déclaré, texte libre lu dans un fichier, n'entre
  jamais dans le message : il reste dans `declaredParent`.
- **Alternatives écartées** : garder le message fixe et ne compter que sur D2/D3 (l'HTTP et tout lecteur du seul
  message restent muets) ; recopier le nom et le parent sans borne (un nom long ferait dépasser 512 caractères au texte
  du refus, que la frontière HTTP remplacerait alors en entier par le nom du type : le remède rendrait le refus plus
  muet qu'avant).
- **Compromis** : la borne de 32 caractères est dimensionnée sur le texte complet d'un refus dont les huit catégories
  ont échoué ; un test la mesure sur le texte réel au lieu de la calculer.
- **ADR** : non concerné (les motifs et les clés de détail d'ADR-0169 ne changent pas).

### D2 : le refus porte les diagnostics, son texte ne change pas
- **Nature** : [DÉCISION]
- **Exigences servies** : `project-ingestion-port` › A refused read SHALL carry the error diagnostics of the provider
- **Choix** : `IncompleteProjectReadException.providerErrors()` rend les diagnostics `ERROR` distincts, dans l'ordre du
  provider. Le texte reste formé des messages distincts, cinq au plus.
- **Alternatives écartées** : écrire les détails dans le texte du refus (ce texte est relayé par HTTP : toute clé de
  détail y passerait hors de `REMOTE_DETAIL_KEYS`, et une valeur à forme de chemin ferait remplacer le texte entier).
- **Compromis** : aucun.
- **ADR** : non concerné.

### D3 : la ligne d'erreur de la CLI relaie ces diagnostics, dans le format d'un sync publié
- **Nature** : [DÉCISION]
- **Exigences servies** : `cli-argument-base` › The error line of a refused project read SHALL relay the provider's
  error diagnostics
- **Choix** : `CliCommandRun` rend `IncompleteProjectReadException` par une écriture de `CliErrorLine` qui reçoit
  `BoundedDiagnostics.local(providerErrors())` : en texte, la ligne d'erreur puis les lignes `diagnostic=` ; sous
  `--json`, un seul objet avec le membre `diagnostics`. Les lignes `diagnostic=` sont écrites par une seule classe,
  `CliDiagnosticLines`, que le `sync` publié et le refus appellent. `sync` et `analyze change` sont servis sans code
  propre, puisqu'ils passent par `CliCommandRun`.
- **Alternatives écartées** : imprimer les lignes depuis `MorpheusProjectCli` (laisse `analyze change` muet, et écrit
  une deuxième fois le cadre des erreurs) ; deux objets JSON sur le flux d'erreur (un script n'en lit qu'un).
- **Compromis** : sous `--json`, l'objet d'erreur d'une lecture refusée a un membre de plus ; les autres erreurs
  gardent `exitCode`, `code`, `message`.
- **ADR** : non concerné.

### D4 : l'enveloppe d'erreur HTTP ne change pas
- **Nature** : [DÉCISION]
- **Exigences servies** : `openspec-provider-reading` › The refusal of a project schema says what would make it readable
  (scénario « The refused read stays relayable »)
- **Choix** : l'HTTP reçoit la phrase de D1 dans le message du `409`, par le filtre existant. Aucun diagnostic
  structuré n'est ajouté à `ApiErrorEnvelope`.
- **Alternatives écartées** : ajouter `diagnostics` à l'enveloppe (change l'OpenAPI et le contrat de toutes les
  erreurs pour un refus que son message explique déjà).
- **Compromis** : sur HTTP, un refus d'un autre provider n'est expliqué que par ses messages, comme aujourd'hui.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| « one line » pour toute erreur de la CLI | `cli-argument-base` › Every CLI failure SHALL be reported through one error line | D3 : des lignes `diagnostic=` suivent la ligne d'erreur d'une lecture refusée | l'exigence ajoutée le dit ; la ligne d'erreur reste unique et inchangée |
| `reason` et `declaredParent` absents de la liste distante | `BoundedDiagnostics.REMOTE_DETAIL_KEYS` | détails du refus | sans objet : ces diagnostics n'atteignent aucune réponse HTTP (D4) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| The refusal of a project schema says what would make it readable | les quatre motifs | `OpenSpecSpecificationProviderTest` (message relu dans `assertRefused` et par motif) | écrit |
| idem | The refused read stays relayable | `OpenSpecSchemaRefusalStaysRelayableTest` | écrit |
| idem | (HTTP) | `MorpheusApiProjectSyncIntegrationTest#aSyncRefusedForAnUnsupportedSchemaSaysWhatWouldMakeItReadable` | écrit |
| A refused read SHALL carry the error diagnostics of the provider | les trois scénarios | `ProjectContentIngestionTest` | écrit |
| The error line of a refused project read SHALL relay the provider's error diagnostics | texte, JSON | `MorpheusCliTest#aSyncRefusedForAnUnsupportedSchemaRelaysTheDetailsOfItsDiagnostic` | écrit |
| idem | sans erreur, autre échec | `CliErrorLineTest` | écrit |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/openspec-provider-reading.md`, `project-ingestion-port.md` et `cli-argument-base.md` ;
`docs/user/QUICKSTART.md` (ce que dit le refus d'un schéma).

## Questions ouvertes

Aucune.
