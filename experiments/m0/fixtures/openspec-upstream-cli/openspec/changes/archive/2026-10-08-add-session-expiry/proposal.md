# Proposal

## Why

Authenticated sessions never expire, so an unattended browser keeps access indefinitely.

## What Changes

- Expire an authenticated session after 30 minutes of inactivity.

## Capabilities

### New Capabilities

- `auth-session`: how long an authenticated session stays valid and when it requires authentication again.

### Modified Capabilities

## Impact

Session middleware and its configuration.
