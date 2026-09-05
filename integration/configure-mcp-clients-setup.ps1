[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallRoot,

    [switch] $CopilotJetBrains,
    [switch] $CopilotCli,
    [switch] $ClaudeCode,
    [switch] $ClaudeDesktop,
    [switch] $Codex,

    [string] $DataRoot = '',
    [string] $ConfigRoot = '',
    [string] $StatePath = '',
    [string] $LogPath = '',
    [string] $BackupRoot = '',
    [string] $ClaudeDesktopConfigPath = '',
    [string] $CopilotJetBrainsConfigPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Manager = Join-Path $PSScriptRoot 'configure-mcp-clients.ps1'
if (-not (Test-Path -LiteralPath $Manager -PathType Leaf)) {
    throw "MORPHEUS MCP client integration manager not found: $Manager"
}

$Parameters = @{ InstallRoot = $InstallRoot }
if (-not [string]::IsNullOrWhiteSpace($DataRoot)) { $Parameters['DataRoot'] = $DataRoot }
if (-not [string]::IsNullOrWhiteSpace($ConfigRoot)) { $Parameters['ConfigRoot'] = $ConfigRoot }
if (-not [string]::IsNullOrWhiteSpace($StatePath)) { $Parameters['StatePath'] = $StatePath }
if (-not [string]::IsNullOrWhiteSpace($LogPath)) { $Parameters['LogPath'] = $LogPath }
if (-not [string]::IsNullOrWhiteSpace($BackupRoot)) { $Parameters['BackupRoot'] = $BackupRoot }
if (-not [string]::IsNullOrWhiteSpace($ClaudeDesktopConfigPath)) { $Parameters['ClaudeDesktopConfigPath'] = $ClaudeDesktopConfigPath }
if (-not [string]::IsNullOrWhiteSpace($CopilotJetBrainsConfigPath)) { $Parameters['CopilotJetBrainsConfigPath'] = $CopilotJetBrainsConfigPath }
if ($CopilotJetBrains) { $Parameters['CopilotJetBrains'] = $true }
if ($CopilotCli) { $Parameters['CopilotCli'] = $true }
if ($ClaudeCode) { $Parameters['ClaudeCode'] = $true }
if ($ClaudeDesktop) { $Parameters['ClaudeDesktop'] = $true }
if ($Codex) { $Parameters['Codex'] = $true }

& $Manager @Parameters

$Selected = @()
if ($CopilotJetBrains) { $Selected += 'copilot-jetbrains' }
if ($CopilotCli) { $Selected += 'copilot-cli' }
if ($ClaudeCode) { $Selected += 'claude-code' }
if ($ClaudeDesktop) { $Selected += 'claude-desktop' }
if ($Codex) { $Selected += 'codex' }
if ($Selected.Count -eq 0) { return }

$VerificationStatePath = $StatePath
if ([string]::IsNullOrWhiteSpace($VerificationStatePath)) {
    $LocalAppData = [Environment]::GetFolderPath('LocalApplicationData')
    $VerificationStatePath = Join-Path $LocalAppData 'MORPHEUS\mcp-client-integrations.json'
}
$ManagedIds = @()
if (Test-Path -LiteralPath $VerificationStatePath -PathType Leaf) {
    try {
        $State = Get-Content -Raw -LiteralPath $VerificationStatePath | ConvertFrom-Json
        $ManagedIds = @($State.clients | ForEach-Object { [string]$_.id })
    }
    catch {
        throw "MORPHEUS MCP integration state could not be verified: $($_.Exception.Message)"
    }
}

$Missing = @($Selected | Where-Object { $ManagedIds -notcontains $_ })
if ($Missing.Count -gt 0) {
    throw "One or more selected MORPHEUS MCP client integrations were not configured: $($Missing -join ', '). See %LOCALAPPDATA%\MORPHEUS\mcp-clients.log."
}

Write-Host "MORPHEUS setup MCP client selection SUCCESS: $($Selected -join ', ')" -ForegroundColor Green
