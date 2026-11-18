## Purpose
State what the remote server requires of a credential file, its identity file and its TLS keystore, before reading it, so that another account of the host cannot grant itself a role. Durable page: `docs/developer/specifications/remote-credential-file-trust.md`.

## ADDED Requirements

### Requirement: The identity file is read only when no other account can write it
Before reading the remote identity file, at start and at every reload, the server MUST verify that it is a regular file owned by the account running MORPHEUS or a trusted administrator, and that no other principal can write it. Otherwise it MUST refuse by name, without reading or authenticating any identity, and MUST NOT change the permissions of the file.

#### Scenario: A group-writable identity file at start
- **GIVEN** a valid identity file whose POSIX mode is `rw-rw----`
- **WHEN** the remote server starts
- **THEN** the start is refused with a message naming the identity file permission and its remedy
- **AND** the mode of the file is unchanged and no server listens

#### Scenario: An identity file that becomes group-writable while the server runs
- **GIVEN** a running remote server whose identity file is changed to `rw-rw----` and given a new line
- **WHEN** a client presents the token of that new line
- **THEN** the request is not authenticated and the refusal is named
- **AND** no identity of the changed file is accepted until its permissions are restored

#### Scenario: An identity file written by the identity commands
- **GIVEN** an identity file written by `server identity create`
- **WHEN** the remote server starts and serves an authenticated request
- **THEN** the file is accepted as before

#### Scenario: An ACL that grants write to an untrusted principal
- **GIVEN** an identity file on an ACL file system whose ACL grants a write right to a principal other than the owner and the trusted administrators
- **WHEN** the remote server starts
- **THEN** the start is refused by name

### Requirement: The TLS keystore is read only when no other account can write it
Before reading the TLS keystore, the remote server MUST apply the same verification as for the identity file, and MUST refuse to start by name when another principal can write the keystore, without reading it.

#### Scenario: A group-writable keystore
- **GIVEN** a valid PKCS12 keystore whose POSIX mode is `rw-rw----`
- **WHEN** the remote server starts
- **THEN** the start is refused with a message naming the keystore permission and its remedy
- **AND** the keystore is not read and its mode is unchanged
