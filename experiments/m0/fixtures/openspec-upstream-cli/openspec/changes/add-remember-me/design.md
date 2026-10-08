# Design

## Context

Sessions expire after 30 minutes of inactivity (`auth-session`). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Keep opted-in users authenticated across the inactivity window.

**Non-Goals:**
- Single sign-on across devices.

## Decisions

1. **Store a hashed, revocable persistent credential** rather than extending the session lifetime: revoking one
   device must not end the others.

## Risks / Trade-offs

- [A stolen persistent credential grants access] → it is bound to the device and revoked on password change.
