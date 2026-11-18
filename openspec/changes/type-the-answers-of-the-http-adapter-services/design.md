# Conception : typer les réponses des services de l'adaptateur HTTP (AUD-QUA-36)

## Contexte

- [EXISTANT VÉRIFIÉ] 91 méthodes publiques des services de `com.morpheus.api` déclarent `Object` (mesure du 17/11/2026
  sur `1a539c22`, `grep -n "public Object "`) ; quatre services n'en ont aucune (`MorpheusCompositionApiService`,
  `MorpheusControlledLifecycleApiService`, `MorpheusOperabilityApiService`, `MorpheusProviderPluginApiService`).
- [EXISTANT VÉRIFIÉ] Les expressions rendues ont déjà un type statique précis : `PayloadMap.of` et les méthodes de
  `PublicPayloads` (`com.morpheus.application.payload`) rendent `Map<String, Object>` ; les vues publiques
  (`PolicyPublicViews`…), `PagedEnvelope` (`com.morpheus.api.transport`) et des records de résultat
  (`MorpheusApiService.RegistrationResult`) rendent leur propre type.
- [EXISTANT VÉRIFIÉ] Les routeurs passent la réponse à `ApiSuccess(String apiVersion, Object data)`, que
  `MorpheusHttpResponseWriter` sérialise par `CanonicalJsonSerializer` : la sérialisation dépend du type dynamique, pas
  du type déclaré.
- [EXISTANT VÉRIFIÉ] `SharedDomainProjectionArchitectureTest` refuse qu'une méthode d'adaptateur projette elle-même un
  type partagé ; `ApiEntryTypesArchitectureTest` (ARCH-0155) ne laisse aux autres modules que six types d'entrée,
  aucun service.
- [EXISTANT VÉRIFIÉ] `config/enforcement-ceiling-ratchets.properties` ne porte que les plafonds des interdits textuels,
  et `EnforcementMechanismAccountingTest` refuse une clé qu'aucun de ses plafonds ne lit.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `http-service-answer-types` | A public method of an HTTP adapter service SHALL declare the type of the answer it returns | D1, D3 |
| `http-service-answer-types` | Typing the answer of an HTTP adapter service SHALL change no HTTP response | D2 |

## Composants et frontières

Services `*ApiService` de `com.morpheus.api` ; routeurs de `com.morpheus.api.routes` et serveur de
`com.morpheus.api.local` (variables locales typées) ; une règle dans `morpheus-architecture-tests`. L'ordre des
paquets d'ARCH-0155 (`routes` > services > `transport`) est inchangé : un service peut rendre un type de `transport`
(`PagedEnvelope`) ou de l'application, jamais d'un routeur.

Sans objet : modèle de domaine, états, persistance, transactions, sécurité (aucune donnée nouvelle exposée),
performance (aucun coût d'exécution).

## Contrats d'interface

`docs/developer/reference/openapi/morpheus-v1*.yaml` et `contracts/public-surfaces.tsv` ne changent pas : le typage
est invisible au client.

## Compatibilité, migration et retour arrière

Aucun appelant hors de `morpheus-api` (aucun service n'est un type d'entrée). Retour arrière : rétablir `Object`, sans
effet sur une réponse.

## Décisions

### D1 : tout typer d'un coup, ou plafonner et typer progressivement
- **Nature** : [QUESTION]
- **Exigences servies** : `http-service-answer-types` › A public method of an HTTP adapter service SHALL declare the type of the answer it returns
- **Options** :
  - (A) typer les 91 méthodes dans ce changement, et une règle sans exception ;
  - (B) typer la façade `MorpheusApiService` d'abord, et une règle qui compte les méthodes restantes contre un plafond
    exact qui ne fait que baisser. Le plafond exige une clé de cliquet : un nouveau fichier
    `config/http-service-answer-ratchets.properties` (à créer), ou une extension du mécanisme de
    `config/enforcement-ceiling-ratchets.properties`, qui refuse aujourd'hui une clé qu'il ne connaît pas. En B,
    l'exigence est réécrite (« sauf les méthodes comptées par le plafond ») avant l'application ;
  - (C) typer sans règle.
- **[PROPOSITION]** : A. Le typage est mécanique (le type existe à chaque site), l'effort reste celui que l'audit
  estime (M), et une règle sans exception ne demande ni clé de cliquet, ni fichier de gouvernance de plus, ni suivi.
  B ne se justifie que si le diff doit être découpé ; C laisse revenir le défaut.
- **ADR** : non concerné (convention locale d'un adaptateur, tenue par un test).

### D2 : le type déclaré est le type statique de l'expression rendue
- **Nature** : [DÉCISION]
- **Exigences servies** : `http-service-answer-types` › Typing the answer of an HTTP adapter service SHALL change no HTTP response
- **Choix** : chaque méthode déclare le type statique de ce qu'elle rend déjà (`Map<String, Object>`, une vue
  publique, `PagedEnvelope`, un record existant) ; aucun objet de réponse nouveau n'est construit, aucune projection
  n'est réécrite.
- **Alternatives écartées** : des records de réponse nouveaux par route (changeraient l'ordre ou le nom des clés
  sérialisées, et une projection d'un type partagé dans l'adaptateur serait refusée par
  `SharedDomainProjectionArchitectureTest`).
- **Compromis** : `Map<String, Object>` reste peu typé ; il distingue néanmoins une carte d'une liste ou d'une page, et
  la façade ne peut plus déléguer à une méthode de forme différente sans erreur de compilation.
- **ADR** : non concerné.

### D3 : la règle sur le bytecode
- **Nature** : [DÉCISION]
- **Exigences servies** : `http-service-answer-types` › A public method of an HTTP adapter service SHALL declare the type of the answer it returns
- **Choix** : une règle ArchUnit `methods().that().arePublic().and().areDeclaredInClassesThat()` résident dans
  `com.morpheus.api` et ont un nom en `ApiService`, `should().notHaveRawReturnType(Object.class)`, avec un message qui
  nomme le remède ; garde-fou `failOnEmptyShould` actif. Trace de la violation plantée dans la Javadoc.
- **Alternatives écartées** : un interdit textuel de `public Object ` (ne voit pas un type générique `<T> T` effacé
  en `Object`, et lit les commentaires).
- **Compromis** : une méthode générique dont le type effacé est `Object` est refusée aussi, ce qui est voulu.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| « Les 19 services » | titre de `AUD-QUA-36` | 15 services sur 19 portent les 91 méthodes | sans objet : le chiffre de méthodes est exact |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| A public method … declare the type | Un service ajouté rend Object ; Une méthode non publique ; La règle a quelque chose à juger | `HttpServiceAnswerTypesArchitectureTest` (à écrire) et sa violation plantée ; `AnEmptyArchitectureRuleFailsTest` | à écrire / existant |
| Typing … change no HTTP response | Les réponses figées restent identiques | `ExtensionRoutesResponseWritingParityTest`, tests d'intégration `MorpheusApi*IntegrationTest` | existant |
| idem | Le contenu partagé reste convergent | `PublicPayloadConvergenceTest` | existant |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/http-service-answer-types.md` (à créer, gabarit de
`docs/developer/specifications/README.md`, ajouté à l'index, section « Frontière HTTP locale et distante ») ;
`docs/developer/API.md` si une section décrit les services.

## Questions ouvertes

D1 attend le mainteneur ; en option B, l'exigence et les tâches 2.x sont révisées avant l'application.
