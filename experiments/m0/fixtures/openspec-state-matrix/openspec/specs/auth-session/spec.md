# Auth session

## Requirements

### Requirement: Session expiration

The system SHALL expire an authenticated session after 30 minutes.

#### Scenario: Default timeout

- **WHEN** 30 minutes pass
- **THEN** the session SHALL expire
