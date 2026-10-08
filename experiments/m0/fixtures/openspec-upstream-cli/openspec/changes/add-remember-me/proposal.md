# Proposal

## Why

Users who sign in from a personal device ask to stay authenticated across the 30-minute inactivity limit.

## What Changes

- Let a user opt in to a persistent session ("remember me") when authenticating.
- Keep expiring sessions that did not opt in.

## Capabilities

### New Capabilities

### Modified Capabilities

- `auth-session`: an opted-in session survives the inactivity window through a persistent credential.

## Impact

Authentication form, session middleware, credential storage.
