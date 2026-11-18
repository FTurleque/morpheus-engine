# Spec delta : suppressed-failure-trace

## ADDED Requirements

### Requirement: L'échec de nettoyage après commit d'une transaction SQLite est compté

Quand la remise en auto-commit d'une connexion échoue après le commit d'une transaction, le store DOIT (MUST) compter
cet échec une fois, sur le recorder avec lequel la connexion a été ouverte, sous la famille
`sqlite-post-commit-cleanup`, que la reprise réussisse, que la portée remplace sa connexion ou que la connexion soit
mise en quarantaine. La mutation validée DOIT rester rapportée comme réussie.

#### Scenario: Une connexion mise en quarantaine est visible

- **GIVEN** une connexion ouverte avec un recorder, dont la remise en auto-commit échoue après le commit, à la reprise
  comme à la première tentative, hors de toute portée
- **WHEN** une transaction est exécutée sur elle
- **THEN** la transaction rend son résultat, et le compteur `failure.suppressed.sqlite-post-commit-cleanup` du recorder
  a avancé de un
- **AND** la mutation est persistée, et la transaction suivante sur cette connexion est refusée par le message qui
  nomme la quarantaine

#### Scenario: Une reprise réussie est comptée aussi

- **GIVEN** une connexion dont la remise en auto-commit échoue une fois puis réussit à la reprise
- **WHEN** une transaction est exécutée sur elle
- **THEN** le compteur de la famille a avancé de un et la connexion reste utilisable
- **AND** la mutation est persistée

#### Scenario: Le compteur est servi par la route existante

- **GIVEN** un serveur local dont une opération a connu un échec de nettoyage après commit
- **WHEN** les métriques sont demandées
- **THEN** la réponse porte `failure.suppressed.sqlite-post-commit-cleanup` avec sa valeur, sans route nouvelle

#### Scenario: Une transaction dont le nettoyage réussit n'est pas comptée

- **WHEN** une transaction est validée et la remise en auto-commit réussit du premier coup
- **THEN** le compteur de la famille n'a pas bougé
