# Spec delta : http-path-decoding

## ADDED Requirements

### Requirement: Un seul séparateur final est ignoré

Le lecteur de chemin DOIT (MUST) ignorer un unique `/` final et DOIT (MUST) refuser par un 404 `invalid API path` un
chemin qui se termine par deux `/`, comme tout chemin portant un segment vide.

#### Scenario: Un séparateur final ne change pas la route

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/api/v1/projects/`
- **THEN** la route lue est celle de `/api/v1/projects`, avec le seul segment `projects`
- **AND** aucun état n'est modifié par la lecture du chemin

#### Scenario: Deux séparateurs finaux sont refusés

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/api/v1/projects//` ou `/api/v1//`
- **THEN** la réponse est 404, code `NOT_FOUND`, message `invalid API path`
- **AND** aucune opération n'est exécutée

### Requirement: Le préfixe de l'API est reconnu segment par segment

Le lecteur de chemin DOIT (MUST) comparer le préfixe de l'API aux premiers segments décodés du chemin, jamais à son
texte, et DOIT (MUST) refuser un chemin dont les premiers segments ne sont pas ce préfixe par un 404 `NOT_FOUND` de
message `unknown API route: <chemin brut>`.

#### Scenario: Un préfixe collé au segment suivant n'est pas le préfixe

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/api/v1health`
- **THEN** la réponse est 404, code `NOT_FOUND`, et son message commence par `unknown API route:`

#### Scenario: Un séparateur encodé ne forme pas le préfixe

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/api%2Fv1/health`
- **THEN** la réponse est 404, code `NOT_FOUND`, et son message commence par `unknown API route:`

#### Scenario: Un préfixe étranger est nommé dans le refus

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/other/projects`
- **THEN** la réponse est 404, code `NOT_FOUND`, message `unknown API route: /other/projects`
- **AND** aucune opération n'est exécutée

### Requirement: Un segment qui n'est pas de l'UTF-8 valide est refusé

Le lecteur de chemin DOIT (MUST) décoder les octets d'un segment en UTF-8 strict et DOIT (MUST) refuser par un 404
`invalid API path` un segment dont les octets décodés ne forment pas de l'UTF-8 valide, sans remplacer aucun octet.

#### Scenario: Un octet isolé hors UTF-8 est refusé

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise `/api/v1/%ff` ou `/api/v1/%C3`
- **THEN** la réponse est 404, code `NOT_FOUND`, message `invalid API path`
- **AND** aucune opération n'est exécutée

#### Scenario: Un caractère encodé valide est lu quelle que soit la casse de l'échappement

- **GIVEN** un serveur local démarré
- **WHEN** une requête vise un segment écrit `caf%C3%A9` ou `caf%c3%a9`
- **THEN** le segment lu est `café` dans les deux cas
