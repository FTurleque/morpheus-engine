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
