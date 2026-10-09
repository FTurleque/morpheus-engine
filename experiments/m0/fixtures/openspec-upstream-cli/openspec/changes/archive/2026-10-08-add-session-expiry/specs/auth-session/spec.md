# Spec Delta

## Purpose

Define how long an authenticated session stays valid and when the user must authenticate again.

## ADDED Requirements

### Requirement: Session expiration
The system SHALL expire an authenticated session after 30 minutes of inactivity.

#### Scenario: Expire an inactive session
- **WHEN** no activity has occurred on an authenticated session for 30 minutes
- **THEN** the system SHALL require authentication again before a protected action
