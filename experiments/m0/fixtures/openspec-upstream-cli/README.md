# openspec-upstream-cli

A workspace produced by the OpenSpec CLI itself, not written by hand: the other `openspec-*` fixtures use the dialect
MORPHEUS was first written against (`# Proposal: <title>`, `## Intent`), this one uses the CLI's `spec-driven` templates
(`# Proposal`, `## Why`, `## What Changes`, `## Capabilities`, `## Impact`).

- **OpenSpec CLI version**: 1.14.1
- **Generated on**: 2026-10-08
- **How**: `openspec init --tools none`; `openspec new change add-session-expiry` with its artifacts written from
  `openspec instructions <artifact>` templates, then `openspec archive add-session-expiry -y`, which wrote
  `openspec/specs/auth-session/spec.md`; then `openspec new change add-remember-me` (MODIFIED and ADDED requirements,
  design, tasks). `openspec validate` accepts both changes.

Regenerate it with a newer CLI when the supported version moves: a template change then shows up as a failing test
instead of as a user's workspace that MORPHEUS cannot read.
