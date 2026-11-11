# Proposal

## Why

AUD-SEC-05 (sprint 3, Low) reports that the Java command of an MCP peer is looked up on `PATH`. Re-read on `develop` at
`0f3b0ad0` on 2026-10-12, the finding **holds**:

- `MinosIntegrationSettings.java:68` and `NexusIntegrationSettings.java:67` resolve the command as the configured value
  or the literal `java`.
- `BoundedStdioClientTransport` keeps `PATH` in the allowlist of inherited environment keys and starts the command with
  `ProcessBuilder`, so the operating system searches `PATH` for it.
- The provider-plugin probe already does the opposite: `ProviderPluginProbeProcess.java:268` starts its child from
  `java.home`, never from `PATH`, and the packaging keeps the runtime's launcher for that purpose.

A manipulated `PATH` in the environment of the MORPHEUS process makes it execute another `java` as its MINOS or NEXUS
peer (untrusted search path, CWE-426). The damage is bounded to the account running MORPHEUS; it is also avoidable.

## What Changes

- Without a configured command, a peer is started with the Java launcher of the runtime MORPHEUS itself runs on.
- A configured command must be an absolute path to an existing regular file; a bare name or a relative path is refused
  when the settings are resolved, with a named reason.
- The status surfaces report the resolved launcher as they report the JAR today, under the existing disclosure rules.

## Capabilities

### New Capabilities

- `mcp-peer-launch-command`: which executable starts an MCP peer, and what a configured value must be to be accepted.

### Modified Capabilities

(none)

## Impact

- `morpheus-integration-minos`, `morpheus-integration-nexus` (settings and their tests), the status views that print
  `javaCommand`.
- `docs/user/INTEGRATIONS.md` (the `MORPHEUS_MINOS_JAVA` / `MORPHEUS_NEXUS_JAVA` rows).
- Behaviour change for an operator who set the variable to `java` or to a relative path: the integration reports a
  configuration error instead of starting. `PATH` stays in the inherited allowlist: the peer itself may need it.
