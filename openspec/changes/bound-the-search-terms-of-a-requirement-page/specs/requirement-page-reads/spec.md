## ADDED Requirements

### Requirement: The number of terms of a requirement search SHALL be bounded
A requirement search MUST carry at most a declared number of distinct terms, repeated terms counting once. A search beyond that number MUST be refused as an invalid request whose message names the bound, before the store is read, on HTTP, CLI and MCP alike, and MUST NOT surface as a database failure.

#### Scenario: A search at the bound
- **GIVEN** a published snapshot and a search of exactly the declared number of distinct terms
- **WHEN** the search is run
- **THEN** a page is returned as for any other search

#### Scenario: Repeated terms count once
- **GIVEN** a search that repeats one term many more times than the bound
- **WHEN** the search is run
- **THEN** it is accepted and returns the same page as the search of that term alone

#### Scenario: A search beyond the bound over HTTP
- **GIVEN** a published snapshot
- **WHEN** a client sends `GET /api/v1/projects/{projectId}/requirements` whose `query` holds one more distinct term than the bound
- **THEN** the server answers `400` with a named refusal that names the bound
- **AND** no database read is made and no worker is held beyond the refusal

#### Scenario: A search beyond the bound through the CLI and MCP
- **GIVEN** a published snapshot
- **WHEN** `requirements find` or `find_requirements` receives one more distinct term than the bound
- **THEN** each refuses with the same named reason, and nothing is read

### Requirement: The cost of matching a requirement SHALL NOT grow with each term beyond one corpus per row
A store that evaluates a requirement search MUST build the searched corpus of a requirement at most once per row read, whatever the number of terms, and MUST return the same page and total as when every term is matched separately.

#### Scenario: A search of many terms on the SQLite store
- **GIVEN** a snapshot on the SQLite store and a search at the term bound
- **WHEN** the page and its total are read
- **THEN** the corpus of each CURRENT row read is built once
- **AND** the page and total equal those of the in-memory store for the same search
