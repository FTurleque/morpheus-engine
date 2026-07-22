# Authentication Session Delta

## MODIFIED Requirements

### Requirement: Session expiration

The system SHALL expire a standard authenticated session after 30 minutes of inactivity and SHALL preserve a remember-me session according to its persistent-session policy when the user explicitly opted in.

#### Scenario: Expire a standard inactive session

- **GIVEN** an authenticated user session without remember-me enabled
- **AND** no activity has occurred for 30 minutes
- **WHEN** the user attempts a protected action
- **THEN** the system SHALL require authentication again

#### Scenario: Preserve an opted-in remember-me session

- **GIVEN** an authenticated session created with remember-me explicitly enabled
- **WHEN** the standard inactivity window expires
- **THEN** the system MAY restore authentication using the valid persistent credential
- **AND** restoration SHALL fail if that credential has been revoked

## ADDED Requirements

### Requirement: Explicit remember-me opt-in

The system SHALL enable persistent authentication only when the user explicitly requests remember-me during authentication.

#### Scenario: Default authentication remains non-persistent

- **GIVEN** a user authenticating without selecting remember-me
- **WHEN** authentication succeeds
- **THEN** the session SHALL use the standard non-persistent policy

#### Scenario: User opts into persistent authentication

- **GIVEN** a user authenticating on a trusted device
- **WHEN** the user explicitly selects remember-me
- **THEN** the system SHALL create a revocable persistent credential

### Requirement: Persistent credential revocation

The system SHALL revoke the active persistent credential when the user explicitly logs out.

#### Scenario: Logout prevents future restoration

- **GIVEN** a valid persistent credential
- **WHEN** the user explicitly logs out
- **THEN** the persistent credential SHALL be revoked
- **AND** a later request SHALL NOT restore authentication from that credential
