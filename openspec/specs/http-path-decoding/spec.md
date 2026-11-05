# http-path-decoding Specification

## Purpose
Define how the HTTP adapter turns a request path into segments: decoded exactly once, by one function, and identically
for the code that authorizes a request and the code that routes it.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/http-path-decoding.md](../../../docs/developer/specifications/http-path-decoding.md).

## Requirements

### Requirement: A path segment SHALL be percent-decoded exactly once and `+` SHALL stay `+`

The HTTP adapter MUST split the raw request path into segments and decode each segment's percent escapes exactly once,
MUST NOT turn `+` into a space in a path, and MUST answer 404 to a path with an empty segment or a malformed escape.

#### Scenario: A doubly encoded dot is not a dot

- **WHEN** a request path contains the segment `%252e%252e`
- **THEN** the segment the router sees is `%2e%2e`, not `..`

#### Scenario: An encoded slash does not create a segment

- **WHEN** a request path contains the segment `a%2Fb`
- **THEN** the router sees one segment, `a/b`, and the number of segments is the number of `/` separators sent

#### Scenario: A plus sign is a plus sign

- **WHEN** a request path contains the segment `a+b`
- **THEN** the router sees `a+b`

#### Scenario: A malformed escape is refused

- **WHEN** a request path contains `%zz` or a truncated escape
- **THEN** the answer is 404 with "invalid API path", as for an empty segment

### Requirement: Authorization and routing SHALL see the same segments

The remote server's role decision and the local server's route selection MUST obtain their segments from the same
function applied to the same raw path.

#### Scenario: Hostile encodings are read identically

- **WHEN** each of a set of paths using `%2e`, `%252e`, `%2F`, `%252F`, `+`, `%2B` and mixed case escapes is given to the
  remote role policy and to the local router
- **THEN** both obtain the same list of segments, or both refuse the path

#### Scenario: A route the policy authorizes is the route that is served

- **WHEN** a request is authorized remotely for a route of the route table and forwarded
- **THEN** the local server selects that same route, with the same path parameters

### Requirement: The adapter SHALL hold one path parser

No class of `com.morpheus.api` other than the path parser MUST split a request path into segments or decode a path
escape, and `URLDecoder` MUST NOT be used on a path.

#### Scenario: The two copies are gone

- **WHEN** the architecture suite reads the policy and query routers after this change
- **THEN** neither contains a segment-splitting loop nor a call to `URLDecoder`

#### Scenario: A new form decoding of a path fails the build

- **WHEN** a class of the adapter calls `URLDecoder.decode` and is not one of the classes listed as decoding query
  parameters, each with its reason
- **THEN** the suite fails and names the class

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
