# Proposition : rendre chaque échec HTTP par une seule table de correspondance (AUD-QUA-30)

## Why

La décision « quel échec devient quel statut et quel code » est recopiée dans cinq gestionnaires de `morpheus-api` :
`MorpheusHttpServer.handle` (`com.morpheus.api.local`) et les quatre routeurs à contexte propre
(`MorpheusPolicyHttpRoutes`, `MorpheusPolicyManagementHttpRoutes`, `MorpheusQueryHttpRoutes`,
`MorpheusReasoningHttpRoutes`, paquet `com.morpheus.api.routes.owncontext`). Chacun réécrit la même échelle
`ApiFailure` (405 avec `Allow`) / `IllegalArgumentException` → 400 `BAD_REQUEST` / `KnowledgeStoreException` |
`IllegalStateException` → 409 `STATE_CONFLICT` / `RuntimeException` → trace puis 500 `INTERNAL_ERROR`.

Les copies divergent déjà, revérifié le 17/11/2026 sur `1a539c22` :

- `PublishedHistoryException` n'est rendue en 409 que par le serveur local ; levée sous un routeur, elle tomberait en
  500 ;
- le routeur de raisonnement n'a aucune branche 409 et nomme son 500 `internal MORPHEUS reasoning error` ;
- la Javadoc d'`ExtensionRoutesResponseWritingParityTest` consigne qu'aucune des quatre branches 500 et aucune branche
  `STATE_CONFLICT` de policy et de policy management n'est exercée.

Le writer a été mutualisé (DT-17), pas la décision qu'il écrit : toute exception métier nouvelle s'ajoute à la main à
chaque copie, et un oubli la fait tomber en 500 sur un routeur et en 409 sur un autre.

Preuve : constat `AUD-QUA-30` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, confirmé à l'identique sur `1a539c22`.

## What Changes

- Une classe unique de `com.morpheus.api.transport` décide, pour un échec, du statut, du code, du texte et de
  l'en-tête `Allow` d'un 405 ; elle porte la famille commune et reçoit, en tête, les correspondances propres à un
  routeur (`REVISION_CONFLICT`, `QUERY_VALIDATION`, `QUERY_BUDGET_EXCEEDED`, `REASONING_VALIDATION`).
- Le serveur local et les quatre routeurs à contexte propre rendent tout échec par elle ; aucun n'écrit plus sa propre
  échelle de `catch`.
- `PublishedHistoryException` est rendue en 409 `STATE_CONFLICT` sur tous les gestionnaires, et le routeur de
  raisonnement reçoit la famille commune entière (décision D2, question du texte de son 500 en D3).
- Un test unitaire de la table exerce chaque branche, 409 et 500 compris, une fois pour toutes.
- Une règle du module d'architecture refuse un gestionnaire de `morpheus-api` qui rendrait un échec hors de la table.
- Aucun statut ni code existant ne change pour une requête déjà servie ; aucune route, aucun outil, aucune ligne de
  `contracts/public-surfaces.tsv` ni de l'OpenAPI ne bouge, sauf si le mainteneur retient l'option B de D3.

## Capabilities

### New Capabilities
- `http-failure-status-mapping` : la correspondance entre un échec et la réponse d'erreur HTTP (statut, code, texte),
  la même pour chaque gestionnaire de l'adaptateur HTTP local.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `MorpheusHttpServer.handle`, les méthodes `handle` des quatre routeurs de
`com.morpheus.api.routes.owncontext`, la nouvelle classe de `com.morpheus.api.transport`, leurs tests, une règle
d'architecture, la page durable de la capacité.

**Exclus :** le serveur distant (`MorpheusRemoteHttpServer`), dont le 500 est écrit par
`MorpheusRemoteResponseWriter` pour la façade et relève d'une autre frontière ; le texte d'un échec, déjà tenu par
`http-failure-disclosure` (`BoundaryFailureMessage.safe`) ; les routeurs des groupes `bodiless` et `shareddecoder`,
portés par le serveur local et donc servis par sa table.

## Acteurs et surfaces concernés

Le client HTTP local (enveloppe d'erreur `ApiErrorEnvelope`, statut et code) ; le contributeur qui ajoute une
exception métier ou un routeur.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Cinq gestionnaires recopient la même échelle | existant vérifié | lignes `catch` des cinq fichiers cités, `1a539c22` |
| `PublishedHistoryException` → 409 seulement en local | existant vérifié | `MorpheusHttpServer.java`, branche `STATE_CONFLICT` |
| Le routeur de raisonnement n'a pas de 409 et nomme son 500 autrement | existant vérifié | `MorpheusReasoningHttpRoutes.handle` |
| Les branches 500 et `STATE_CONFLICT` des routeurs ne sont pas exercées | existant vérifié | Javadoc d'`ExtensionRoutesResponseWritingParityTest` |
| Le writer est exigé dans le groupe `owncontext` et interdit ailleurs | existant vérifié | `HttpRoutesTransportBoundaryArchitectureTest` |
| Le texte du 500 du routeur de raisonnement est unifié | question ouverte | design.md, D3 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : mécanisme interne d'un adaptateur, retour arrière sans coût |

## Impact

`morpheus-api` (paquets `transport`, `local`, `routes.owncontext`) et ses tests ; `morpheus-architecture-tests` (une
règle ajoutée : fichier de gouvernance, justification dans la description de la PR). Aucun contrat ni schéma
persisté. ADR existants respectés : ARCH-0155 (un rôle est un paquet, ordre `routes` > services > `transport`).

Ordre et voisinage : le sprint 9 suit le sprint 3, dont le constat `AUD-SEC-22` (authentification de l'API locale)
touche aussi `com.morpheus.api.local` et peut ajouter un refus à la table ; appliquer ce changement après lui, ou
rebaser l'un sur l'autre en gardant un seul point de décision. Aucune dépendance `depends_on` déclarée par l'audit.

## Questions ouvertes

- Le 500 du routeur de raisonnement garde-t-il le texte `internal MORPHEUS reasoning error` ou prend-il celui des
  autres gestionnaires (design.md, D3) ? À trancher par le mainteneur avant la tâche 2.3.
