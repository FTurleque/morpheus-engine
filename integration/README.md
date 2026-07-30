# MORPHEUS MCP client integration

MORPHEUS exposes a native MCP server over STDIO:

```text
morpheus mcp --stdio
```

## Windows

The portable archive and Windows setup include:

```text
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
```

Manual opt-in configuration example:

```powershell
& .\integration\configure-mcp-clients.ps1 `
  -InstallRoot $PSScriptRoot\.. `
  -CopilotJetBrains `
  -CopilotCli `
  -ClaudeCode `
  -ClaudeDesktop `
  -Codex
```

Remove only integrations still owned by MORPHEUS:

```powershell
& .\integration\configure-mcp-clients.ps1 `
  -InstallRoot $PSScriptRoot\.. `
  -Action Uninstall
```

The manager preserves unrelated servers, backs up JSON files before writes, refuses to overwrite unmanaged entries named `morpheus`, and preserves entries modified after installation.

## Linux

Client configuration is currently manual. Point the MCP client to:

```text
<install-root>/bin/morpheus
```

with arguments:

```text
mcp
--stdio
```

Use the same data and configuration roots for every client:

```text
MORPHEUS_DATA_DIR=$XDG_DATA_HOME/morpheus
MORPHEUS_CONFIG_DIR=$XDG_CONFIG_HOME/morpheus
```

When the XDG variables are unset, use the defaults documented in `docs/user/MCP_CLIENTS.md`.
