[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'M28 MCP client integration verification must run on Windows.'
}

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Manager = Join-Path $RepoRoot 'integration\configure-mcp-clients.ps1'
if (-not (Test-Path -LiteralPath $Manager -PathType Leaf)) {
    throw "MCP client integration manager not found: $Manager"
}

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Read-Json([string] $Path) {
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Write-Utf8Json([string] $Path, [object] $Value) {
    $Parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $Parent | Out-Null
    $Json = $Value | ConvertTo-Json -Depth 32
    [System.IO.File]::WriteAllText($Path, $Json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Assert-Utf8WithoutBom([string] $Path, [string] $DisplayName) {
    $Bytes = [System.IO.File]::ReadAllBytes($Path)
    $HasBom = $Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and $Bytes[2] -eq 0xBF
    Assert-True (-not $HasBom) "$DisplayName contains a UTF-8 BOM."
}

function New-FakeMcpCli([string] $Directory, [string] $Name) {
    $Path = Join-Path $Directory "$Name.cmd"
    @"
@echo off
setlocal
set "STATE=%~dp0$Name.state"
if /I "%~1"=="--version" (
  echo $Name version 0.0.0-test
  exit /b 0
)
if /I "%~1"=="mcp" if /I "%~2"=="get" (
  if exist "%STATE%" (
    type "%STATE%"
    exit /b 0
  )
  exit /b 1
)
if /I "%~1"=="mcp" if /I "%~2"=="add" (
  >"%STATE%" echo %*
  exit /b 0
)
if /I "%~1"=="mcp" if /I "%~2"=="remove" (
  if exist "%STATE%" del /q "%STATE%"
  exit /b 0
)
exit /b 2
"@ | Set-Content -LiteralPath $Path -Encoding ascii
    return $Path
}

function Read-IniReport([string] $Path) {
    $Sections = [ordered]@{}
    $Current = $null
    foreach ($Line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $Trimmed = $Line.Trim()
        if ($Trimmed -eq '') { continue }
        if ($Trimmed.StartsWith('[') -and $Trimmed.EndsWith(']')) {
            $Current = $Trimmed.Substring(1, $Trimmed.Length - 2)
            $Sections[$Current] = @{}
            continue
        }
        if ($null -eq $Current) { continue }
        $SplitIndex = $Trimmed.IndexOf('=')
        if ($SplitIndex -lt 0) { continue }
        $Sections[$Current][$Trimmed.Substring(0, $SplitIndex)] = $Trimmed.Substring($SplitIndex + 1)
    }
    return $Sections
}

function Assert-ClientState([hashtable] $Report, [string] $Section, [string] $Expected, [string] $ScenarioName) {
    Assert-True ($Report.Contains($Section)) "$ScenarioName - section '$Section' missing from Detect report."
    $Row = $Report[$Section]
    foreach ($Flag in @('Available', 'AlreadyManaged', 'NeedsRepair', 'Conflict')) {
        $ExpectedValue = if ($Flag -eq $Expected) { '1' } else { '0' }
        Assert-True ($Row[$Flag] -eq $ExpectedValue) "$ScenarioName - $Section.$Flag expected $ExpectedValue but was '$($Row[$Flag])'."
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace($Row['Reason'])) "$ScenarioName - $Section.Reason is empty."
}

# The wizard and the manager must never see a different state than each other -- this is the same code path
# Install/Uninstall use, so every Detect call below runs with its own sandboxed PATH to keep scenarios isolated.
function Invoke-Detect(
    [string] $DetectInstallRoot, [string] $DetectDataRoot, [string] $DetectConfigRoot,
    [string] $DetectStatePath, [string] $DetectOut,
    [string] $DetectCopilotJetBrainsConfig, [string] $DetectClaudeDesktopConfig,
    [string] $DetectPath, [int] $DetectTimeoutSeconds = 20
) {
    $SavedPath = $env:Path
    try {
        $env:Path = $DetectPath
        & $Manager -InstallRoot $DetectInstallRoot -Action Detect `
            -DataRoot $DetectDataRoot -ConfigRoot $DetectConfigRoot `
            -StatePath $DetectStatePath `
            -LogPath (Join-Path (Split-Path -Parent $DetectOut) 'detect.log') `
            -BackupRoot (Join-Path (Split-Path -Parent $DetectOut) 'detect-backups') `
            -CopilotJetBrainsConfigPath $DetectCopilotJetBrainsConfig `
            -ClaudeDesktopConfigPath $DetectClaudeDesktopConfig `
            -Out $DetectOut -NativeCommandTimeoutSeconds $DetectTimeoutSeconds
        return Read-IniReport -Path $DetectOut
    }
    finally { $env:Path = $SavedPath }
}

$Root = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-m28-mcp-clients-" + [Guid]::NewGuid())
$OldPath = $env:Path
try {
    $InstallRoot = Join-Path $Root 'MORPHEUS install with spaces'
    $FakeBin = Join-Path $Root 'fake-bin'
    $DataRoot = Join-Path $Root 'persistent data'
    $ConfigRoot = Join-Path $Root 'persistent config'
    $StatePath = Join-Path $Root 'state\mcp-client-integrations.json'
    $LogPath = Join-Path $Root 'logs\mcp-clients.log'
    $BackupRoot = Join-Path $Root 'backups'
    $CopilotConfig = Join-Path $Root 'copilot\mcp.json'
    $ClaudeDesktopConfig = Join-Path $Root 'claude-desktop\claude_desktop_config.json'

    New-Item -ItemType Directory -Force -Path $InstallRoot, $FakeBin, $DataRoot, $ConfigRoot | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $InstallRoot 'morpheus.exe') | Out-Null
    New-FakeMcpCli -Directory $FakeBin -Name 'copilot' | Out-Null
    New-FakeMcpCli -Directory $FakeBin -Name 'claude' | Out-Null
    New-FakeMcpCli -Directory $FakeBin -Name 'codex' | Out-Null
    $env:Path = "$FakeBin;$OldPath"

    Write-Utf8Json -Path $CopilotConfig -Value ([pscustomobject][ordered]@{
        servers = [pscustomobject][ordered]@{
            memory = [pscustomobject][ordered]@{ command = 'npx'; args = @('memory-server') }
        }
        keepMe = 'copilot-value'
    })
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value ([pscustomobject][ordered]@{
        mcpServers = [pscustomobject][ordered]@{
            filesystem = [pscustomobject][ordered]@{ command = 'npx'; args = @('filesystem-server') }
        }
        keepMe = 'claude-value'
    })

    & $Manager `
        -InstallRoot $InstallRoot `
        -CopilotJetBrains -CopilotCli -ClaudeCode -ClaudeDesktop -Codex `
        -Strict `
        -DataRoot $DataRoot `
        -ConfigRoot $ConfigRoot `
        -StatePath $StatePath `
        -LogPath $LogPath `
        -BackupRoot $BackupRoot `
        -CopilotJetBrainsConfigPath $CopilotConfig `
        -ClaudeDesktopConfigPath $ClaudeDesktopConfig

    $ExpectedExe = Join-Path $InstallRoot 'morpheus.exe'
    $Copilot = Read-Json -Path $CopilotConfig
    $ClaudeDesktop = Read-Json -Path $ClaudeDesktopConfig
    $State = Read-Json -Path $StatePath

    Assert-True ($Copilot.keepMe -eq 'copilot-value') 'Copilot unrelated root property was not preserved.'
    Assert-True ($null -ne $Copilot.servers.memory) 'Copilot existing MCP server was not preserved.'
    Assert-True ($Copilot.servers.morpheus.command -eq $ExpectedExe) 'Copilot MORPHEUS command is incorrect.'
    Assert-True (@($Copilot.servers.morpheus.args).Count -eq 2) 'Copilot MORPHEUS args count is incorrect.'
    Assert-True ($Copilot.servers.morpheus.args[0] -eq 'mcp') 'Copilot MORPHEUS first arg is incorrect.'
    Assert-True ($Copilot.servers.morpheus.args[1] -eq '--stdio') 'Copilot MORPHEUS second arg is incorrect.'
    Assert-True ($Copilot.servers.morpheus.env.MORPHEUS_DATA_DIR -eq $DataRoot) 'Copilot data root is incorrect.'
    Assert-True ($Copilot.servers.morpheus.env.MORPHEUS_CONFIG_DIR -eq $ConfigRoot) 'Copilot config root is incorrect.'

    Assert-True ($ClaudeDesktop.keepMe -eq 'claude-value') 'Claude Desktop unrelated property was not preserved.'
    Assert-True ($null -ne $ClaudeDesktop.mcpServers.filesystem) 'Claude Desktop existing MCP server was not preserved.'
    Assert-True ($ClaudeDesktop.mcpServers.morpheus.command -eq $ExpectedExe) 'Claude Desktop MORPHEUS command is incorrect.'
    Assert-True (@($State.clients).Count -eq 5) 'Expected five managed MCP client integrations.'

    foreach ($Path in @($CopilotConfig, $ClaudeDesktopConfig, $StatePath)) {
        Assert-Utf8WithoutBom -Path $Path -DisplayName $Path
    }

    foreach ($ClientName in @('copilot', 'claude', 'codex')) {
        $CliState = Get-Content -Raw -LiteralPath (Join-Path $FakeBin "$ClientName.state")
        Assert-True ($CliState.Contains($ExpectedExe)) "$ClientName did not receive MORPHEUS executable."
        Assert-True ($CliState.Contains($DataRoot)) "$ClientName did not receive MORPHEUS_DATA_DIR."
        Assert-True ($CliState.Contains($ConfigRoot)) "$ClientName did not receive MORPHEUS_CONFIG_DIR."
        Assert-True ($CliState.Contains('--stdio')) "$ClientName did not receive --stdio."
    }
    $ClaudeCliState = Get-Content -Raw -LiteralPath (Join-Path $FakeBin 'claude.state')
    Assert-True ($ClaudeCliState -match 'mcp add --scope user .* morpheus -- ') 'Claude Code options are not placed before the server name.'
    Assert-True ((Get-ChildItem -LiteralPath $BackupRoot -File -Recurse).Count -ge 2) 'Expected JSON configuration backups.'

    # Idempotent reinstall: configuration bytes and backup count stay stable.
    $CopilotBefore = [System.IO.File]::ReadAllText($CopilotConfig, [System.Text.Encoding]::UTF8)
    $ClaudeBefore = [System.IO.File]::ReadAllText($ClaudeDesktopConfig, [System.Text.Encoding]::UTF8)
    $BackupCountBefore = (Get-ChildItem -LiteralPath $BackupRoot -File -Recurse).Count
    & $Manager `
        -InstallRoot $InstallRoot `
        -CopilotJetBrains -CopilotCli -ClaudeCode -ClaudeDesktop -Codex `
        -Strict `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath $StatePath -LogPath $LogPath -BackupRoot $BackupRoot `
        -CopilotJetBrainsConfigPath $CopilotConfig -ClaudeDesktopConfigPath $ClaudeDesktopConfig
    Assert-True ([System.IO.File]::ReadAllText($CopilotConfig, [System.Text.Encoding]::UTF8) -eq $CopilotBefore) 'Idempotent Copilot reinstall rewrote JSON.'
    Assert-True ([System.IO.File]::ReadAllText($ClaudeDesktopConfig, [System.Text.Encoding]::UTF8) -eq $ClaudeBefore) 'Idempotent Claude reinstall rewrote JSON.'
    Assert-True ((Get-ChildItem -LiteralPath $BackupRoot -File -Recurse).Count -eq $BackupCountBefore) 'Idempotent reinstall created unnecessary backups.'

    # Preflight (-Action Detect) against a fully-managed installation must report AlreadyManaged for every
    # client -- and must work correctly even though every path here contains spaces (mission scenario: a
    # properly-managed client, exercised inside an install path containing spaces).
    $DetectOut = Join-Path $Root 'detect out\report.ini'
    & $Manager -InstallRoot $InstallRoot -Action Detect `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath $StatePath -LogPath $LogPath -BackupRoot $BackupRoot `
        -CopilotJetBrainsConfigPath $CopilotConfig -ClaudeDesktopConfigPath $ClaudeDesktopConfig `
        -Out $DetectOut
    $ManagedReport = Read-IniReport -Path $DetectOut
    foreach ($Section in @('copilot-jetbrains', 'claude-desktop', 'copilot-cli', 'claude-code', 'codex')) {
        Assert-ClientState $ManagedReport $Section 'AlreadyManaged' 'FullyManaged'
    }

    # A foreign entry named morpheus must never be overwritten.
    $ForeignConfig = Join-Path $Root 'foreign\mcp.json'
    Write-Utf8Json -Path $ForeignConfig -Value ([pscustomobject][ordered]@{
        servers = [pscustomobject][ordered]@{
            morpheus = [pscustomobject][ordered]@{ command = 'foreign.exe'; args = @('serve') }
        }
    })
    $ForeignBefore = [System.IO.File]::ReadAllText($ForeignConfig, [System.Text.Encoding]::UTF8)
    $ForeignState = Join-Path $Root 'foreign-state.json'
    & $Manager -InstallRoot $InstallRoot -CopilotJetBrains `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath $ForeignState -LogPath (Join-Path $Root 'foreign.log') -BackupRoot (Join-Path $Root 'foreign-backups') `
        -CopilotJetBrainsConfigPath $ForeignConfig
    Assert-True ([System.IO.File]::ReadAllText($ForeignConfig, [System.Text.Encoding]::UTF8) -eq $ForeignBefore) 'Foreign MORPHEUS entry was overwritten.'
    Assert-True (-not (Test-Path -LiteralPath $ForeignState)) 'Foreign entry was incorrectly claimed as managed.'

    # A managed entry edited by the user must be preserved during uninstall.
    $ClaudeDesktop = Read-Json -Path $ClaudeDesktopConfig
    $ClaudeDesktop.mcpServers.morpheus.command = 'user-custom-morpheus.exe'
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value $ClaudeDesktop

    & $Manager -InstallRoot $InstallRoot -Action Uninstall `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath $StatePath -LogPath $LogPath -BackupRoot $BackupRoot `
        -CopilotJetBrainsConfigPath $CopilotConfig -ClaudeDesktopConfigPath $ClaudeDesktopConfig

    $CopilotAfterUninstall = Read-Json -Path $CopilotConfig
    $ClaudeAfterUninstall = Read-Json -Path $ClaudeDesktopConfig
    $StateAfterUninstall = Read-Json -Path $StatePath
    Assert-True ($null -eq $CopilotAfterUninstall.servers.PSObject.Properties['morpheus']) 'Managed Copilot entry was not removed.'
    Assert-True ($ClaudeAfterUninstall.mcpServers.morpheus.command -eq 'user-custom-morpheus.exe') 'User-modified Claude entry was not preserved.'
    Assert-True (@($StateAfterUninstall.clients).Count -eq 1) 'Only the preserved modified entry should remain in state.'
    Assert-True (@($StateAfterUninstall.clients)[0].id -eq 'claude-desktop') 'Unexpected remaining managed entry.'
    foreach ($ClientName in @('copilot', 'claude', 'codex')) {
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $FakeBin "$ClientName.state"))) "$ClientName CLI entry was not removed."
    }

    # Restore the exact managed shape and complete the state-driven uninstall.
    $ClaudeAfterUninstall.mcpServers.morpheus.command = $ExpectedExe
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value $ClaudeAfterUninstall
    & $Manager -InstallRoot $InstallRoot -Action Uninstall `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath $StatePath -LogPath $LogPath -BackupRoot $BackupRoot `
        -CopilotJetBrainsConfigPath $CopilotConfig -ClaudeDesktopConfigPath $ClaudeDesktopConfig
    $ClaudeFinal = Read-Json -Path $ClaudeDesktopConfig
    Assert-True ($null -eq $ClaudeFinal.mcpServers.PSObject.Properties['morpheus']) 'Restored managed Claude entry was not removed.'
    Assert-True ($null -ne $ClaudeFinal.mcpServers.filesystem) 'Claude unrelated server was removed.'
    Assert-True (-not (Test-Path -LiteralPath $StatePath)) 'Managed state was not removed after clean uninstall.'

    # Invalid JSON is a hard failure in strict mode and remains byte-identical.
    $InvalidConfig = Join-Path $Root 'invalid\mcp.json'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $InvalidConfig) | Out-Null
    [System.IO.File]::WriteAllText($InvalidConfig, '{ invalid json', [System.Text.UTF8Encoding]::new($false))
    $InvalidBefore = [System.IO.File]::ReadAllText($InvalidConfig, [System.Text.Encoding]::UTF8)
    $Failed = $false
    try {
        & $Manager -InstallRoot $InstallRoot -CopilotJetBrains -Strict `
            -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
            -StatePath (Join-Path $Root 'invalid-state.json') -LogPath (Join-Path $Root 'invalid.log') `
            -BackupRoot (Join-Path $Root 'invalid-backups') -CopilotJetBrainsConfigPath $InvalidConfig
    }
    catch { $Failed = $true }
    Assert-True $Failed 'Invalid JSON did not fail in strict mode.'
    Assert-True ([System.IO.File]::ReadAllText($InvalidConfig, [System.Text.Encoding]::UTF8) -eq $InvalidBefore) 'Invalid JSON file was modified.'

    # A managed CLI entry edited by hand outside MORPHEUS must survive uninstall too, mirroring the JSON case
    # above but through the CLI (kind='cli') code path.
    & $Manager -InstallRoot $InstallRoot -ClaudeCode `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath (Join-Path $Root 'cli-modify-state.json') -LogPath (Join-Path $Root 'cli-modify.log') `
        -BackupRoot (Join-Path $Root 'cli-modify-backups')
    $ClaudeStatePath2 = Join-Path $FakeBin 'claude.state'
    Set-Content -LiteralPath $ClaudeStatePath2 -Value 'mcp add --scope user morpheus -- user-custom-morpheus.exe mcp --stdio' -Encoding ascii
    & $Manager -InstallRoot $InstallRoot -Action Uninstall `
        -DataRoot $DataRoot -ConfigRoot $ConfigRoot `
        -StatePath (Join-Path $Root 'cli-modify-state.json') -LogPath (Join-Path $Root 'cli-modify.log') `
        -BackupRoot (Join-Path $Root 'cli-modify-backups')
    $CliModifyState = Read-Json -Path (Join-Path $Root 'cli-modify-state.json')
    Assert-True (@($CliModifyState.clients).Count -eq 1) 'Manually modified Claude Code CLI entry was not preserved.'
    Assert-True (Test-Path -LiteralPath $ClaudeStatePath2) 'Manually modified Claude Code CLI state file was removed.'
    Remove-Item -LiteralPath $ClaudeStatePath2 -Force -ErrorAction SilentlyContinue

    $Log = Get-Content -Raw -LiteralPath $LogPath
    Assert-True ($Log.Contains('INSTALL client=copilot-jetbrains')) 'Install audit line is missing.'
    Assert-True ($Log.Contains('PRESERVE client=claude-desktop')) 'Preservation audit line is missing.'
    Assert-True ($Log.Contains('END action=Uninstall remaining=0')) 'Final uninstall audit line is missing.'

    Write-Host 'M28 MCP client integration verification: PASS' -ForegroundColor Green
    Write-Host 'clients=5 JSON-merge=PASS CLI-registration=PASS idempotency=PASS foreign-preservation=PASS uninstall=PASS invalid-json=PASS'
}
finally {
    $env:Path = $OldPath
    Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------------------------------------------------
# Preflight (-Action Detect) scenarios. Detect shares its detection functions with Install/Uninstall by
# construction (same script, same functions), so these scenarios pin the five-state classification
# (NotDetected / Available / AlreadyManaged / NeedsRepair / Conflict) the installer wizard reads via the
# INI report, each in its own sandboxed environment.
# ---------------------------------------------------------------------------------------------------------
$Root2 = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-m28-mcp-preflight-" + [Guid]::NewGuid())
$BasePath = $env:Path
try {
    # Scenario: no client installed at all.
    $S1Root = Join-Path $Root2 'scenario-01'
    New-Item -ItemType Directory -Force -Path $S1Root | Out-Null
    $S1Install = Join-Path $S1Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S1Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S1Install 'morpheus.exe') | Out-Null
    $S1EmptyBin = Join-Path $S1Root 'empty-bin'
    New-Item -ItemType Directory -Force -Path $S1EmptyBin | Out-Null
    $S1Report = Invoke-Detect -DetectInstallRoot $S1Install `
        -DetectDataRoot (Join-Path $S1Root 'data') -DetectConfigRoot (Join-Path $S1Root 'config') `
        -DetectStatePath (Join-Path $S1Root 'state.json') -DetectOut (Join-Path $S1Root 'report.ini') `
        -DetectCopilotJetBrainsConfig (Join-Path $S1Root 'no-jetbrains\mcp.json') `
        -DetectClaudeDesktopConfig (Join-Path $S1Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath $S1EmptyBin
    foreach ($Section in @('copilot-jetbrains', 'claude-desktop', 'copilot-cli', 'claude-code', 'codex')) {
        Assert-ClientState $S1Report $Section 'NotDetected' 'Scenario1-NoClients'
    }

    # Scenarios: Claude Desktop / Claude Code / Copilot CLI / Codex all detected but unconfigured, and a JSON
    # client (JetBrains) whose file already lists several foreign (non-MORPHEUS) MCP servers -- Detect must
    # report "Available" for it without ever touching the file.
    $S2Root = Join-Path $Root2 'scenario-02-03-04-06-11'
    New-Item -ItemType Directory -Force -Path $S2Root | Out-Null
    $S2Install = Join-Path $S2Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S2Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S2Install 'morpheus.exe') | Out-Null
    $S2Bin = Join-Path $S2Root 'bin'
    New-Item -ItemType Directory -Force -Path $S2Bin | Out-Null
    New-FakeMcpCli -Directory $S2Bin -Name 'copilot' | Out-Null
    New-FakeMcpCli -Directory $S2Bin -Name 'claude' | Out-Null
    New-FakeMcpCli -Directory $S2Bin -Name 'codex' | Out-Null
    $S2JetBrainsConfig = Join-Path $S2Root 'jetbrains\mcp.json'
    Write-Utf8Json -Path $S2JetBrainsConfig -Value ([pscustomobject][ordered]@{
        servers = [pscustomobject][ordered]@{
            memory = [pscustomobject][ordered]@{ command = 'npx'; args = @('memory-server') }
            other1 = [pscustomobject][ordered]@{ command = 'npx'; args = @('other1-server') }
            other2 = [pscustomobject][ordered]@{ command = 'npx'; args = @('other2-server') }
        }
    })
    $S2ClaudeDesktopConfig = Join-Path $S2Root 'claude-desktop\claude_desktop_config.json'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $S2ClaudeDesktopConfig) | Out-Null
    $S2Before = [System.IO.File]::ReadAllText($S2JetBrainsConfig, [System.Text.Encoding]::UTF8)
    $S2Report = Invoke-Detect -DetectInstallRoot $S2Install `
        -DetectDataRoot (Join-Path $S2Root 'data') -DetectConfigRoot (Join-Path $S2Root 'config') `
        -DetectStatePath (Join-Path $S2Root 'state.json') -DetectOut (Join-Path $S2Root 'report.ini') `
        -DetectCopilotJetBrainsConfig $S2JetBrainsConfig -DetectClaudeDesktopConfig $S2ClaudeDesktopConfig `
        -DetectPath "$S2Bin;$BasePath"
    foreach ($Section in @('copilot-jetbrains', 'claude-desktop', 'copilot-cli', 'claude-code', 'codex')) {
        Assert-ClientState $S2Report $Section 'Available' 'Scenario2-3-4-6-11-Unconfigured'
    }
    Assert-True ([System.IO.File]::ReadAllText($S2JetBrainsConfig, [System.Text.Encoding]::UTF8) -eq $S2Before) 'Detect must never modify a client configuration file.'

    # Scenario: a fake VS Code Copilot Chat shim sits earlier on PATH than the real Copilot CLI. MORPHEUS must
    # reject it by its path shape (Test-VsCodeShimPath) and keep looking rather than stopping at the first
    # PATH match.
    $S5Root = Join-Path $Root2 'scenario-05'
    New-Item -ItemType Directory -Force -Path $S5Root | Out-Null
    $S5Install = Join-Path $S5Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S5Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S5Install 'morpheus.exe') | Out-Null
    $S5DataRoot = Join-Path $S5Root 'data'
    $S5ConfigRoot = Join-Path $S5Root 'config'
    $S5RealBin = Join-Path $S5Root 'real-bin'
    New-Item -ItemType Directory -Force -Path $S5RealBin | Out-Null
    New-FakeMcpCli -Directory $S5RealBin -Name 'copilot' | Out-Null
    $S5ShimBin = Join-Path $S5Root 'Microsoft VS Code\bin'
    New-Item -ItemType Directory -Force -Path $S5ShimBin | Out-Null
    New-FakeMcpCli -Directory $S5ShimBin -Name 'copilot' | Out-Null
    $S5Exe = Join-Path $S5Install 'morpheus.exe'
    $S5StatePath = Join-Path $S5Root 'state.json'
    Write-Utf8Json -Path $S5StatePath -Value ([pscustomobject][ordered]@{
        formatVersion = 1
        updatedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        installRoot = $S5Install
        dataRoot = $S5DataRoot
        configRoot = $S5ConfigRoot
        clients = @([pscustomobject][ordered]@{
            id = 'copilot-cli'; displayName = 'GitHub Copilot CLI'; kind = 'cli'; ownership = 'managed'
            toolName = 'copilot'; toolPath = (Join-Path $S5RealBin 'copilot.cmd')
            command = $S5Exe; dataRoot = $S5DataRoot; configRoot = $S5ConfigRoot
            getArguments = @('mcp', 'get', 'morpheus', '--json'); removeArguments = @('mcp', 'remove', 'morpheus')
            configuredAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        })
    })
    Set-Content -LiteralPath (Join-Path $S5RealBin 'copilot.state') `
        -Value "mcp add morpheus --env MORPHEUS_DATA_DIR=$S5DataRoot --env MORPHEUS_CONFIG_DIR=$S5ConfigRoot -- $S5Exe mcp --stdio" `
        -Encoding ascii
    # If the shim were wrongly selected instead of the real CLI, 'mcp get' against it would find no state file
    # and report "Available" rather than "AlreadyManaged" -- so this assertion only passes when the shim was
    # correctly skipped.
    $S5Report = Invoke-Detect -DetectInstallRoot $S5Install `
        -DetectDataRoot $S5DataRoot -DetectConfigRoot $S5ConfigRoot `
        -DetectStatePath $S5StatePath -DetectOut (Join-Path $S5Root 'report.ini') `
        -DetectCopilotJetBrainsConfig (Join-Path $S5Root 'no-jetbrains\mcp.json') `
        -DetectClaudeDesktopConfig (Join-Path $S5Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath "$S5ShimBin;$S5RealBin;$BasePath"
    Assert-ClientState $S5Report 'copilot-cli' 'AlreadyManaged' 'Scenario5-ShimRejection'

    # Scenario: a managed client whose install/data/config roots have moved since it was configured -- the
    # tracked entry is exactly what MORPHEUS wrote, it is simply stale ("needs repair", not a conflict) --
    # and re-selecting it must actually repair it, not just report the stale state.
    $S8Root = Join-Path $Root2 'scenario-08-14'
    New-Item -ItemType Directory -Force -Path $S8Root | Out-Null
    $S8OldInstall = Join-Path $S8Root 'MORPHEUS-old'
    $S8NewInstall = Join-Path $S8Root 'MORPHEUS-new'
    New-Item -ItemType Directory -Force -Path $S8OldInstall, $S8NewInstall | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S8OldInstall 'morpheus.exe') | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S8NewInstall 'morpheus.exe') | Out-Null
    $S8OldData = Join-Path $S8Root 'data-old'
    $S8OldConfig = Join-Path $S8Root 'config-old'
    $S8NewData = Join-Path $S8Root 'data-new'
    $S8NewConfig = Join-Path $S8Root 'config-new'
    $S8StatePath = Join-Path $S8Root 'state.json'
    $S8JetBrainsConfig = Join-Path $S8Root 'jetbrains\mcp.json'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $S8JetBrainsConfig) | Out-Null

    & $Manager -InstallRoot $S8OldInstall -CopilotJetBrains -Strict `
        -DataRoot $S8OldData -ConfigRoot $S8OldConfig `
        -StatePath $S8StatePath -LogPath (Join-Path $S8Root 'install.log') -BackupRoot (Join-Path $S8Root 'backups') `
        -CopilotJetBrainsConfigPath $S8JetBrainsConfig

    $S8Report = Invoke-Detect -DetectInstallRoot $S8NewInstall `
        -DetectDataRoot $S8NewData -DetectConfigRoot $S8NewConfig `
        -DetectStatePath $S8StatePath -DetectOut (Join-Path $S8Root 'report.ini') `
        -DetectCopilotJetBrainsConfig $S8JetBrainsConfig `
        -DetectClaudeDesktopConfig (Join-Path $S8Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath $BasePath
    Assert-ClientState $S8Report 'copilot-jetbrains' 'NeedsRepair' 'Scenario8-StaleRepair'

    & $Manager -InstallRoot $S8NewInstall -CopilotJetBrains -Strict `
        -DataRoot $S8NewData -ConfigRoot $S8NewConfig `
        -StatePath $S8StatePath -LogPath (Join-Path $S8Root 'install.log') -BackupRoot (Join-Path $S8Root 'backups') `
        -CopilotJetBrainsConfigPath $S8JetBrainsConfig
    $S8Repaired = Read-Json -Path $S8JetBrainsConfig
    Assert-True ($S8Repaired.servers.morpheus.command -eq (Join-Path $S8NewInstall 'morpheus.exe')) 'Repair reinstall did not update the MORPHEUS command path.'
    Assert-True ($S8Repaired.servers.morpheus.env.MORPHEUS_DATA_DIR -eq $S8NewData) 'Repair reinstall did not update MORPHEUS_DATA_DIR.'

    $S8ReportAfterRepair = Invoke-Detect -DetectInstallRoot $S8NewInstall `
        -DetectDataRoot $S8NewData -DetectConfigRoot $S8NewConfig `
        -DetectStatePath $S8StatePath -DetectOut (Join-Path $S8Root 'report-after.ini') `
        -DetectCopilotJetBrainsConfig $S8JetBrainsConfig `
        -DetectClaudeDesktopConfig (Join-Path $S8Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath $BasePath
    Assert-ClientState $S8ReportAfterRepair 'copilot-jetbrains' 'AlreadyManaged' 'Scenario14-RepairApplied'

    # Scenario: a foreign entry literally named 'morpheus' that MORPHEUS never created, for both a JSON client
    # and a CLI client, with no managed state at all -- must classify as Conflict, never overwritten.
    $S9Root = Join-Path $Root2 'scenario-09'
    New-Item -ItemType Directory -Force -Path $S9Root | Out-Null
    $S9Install = Join-Path $S9Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S9Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S9Install 'morpheus.exe') | Out-Null
    $S9Bin = Join-Path $S9Root 'bin'
    New-Item -ItemType Directory -Force -Path $S9Bin | Out-Null
    New-FakeMcpCli -Directory $S9Bin -Name 'copilot' | Out-Null
    Set-Content -LiteralPath (Join-Path $S9Bin 'copilot.state') -Value 'mcp add morpheus -- foreign-tool.exe serve' -Encoding ascii
    $S9JetBrainsConfig = Join-Path $S9Root 'jetbrains\mcp.json'
    Write-Utf8Json -Path $S9JetBrainsConfig -Value ([pscustomobject][ordered]@{
        servers = [pscustomobject][ordered]@{
            morpheus = [pscustomobject][ordered]@{ command = 'foreign.exe'; args = @('serve') }
        }
    })
    $S9Report = Invoke-Detect -DetectInstallRoot $S9Install `
        -DetectDataRoot (Join-Path $S9Root 'data') -DetectConfigRoot (Join-Path $S9Root 'config') `
        -DetectStatePath (Join-Path $S9Root 'state.json') -DetectOut (Join-Path $S9Root 'report.ini') `
        -DetectCopilotJetBrainsConfig $S9JetBrainsConfig `
        -DetectClaudeDesktopConfig (Join-Path $S9Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath "$S9Bin;$BasePath"
    Assert-ClientState $S9Report 'copilot-jetbrains' 'Conflict' 'Scenario9-ForeignJson'
    Assert-ClientState $S9Report 'copilot-cli' 'Conflict' 'Scenario9-ForeignCli'

    # Scenario: an invalid (unparseable) JSON configuration file must classify as Conflict with an explanatory
    # reason, never crash Detect, and never be modified.
    $S10Root = Join-Path $Root2 'scenario-10'
    New-Item -ItemType Directory -Force -Path $S10Root | Out-Null
    $S10Install = Join-Path $S10Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S10Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S10Install 'morpheus.exe') | Out-Null
    $S10JetBrainsConfig = Join-Path $S10Root 'jetbrains\mcp.json'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $S10JetBrainsConfig) | Out-Null
    [System.IO.File]::WriteAllText($S10JetBrainsConfig, '{ invalid json', [System.Text.UTF8Encoding]::new($false))
    $S10Before = [System.IO.File]::ReadAllText($S10JetBrainsConfig, [System.Text.Encoding]::UTF8)
    $S10Report = Invoke-Detect -DetectInstallRoot $S10Install `
        -DetectDataRoot (Join-Path $S10Root 'data') -DetectConfigRoot (Join-Path $S10Root 'config') `
        -DetectStatePath (Join-Path $S10Root 'state.json') -DetectOut (Join-Path $S10Root 'report.ini') `
        -DetectCopilotJetBrainsConfig $S10JetBrainsConfig `
        -DetectClaudeDesktopConfig (Join-Path $S10Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath $BasePath
    Assert-ClientState $S10Report 'copilot-jetbrains' 'Conflict' 'Scenario10-InvalidJson'
    Assert-True ($S10Report['copilot-jetbrains']['Reason'] -match 'invalid JSON') 'Invalid JSON reason text is missing.'
    Assert-True ([System.IO.File]::ReadAllText($S10JetBrainsConfig, [System.Text.Encoding]::UTF8) -eq $S10Before) 'Detect must never modify an invalid configuration file.'

    # Scenario: a CLI whose capability probe answers quickly but whose 'mcp get' call hangs -- Detect must
    # bound the wait (never hang indefinitely) and must not crash; a timed-out probe is treated the same as
    # "no entry yet" (Available), not a false AlreadyManaged/Conflict.
    $S12Root = Join-Path $Root2 'scenario-12'
    New-Item -ItemType Directory -Force -Path $S12Root | Out-Null
    $S12Install = Join-Path $S12Root 'MORPHEUS'
    New-Item -ItemType Directory -Force -Path $S12Install | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $S12Install 'morpheus.exe') | Out-Null
    $S12Bin = Join-Path $S12Root 'bin'
    New-Item -ItemType Directory -Force -Path $S12Bin | Out-Null
    $S12ClaudePath = Join-Path $S12Bin 'claude.cmd'
    @'
@echo off
if /I "%~1"=="--version" (
  echo claude version 0.0.0-test
  exit /b 0
)
if /I "%~1"=="mcp" if /I "%~2"=="get" (
  ping -n 6 127.0.0.1 >nul
  exit /b 0
)
exit /b 2
'@ | Set-Content -LiteralPath $S12ClaudePath -Encoding ascii
    $S12Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $S12Report = Invoke-Detect -DetectInstallRoot $S12Install `
        -DetectDataRoot (Join-Path $S12Root 'data') -DetectConfigRoot (Join-Path $S12Root 'config') `
        -DetectStatePath (Join-Path $S12Root 'state.json') -DetectOut (Join-Path $S12Root 'report.ini') `
        -DetectCopilotJetBrainsConfig (Join-Path $S12Root 'no-jetbrains\mcp.json') `
        -DetectClaudeDesktopConfig (Join-Path $S12Root 'no-claude-desktop\claude_desktop_config.json') `
        -DetectPath "$S12Bin;$BasePath" -DetectTimeoutSeconds 2
    $S12Stopwatch.Stop()
    Assert-ClientState $S12Report 'claude-code' 'Available' 'Scenario12-CliProbeTimeout'
    Assert-True ($S12Stopwatch.Elapsed.TotalSeconds -lt 30) 'CLI probe timeout did not bound total Detect duration; the child process tree may not have been killed.'

    Write-Host 'M28 MCP client preflight verification: PASS' -ForegroundColor Green
    Write-Host 'scenarios=15 states=NotDetected,Available,AlreadyManaged,NeedsRepair,Conflict shim-rejection=PASS stale-repair=PASS timeout-bound=PASS'
}
finally {
    $env:Path = $BasePath
    Remove-Item -LiteralPath $Root2 -Recurse -Force -ErrorAction SilentlyContinue
}
