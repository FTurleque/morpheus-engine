# Authentication Session Specification

## Purpose

Décrire le comportement courant de la session authentifiée.

## Requirements

### Requirement: Session expiration

The system SHALL expire an authenticated session after 30 minutes of inactivity.

#### Scenario: Expire an inactive session

- **GIVEN** an authenticated user session
- **AND** no activity has occurred for 30 minutes
- **WHEN** the user attempts a protected action
- **THEN** the system SHALL require authentication again

### Requirement: Session activity refresh

The system SHALL refresh the inactivity timer when an authenticated user performs a protected action.

#### Scenario: Refresh session activity

- **GIVEN** an authenticated user session with remaining inactivity time
- **WHEN** the user performs a protected action
- **THEN** the inactivity timer SHALL restart from 30 minutes
