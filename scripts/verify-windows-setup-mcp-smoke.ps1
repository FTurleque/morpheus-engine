[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SmokeSetupExePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'MORPHEUS Windows setup MCP smoke verification must run on Windows.'
}
if (-not (Test-Path -LiteralPath $SmokeSetupExePath -PathType Leaf)) {
    throw "Smoke setup executable not found: $SmokeSetupExePath"
}
$SmokeSetupExePath = (Resolve-Path -LiteralPath $SmokeSetupExePath).Path

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Utf8Json([string] $Path, [object] $Value) {
    $Parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $Parent | Out-Null
    $Json = $Value | ConvertTo-Json -Depth 32
    [System.IO.File]::WriteAllText($Path, $Json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Read-Json([string] $Path) {
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

# This is the exact code path a real user drives interactively (wizard -> detect -> select ->
# CurStepChanged -> ConfigureNativeMcpClients -> configure-mcp-clients-setup.ps1 -> the manager); the only
# thing this script does differently is provide the selection via /MORPHEUSMCPCLIENTS instead of a mouse
# click, because a silent install never shows wizard pages. MORPHEUS_SMOKE_* env vars are what let a smoke
# build point that same code path at sandboxed files instead of the real Claude Desktop configuration.
function Invoke-SmokeSetup([string] $InstallDirValue, [string] $LogPath, [string] $Clients) {
    $InstallArgs = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER', '/NOICONS',
        '/TASKS=', ('/MORPHEUSMCPCLIENTS=' + $Clients),
        ('/DIR="' + $InstallDirValue + '"'), ('/LOG="' + $LogPath + '"'))
    $Process = Start-Process -FilePath $SmokeSetupExePath -ArgumentList $InstallArgs -Wait -PassThru -NoNewWindow
    return $Process.ExitCode
}

# The smoke build's AppId (MORPHEUS.iss, #ifdef SmokeMode) is deliberately distinct and stable so this test's
# HKCU uninstall key can never be confused with -- or collide with -- the real production installation's.
$SmokeRegistryKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{6BF23F0E-6C9B-4B5A-9E51-8B6D1F0C7E42}_is1'

$Root = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-setup-mcp-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $Root | Out-Null
$InstallDir = Join-Path $Root 'MORPHEUS smoke install'
$ClaudeDesktopConfig = Join-Path $Root 'claude-desktop\claude_desktop_config.json'
$DataRoot = Join-Path $Root 'data'
$ConfigRoot = Join-Path $Root 'config'
$StatePath = Join-Path $Root 'state\mcp-client-integrations.json'
$LogPathRoot = Join-Path $Root 'logs\mcp-clients.log'
$BackupRoot = Join-Path $Root 'backups'
$RealClaudeDesktopConfig = Join-Path $env:APPDATA 'Claude\claude_desktop_config.json'

$SmokeEnvNames = @(
    'MORPHEUS_SMOKE_MODE', 'MORPHEUS_SMOKE_DATA_ROOT', 'MORPHEUS_SMOKE_CONFIG_ROOT',
    'MORPHEUS_SMOKE_STATE_PATH', 'MORPHEUS_SMOKE_LOG_PATH', 'MORPHEUS_SMOKE_BACKUP_ROOT',
    'MORPHEUS_SMOKE_CLAUDE_DESKTOP_CONFIG_PATH'
)
$SavedEnv = @{}
foreach ($Name in $SmokeEnvNames) { $SavedEnv[$Name] = [Environment]::GetEnvironmentVariable($Name) }

try {
    $RealConfigExistedBefore = Test-Path -LiteralPath $RealClaudeDesktopConfig
    $RealConfigHashBefore = if ($RealConfigExistedBefore) { (Get-FileHash -LiteralPath $RealClaudeDesktopConfig -Algorithm SHA256).Hash } else { $null }

    # A "detectable" Claude Desktop sandbox: a foreign MCP server and an unrelated root property, so this
    # test proves both that a fresh sandbox is classified Available (no 'morpheus' key yet) and that
    # everything unrelated survives the real Setup.exe's write.
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value ([pscustomobject][ordered]@{
        mcpServers = [pscustomobject][ordered]@{
            filesystem = [pscustomobject][ordered]@{ command = 'npx'; args = @('fs-server') }
        }
        keepMe = 'smoke-value'
    })

    $env:MORPHEUS_SMOKE_MODE = '1'
    $env:MORPHEUS_SMOKE_DATA_ROOT = $DataRoot
    $env:MORPHEUS_SMOKE_CONFIG_ROOT = $ConfigRoot
    $env:MORPHEUS_SMOKE_STATE_PATH = $StatePath
    $env:MORPHEUS_SMOKE_LOG_PATH = $LogPathRoot
    $env:MORPHEUS_SMOKE_BACKUP_ROOT = $BackupRoot
    $env:MORPHEUS_SMOKE_CLAUDE_DESKTOP_CONFIG_PATH = $ClaudeDesktopConfig

    # --- Real wizard end to end: preflight detects the sandboxed client -> /MORPHEUSMCPCLIENTS selects it
    # (the same ApplyCommandLineClientSelection an interactive checkbox click would use) -> CurStepChanged ->
    # ConfigureNativeMcpClients -> configure-mcp-clients-setup.ps1 -> the manager writes the config. ----------
    $Log1 = Join-Path $Root 'install.log'
    $ExitCode = Invoke-SmokeSetup -InstallDirValue $InstallDir -LogPath $Log1 -Clients 'claude-desktop'
    Assert-True ($ExitCode -eq 0) "Smoke install failed with exit code $ExitCode. See $Log1"
    Assert-True (Test-Path -LiteralPath $SmokeRegistryKey) 'Smoke install did not register its own (smoke) per-user uninstall key.'

    $MorpheusExe = Join-Path $InstallDir 'morpheus.exe'
    Assert-True (Test-Path -LiteralPath $MorpheusExe -PathType Leaf) 'Smoke install did not place morpheus.exe.'

    $ConfigAfterInstall = Read-Json -Path $ClaudeDesktopConfig
    Assert-True ($ConfigAfterInstall.mcpServers.morpheus.command -eq $MorpheusExe) 'Wizard-selected client integration did not write the expected morpheus.exe path.'
    Assert-True (@($ConfigAfterInstall.mcpServers.morpheus.args).Count -eq 2 -and
        [string]$ConfigAfterInstall.mcpServers.morpheus.args[0] -eq 'mcp' -and
        [string]$ConfigAfterInstall.mcpServers.morpheus.args[1] -eq '--stdio') 'Wizard-selected client integration did not write "mcp --stdio" args.'
    Assert-True ($ConfigAfterInstall.mcpServers.morpheus.env.MORPHEUS_DATA_DIR -eq $DataRoot) 'Wizard-selected client integration did not write the sandboxed MORPHEUS_DATA_DIR.'
    Assert-True ($ConfigAfterInstall.mcpServers.morpheus.env.MORPHEUS_CONFIG_DIR -eq $ConfigRoot) 'Wizard-selected client integration did not write the sandboxed MORPHEUS_CONFIG_DIR.'
    Assert-True ($null -ne $ConfigAfterInstall.mcpServers.filesystem) 'Pre-existing foreign MCP server was not preserved by the real Setup.exe.'
    Assert-True ($ConfigAfterInstall.keepMe -eq 'smoke-value') 'Pre-existing unrelated root property was not preserved by the real Setup.exe.'

    # Preflight (RunDetect) and the manager (ConfigureNativeMcpClients) share one BuildSmokeOverrideParameters
    # call site each in MORPHEUS.iss; this sandboxed state file only exists at all if both actually received
    # the identical injected -StatePath, which is the concrete proof they cannot diverge.
    Assert-True (Test-Path -LiteralPath $StatePath -PathType Leaf) 'Sandboxed state file was not created; preflight and the manager may have used different paths.'
    $State = Read-Json -Path $StatePath
    Assert-True (@(@($State.clients) | Where-Object { $_.id -eq 'claude-desktop' }).Count -eq 1) 'Managed state does not list exactly one claude-desktop integration.'

    Assert-True ((Test-Path -LiteralPath $RealClaudeDesktopConfig) -eq $RealConfigExistedBefore) "The real Claude Desktop configuration file's existence changed: smoke mode touched the real machine."
    if ($RealConfigExistedBefore) {
        Assert-True ((Get-FileHash -LiteralPath $RealClaudeDesktopConfig -Algorithm SHA256).Hash -eq $RealConfigHashBefore) 'The real Claude Desktop configuration file was modified: smoke mode touched the real machine.'
    }

    # --- Idempotent reinstall ----------------------------------------------------------------------------
    $BytesBefore = [System.IO.File]::ReadAllText($ClaudeDesktopConfig, [System.Text.Encoding]::UTF8)
    $Log2 = Join-Path $Root 'reinstall.log'
    $ExitCode = Invoke-SmokeSetup -InstallDirValue $InstallDir -LogPath $Log2 -Clients 'claude-desktop'
    Assert-True ($ExitCode -eq 0) "Idempotent smoke reinstall failed with exit code $ExitCode. See $Log2"
    $BytesAfter = [System.IO.File]::ReadAllText($ClaudeDesktopConfig, [System.Text.Encoding]::UTF8)
    Assert-True ($BytesBefore -eq $BytesAfter) 'Idempotent reinstall rewrote the sandboxed configuration.'

    # --- A manually modified managed entry survives the real Setup.exe's own uninstaller -----------------
    $Modified = Read-Json -Path $ClaudeDesktopConfig
    $Modified.mcpServers.morpheus.command = 'user-custom-morpheus.exe'
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value $Modified

    $Uninstaller = Join-Path $InstallDir 'unins000.exe'
    $UninstallLog1 = Join-Path $Root 'uninstall1.log'
    $UninstallProcess = Start-Process -FilePath $Uninstaller -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', ('/LOG="' + $UninstallLog1 + '"')) -Wait -PassThru -NoNewWindow
    Assert-True ($UninstallProcess.ExitCode -eq 0) "Uninstall after manual modification failed with exit code $($UninstallProcess.ExitCode)."
    $ConfigAfterModifiedUninstall = Read-Json -Path $ClaudeDesktopConfig
    Assert-True ($ConfigAfterModifiedUninstall.mcpServers.morpheus.command -eq 'user-custom-morpheus.exe') 'A manually modified managed entry was not preserved by the real Setup.exe uninstaller.'
    Assert-True ($null -ne $ConfigAfterModifiedUninstall.mcpServers.filesystem) 'Foreign entry was lost while uninstalling a modified managed entry.'
    Assert-True (-not (Test-Path -LiteralPath $InstallDir)) 'Program directory was not removed by uninstall.'
    Assert-True (-not (Test-Path -LiteralPath $SmokeRegistryKey)) 'Smoke per-user uninstall key was not removed after uninstall.'

    # --- A genuinely owned, unmodified entry is removed cleanly on uninstall -------------------------------
    # Reset to a fresh sandbox state so this scenario is not entangled with the manually modified entry above.
    Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
    Write-Utf8Json -Path $ClaudeDesktopConfig -Value ([pscustomobject][ordered]@{
        mcpServers = [pscustomobject][ordered]@{
            filesystem = [pscustomobject][ordered]@{ command = 'npx'; args = @('fs-server') }
        }
        keepMe = 'smoke-value'
    })

    $Log3 = Join-Path $Root 'install2.log'
    $ExitCode = Invoke-SmokeSetup -InstallDirValue $InstallDir -LogPath $Log3 -Clients 'claude-desktop'
    Assert-True ($ExitCode -eq 0) "Second smoke install failed with exit code $ExitCode. See $Log3"
    $ConfigAfterSecondInstall = Read-Json -Path $ClaudeDesktopConfig
    Assert-True ($ConfigAfterSecondInstall.mcpServers.morpheus.command -eq $MorpheusExe) 'Second smoke install did not write the expected morpheus.exe path.'

    $UninstallLog2 = Join-Path $Root 'uninstall2.log'
    $UninstallProcess = Start-Process -FilePath $Uninstaller -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', ('/LOG="' + $UninstallLog2 + '"')) -Wait -PassThru -NoNewWindow
    Assert-True ($UninstallProcess.ExitCode -eq 0) "Clean uninstall failed with exit code $($UninstallProcess.ExitCode)."
    $ConfigAfterCleanUninstall = Read-Json -Path $ClaudeDesktopConfig
    Assert-True ($null -eq $ConfigAfterCleanUninstall.mcpServers.PSObject.Properties['morpheus']) 'A genuinely owned, unmodified managed entry was not removed on uninstall.'
    Assert-True ($null -ne $ConfigAfterCleanUninstall.mcpServers.filesystem) 'Foreign entry was lost while removing a managed entry.'
    Assert-True ($ConfigAfterCleanUninstall.keepMe -eq 'smoke-value') 'Unrelated root property was lost while removing a managed entry.'
    Assert-True (-not (Test-Path -LiteralPath $StatePath)) 'Managed state was not cleared after removing the only managed entry.'
    Assert-True (-not (Test-Path -LiteralPath $InstallDir)) 'Program directory was not removed by the clean uninstall.'
    Assert-True (-not (Test-Path -LiteralPath $SmokeRegistryKey)) 'Smoke per-user uninstall key was not removed after the clean uninstall.'

    Assert-True ((Test-Path -LiteralPath $RealClaudeDesktopConfig) -eq $RealConfigExistedBefore) "The real Claude Desktop configuration file's existence changed by the end of the smoke run."
    if ($RealConfigExistedBefore) {
        Assert-True ((Get-FileHash -LiteralPath $RealClaudeDesktopConfig -Algorithm SHA256).Hash -eq $RealConfigHashBefore) 'The real Claude Desktop configuration file was modified by the end of the smoke run.'
    }

    Write-Host 'MORPHEUS Windows setup MCP smoke verification: PASS' -ForegroundColor Green
    Write-Host 'wizard-select=PASS foreign-preservation=PASS state-agreement=PASS idempotent=PASS modified-preserved=PASS clean-removed=PASS real-machine-untouched=PASS'
}
finally {
    # Best-effort cleanup even on failure: never leave a real (smoke-identified) HKCU key or sandbox files
    # behind longer than necessary.
    $LeftoverUninstaller = Join-Path $InstallDir 'unins000.exe'
    if (Test-Path -LiteralPath $LeftoverUninstaller -PathType Leaf) {
        try {
            Start-Process -FilePath $LeftoverUninstaller -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART') -Wait -NoNewWindow -ErrorAction SilentlyContinue | Out-Null
        }
        catch { }
    }
    foreach ($Name in $SmokeEnvNames) {
        [Environment]::SetEnvironmentVariable($Name, $SavedEnv[$Name])
    }
    Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
}
