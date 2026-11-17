## Purpose
Bound what the remote server spends on a connection before its request is authenticated, so that a client with mere network access cannot hold descriptors and threads without limit. Durable page: `docs/developer/specifications/remote-pre-authentication-bounds.md`.

## ADDED Requirements

### Requirement: A connection that stalls before its request is read is closed in bounded time
The remote server MUST close a connection that has not completed its TLS handshake, or has not sent the full header section of its request, within a bounded time counted from its acceptance. The bound MUST hold whether the client sends nothing, a partial TLS record, or partial headers.

#### Scenario: A partial TLS handshake
- **GIVEN** a running remote server
- **WHEN** a client opens a TCP connection, sends the first bytes of a TLS ClientHello and then stays silent
- **THEN** the server closes the connection within the bound
- **AND** no access journal record is written and the authentication failure counter does not move

#### Scenario: Partial request headers over an established TLS session
- **GIVEN** a running remote server
- **WHEN** a client completes the TLS handshake, sends `GET /api/v1/health HTTP/1.1` and a `Host` header without the terminating empty line, then stays silent
- **THEN** the server closes the connection within the bound
- **AND** no access journal record is written

#### Scenario: A client that completes its request in time is served
- **GIVEN** a running remote server
- **WHEN** an authenticated client sends a complete request within the bound
- **THEN** the request is served as before

### Requirement: The number of open connections of the remote server is bounded
The remote server MUST bound the number of connections it holds open at once, counted before authentication. A connection beyond the bound MUST NOT be held open by the server, and connections already open MUST keep being served.

#### Scenario: Connections beyond the bound
- **GIVEN** a remote server holding as many stalled connections as its bound
- **WHEN** another client connects
- **THEN** the new connection is closed or refused without reaching authentication
- **AND** once the stalled connections are closed by the time bound, a new authenticated request is served

### Requirement: The pre-authentication bounds are stated to the operator
`SECURITY.md` and the remote server guide MUST state which pre-authentication bounds the remote server applies, how they are set, and what remains outside them, including any component the deployment must place in front of the server.

#### Scenario: The operator reads the remote server guide
- **GIVEN** the remote server guide and `SECURITY.md`
- **WHEN** an operator looks for what protects the server before authentication
- **THEN** both name the time bound, the connection bound and their limits
