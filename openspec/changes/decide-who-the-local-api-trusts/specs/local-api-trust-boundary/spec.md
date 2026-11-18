## Purpose
State which callers the local HTTP API trusts, how it tells them from other accounts of the host, and what the operator is told about that boundary. Durable page: `docs/developer/specifications/local-api-trust-boundary.md`.

## ADDED Requirements

### Requirement: The trust boundary of the local API is stated to the operator
`SECURITY.md` and the user guide of `morpheus api` MUST state which accounts of the host the local API trusts, what an untrusted local account can and cannot do through it, and how this boundary relates to the protection of the database against other accounts.

#### Scenario: The operator reads the security policy
- **GIVEN** `SECURITY.md` and the user guide of `morpheus api`
- **WHEN** an operator looks for who may call the local API
- **THEN** both state the boundary decided for the local API and name what it protects

### Requirement: The local API refuses a request that does not carry the local capability
The local server MUST refuse, before any route is served, a request that does not carry the capability of the running process, with a named error code and no change to the database. The capability MUST be written to a file of the data directory readable by the account running MORPHEUS alone, and MUST change at every start.

#### Scenario: A request from another account without the capability
- **GIVEN** a running local server and a client that does not hold the capability file
- **WHEN** the client sends `POST /api/v1/projects` with a workspace path
- **THEN** the server answers with a named refusal and no project is registered
- **AND** the database is unchanged

#### Scenario: A request from the operator's client with the capability
- **GIVEN** a running local server and a client of the operator account that reads the capability file
- **WHEN** it sends the same request with the capability
- **THEN** the request is served as before

#### Scenario: The capability file is private and renewed
- **GIVEN** the local server has started twice in a row
- **WHEN** the capability file is inspected after each start
- **THEN** it is readable by the running account alone and its content differs between the two starts

#### Scenario: The probes stay reachable
- **GIVEN** a running local server
- **WHEN** a client without the capability calls `GET /api/v1/health`
- **THEN** the probe answers, and no other route is served without the capability
