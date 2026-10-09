# Proposal

## Why

ADR-0003 lists "no dependency of CLI/MCP/API on the chosen database" as a consequence of the knowledge-store
decision, and the project rules state that adapters are siblings that never call each other, with wiring explicit
in `MorpheusMain`. At `3ec3ea46`, `morpheus-api` and `morpheus-mcp` declare compile dependencies on
`morpheus-store-sqlite`, and `morpheus-api` on `morpheus-provider-openspec`; 17 production classes import them
(`ApiRuntime`, `MorpheusMcpRuntime`, `Morpheus*ApiService`, `Morpheus*McpTools`, the server bootstraps). No ADR
records this as accepted and no rule constrains it, so the documented model and the code disagree silently
(audit `docs/audits/AUDIT_OUTILLE_2026-10-08.md`, finding ARC-AUD-3). This change asks for the decision; it does not
presume which way it goes.

## What Changes

- An ADR decides between: (a) the transport adapters are composition roots for their own runtime and may depend on
  concrete stores and providers through a named, shrinking set of classes; or (b) wiring moves back to `morpheus-cli`
  and the adapters depend on application ports only.
- The decision is enforced: an ArchUnit rule refuses any new dependency from `com.morpheus.api..` /
  `com.morpheus.mcp..` to `com.morpheus.store..` / `com.morpheus.provider..` outside the allowed classes (for (a)), or
  outright (for (b), after the move).
- ADR-0003, `.claude/CLAUDE.md` and `.claude/rules/architecture.md` describe the model the code follows.
- Two further rules, measured by the audit but not backed by a written decision, are put to the same decision:
  `morpheus-mcp-transport` depends on no MORPHEUS package (stated only in test Javadocs), and package slices of
  `domain`, `application` and `api` are free of cycles (never measured).

## Capabilities

### New Capabilities

(none — this change records an architecture decision and its enforcement; no externally observable behaviour changes)

### Modified Capabilities

(none)

## Impact

- `docs/adr/` (new ADR; amendment of ADR-0003), `.claude/CLAUDE.md`, `.claude/rules/architecture.md`.
- `morpheus-architecture-tests`: new rule(s) with an allowlist that may only shrink.
- For option (b) only: `morpheus-api`, `morpheus-mcp`, `morpheus-cli` POMs and wiring.
