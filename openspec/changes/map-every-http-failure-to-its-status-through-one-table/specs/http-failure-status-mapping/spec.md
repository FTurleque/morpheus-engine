## Purpose

Fixer la réponse d'erreur (statut, code, texte, en-tête `Allow`) que l'adaptateur HTTP local rend pour chaque échec,
identique sur chaque gestionnaire, et la tenir à un seul endroit. Page durable :
`docs/developer/specifications/http-failure-status-mapping.md`.

## ADDED Requirements

### Requirement: Every handler of the local HTTP adapter SHALL answer a failure of the common family with the same status and code

Le serveur local et chaque routeur qui enregistre son propre contexte DOIVENT (MUST) rendre : un refus nommé par
l'adaptateur avec son propre statut et son code ; une entrée invalide en 400 `BAD_REQUEST` ; un conflit d'état, de
stockage ou d'historique publié en 409 `STATE_CONFLICT` ; tout autre échec en 500 `INTERNAL_ERROR`, sans texte de
l'échec. Le corps est l'enveloppe d'erreur `v1`.

#### Scenario: Un conflit de stockage sous une route policy
- **GIVEN** un serveur local dont le store lève un conflit de stockage pendant une lecture de policy pack
- **WHEN** un client appelle `GET /api/v1/policy-packs`
- **THEN** la réponse est un 409 dont l'enveloppe porte le code `STATE_CONFLICT` et le texte filtré de l'échec
- **AND** rien n'est écrit dans la base par cette requête

#### Scenario: Un conflit d'historique publié sous un routeur
- **GIVEN** un routeur à contexte propre dont le service lève un conflit d'historique publié
- **WHEN** la requête échoue
- **THEN** la réponse est un 409 `STATE_CONFLICT`, comme sous une route du serveur local, et non un 500

#### Scenario: Un échec imprévu
- **GIVEN** un gestionnaire dont le service lève une exception d'exécution qu'aucune branche ne nomme
- **WHEN** la requête échoue
- **THEN** la réponse est un 500 `INTERNAL_ERROR` dont le texte ne contient ni le message ni le type de l'échec
- **AND** l'échec est compté une fois dans la famille de trace des erreurs internes HTTP

#### Scenario: Une méthode refusée
- **WHEN** un client appelle une route avec une méthode qu'elle ne sert pas
- **THEN** la réponse est un 405 `METHOD_NOT_ALLOWED` dont l'en-tête `Allow` égale les méthodes de la route

### Requirement: A router SHALL name its own failures before the common family, and only there

Un routeur DOIT (MUST) déclarer ses échecs propres (code et statut) comme correspondances examinées avant la famille
commune, et un échec qu'il ne nomme pas DOIT recevoir la réponse de la famille commune. Une correspondance propre NE
DOIT PAS (MUST NOT) changer la réponse d'un échec qu'elle ne nomme pas.

#### Scenario: Un conflit de révision d'un policy pack
- **GIVEN** une mise à jour de policy pack dont la révision attendue est périmée
- **WHEN** un client l'envoie
- **THEN** la réponse est un 409 `REVISION_CONFLICT`, et non `STATE_CONFLICT`
- **AND** la révision persistée est inchangée

#### Scenario: Un dépassement du budget d'export, sous-type d'un conflit d'état
- **GIVEN** un export de requête plus grand que la borne d'export
- **WHEN** un client l'appelle sous une route de requête
- **THEN** la réponse est un 400 `QUERY_BUDGET_EXCEEDED`, la correspondance propre l'emportant sur le 409 commun

#### Scenario: Une entrée invalide de raisonnement
- **WHEN** un client envoie à `POST /api/v1/reasoning/analyze` un corps qui viole une validation
- **THEN** la réponse est un 400 `REASONING_VALIDATION`

### Requirement: The build SHALL refuse a handler of the HTTP adapter that maps a failure to a status by itself

La suite d'architecture DOIT (MUST) échouer, en nommant la classe, quand une classe de `morpheus-api` autre que la
table de correspondance attrape un échec de la famille commune pour en écrire une réponse, ou trace un échec imprévu
avant d'écrire un 500. Le serveur distant, qui écrit la réponse de la façade, est nommé avec sa raison.

#### Scenario: Un routeur ajouté avec sa propre échelle de catch
- **GIVEN** un routeur à contexte propre qui attrape `IllegalStateException` et écrit lui-même un 409
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le routeur
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une exemption devenue sans objet
- **WHEN** la classe nommée comme exemption n'attrape plus d'échec ni ne trace d'échec imprévu
- **THEN** la suite échoue jusqu'à ce que l'exemption soit retirée
