# Design

## Context

Measured at `3ec3ea46` by import grep and POM reading: `morpheus-api/pom.xml` depends on `morpheus-store-sqlite`
and `morpheus-provider-openspec` (compile), `morpheus-mcp/pom.xml` on `morpheus-store-sqlite` (compile). The importing
classes are `ApiRuntime`, `MorpheusLocalHttpServerBootstrap`, `MorpheusRemoteHttpServer(Bootstrap)`,
`Morpheus{Operability,Policy,Portfolio,ProjectSync,Query}ApiService`, `MorpheusPolicyManagementHttpRoutes`,
`MorpheusMcpRuntime`, `MorpheusMcpServer`, `Morpheus{Composition,PolicyMcpManagement,Policy,Portfolio,Query}McpTools`.
`LayerDependencyTest#httpApiMustRemainSiblingOfCliAndMcp` forbids api → cli/mcp/integration only; nothing has
`com.morpheus.mcp..` as subject except the rule added by the 2026-10-08 audit (mcp → api/cli). Providers, stores and
integrations already have sibling rules (`AdapterSiblingArchitectureTest`).

## Goals / Non-Goals

**Goals:** one written decision; one rule that holds it; documentation that matches the code.

**Non-Goals:** choosing for the maintainer; moving code before the decision.

## Decisions

1. **Record the decision as a new ADR, not as an edit of ADR-0003**: ADR-0003's text is a dated decision; an
   amendment points to the new ADR.
2. **Enforce option (a) with an allowlist that may only shrink**, in the same style as the HTTP router groups of
   `HttpRoutesTransportBoundaryArchitectureTest` (named constants, a test refusing an unclassified offender and a
   vanished one). Option (b) is enforced by a plain `noClasses()` rule once the move is done.
3. **Measure the two unbacked rules before proposing them**: run the transport-independence rule and
   `slices().matching("com.morpheus.(domain|application|api).(*)..").should().beFreeOfCycles()` once, record the
   violations, and let the ADR accept, reject or defer each.

## Risks / Trade-offs

- [Option (b) is a large refactor of runtime wiring] → the ADR can choose (a) now and (b) as a later milestone.
- [A cycle rule may fail widely on first run] → it is proposed only after measurement, never added red.
