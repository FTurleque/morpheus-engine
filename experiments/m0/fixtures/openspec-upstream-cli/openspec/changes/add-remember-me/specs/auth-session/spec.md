# Spec Delta

## MODIFIED Requirements

### Requirement: Session expiration
The system SHALL expire an authenticated session after 30 minutes of inactivity unless the user explicitly opted in to remember-me.

#### Scenario: Expire an inactive session
- **WHEN** no activity has occurred for 30 minutes on a session without remember-me
- **THEN** the system SHALL require authentication again before a protected action

#### Scenario: Preserve an opted-in session
- **WHEN** the inactivity window expires on a session created with remember-me
- **THEN** the system SHALL restore authentication from the valid persistent credential

## ADDED Requirements

### Requirement: Explicit remember-me opt-in
The system SHALL enable persistent authentication only when the user explicitly requests remember-me while authenticating.

#### Scenario: Default authentication stays non-persistent
- **WHEN** the user authenticates without selecting remember-me
- **THEN** the system SHALL NOT issue a persistent credential
