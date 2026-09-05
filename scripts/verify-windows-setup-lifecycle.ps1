[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SetupExePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'MORPHEUS Windows setup lifecycle verification must run on Windows.'
}
if (-not (Test-Path -LiteralPath $SetupExePath -PathType Leaf)) {
    throw "Setup executable not found: $SetupExePath"
}
$SetupExePath = (Resolve-Path -LiteralPath $SetupExePath).Path

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

# [Environment]::GetFolderPath (and Inno's own {localappdata}/{userappdata}/{group} constants) resolve
# against the real user profile via the Windows Known Folder API -- setting $env:LOCALAPPDATA/$env:APPDATA
# in this process has NO effect on them (verified empirically). A real Setup.exe run therefore can never be
# fully sandboxed via environment variables alone: this script keeps every MCP client checkbox unselected
# during the real install (/MORPHEUSMCPCLIENTS= empty, see MORPHEUS.iss's silent-install fallback) and skips
# Start Menu creation (/NOICONS, requires AllowNoIcons=yes in [Setup]) so the only durable, real-machine
# side effect of running the real installer is the transient per-user uninstall registry key, which the
# real uninstaller removes at the end of this script (verified). The MCP client integration manager itself
# (the code Setup.exe would call for a selected client) is instead exercised here from its actual installed
# location with fully injected sandbox paths -- proving the packaged artifact works without ever touching a
# real Claude/Copilot/Codex configuration.
function Invoke-SilentSetup([string] $InstallDirValue, [string] $LogPath) {
    # Start-Process -ArgumentList does not quote array elements containing spaces on the caller's behalf: an
    # unquoted /DIR= or /LOG= value with a space gets split into bogus extra arguments by Windows' own argv
    # parser (reproduced and confirmed against the real Setup.exe before this fix). Quote every value that can
    # contain spaces ourselves.
    $InstallArgs = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER', '/NOICONS',
        '/TASKS=', '/MORPHEUSMCPCLIENTS=', ('/DIR="' + $InstallDirValue + '"'), ('/LOG="' + $LogPath + '"'))
    $Process = Start-Process -FilePath $SetupExePath -ArgumentList $InstallArgs -Wait -PassThru -NoNewWindow
    return $Process.ExitCode
}

function Stop-ProcessTreeSafely([System.Diagnostics.Process] $Process) {
    if ($null -eq $Process) { return }
    try { if ($Process.HasExited) { return } } catch { return }
    try {
        $TaskKill = Join-Path ([Environment]::SystemDirectory) 'taskkill.exe'
        & $TaskKill '/PID' ([string]$Process.Id) '/T' '/F' 2>&1 | Out-Null
    }
    catch { }
    try { if (-not $Process.HasExited) { $Process.Kill() } } catch { }
}

$Root = Join-Path ([System.IO.Path]::GetTempPath()) ("morpheus-setup-lifecycle-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $Root | Out-Null
$InstallDir = Join-Path $Root 'MORPHEUS install with spaces'
$RegistryKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}_is1'
$StartMenuFolder = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\MORPHEUS'

try {
    $StartMenuExistedBefore = Test-Path -LiteralPath $StartMenuFolder

    # --- Fresh install --------------------------------------------------------------------------------
    $Log1 = Join-Path $Root 'install.log'
    $ExitCode = Invoke-SilentSetup -InstallDirValue $InstallDir -LogPath $Log1
    Assert-True ($ExitCode -eq 0) "Fresh install failed with exit code $ExitCode. See $Log1"

    $MorpheusExe = Join-Path $InstallDir 'morpheus.exe'
    $Marker = Join-Path $InstallDir '.morpheus-install.json'
    Assert-True (Test-Path -LiteralPath $MorpheusExe -PathType Leaf) 'Fresh install did not place morpheus.exe.'
    Assert-True (Test-Path -LiteralPath $Marker -PathType Leaf) 'Fresh install did not write the ownership marker.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallDir 'integration\configure-mcp-clients.ps1') -PathType Leaf) 'Fresh install did not place the MCP client integration manager.'
    Assert-True (Test-Path -LiteralPath (Join-Path $InstallDir 'unins000.exe') -PathType Leaf) 'Fresh install did not write the uninstaller.'
    Assert-True (Test-Path -LiteralPath $RegistryKey) 'Fresh install did not register the per-user uninstall key.'
    Assert-True ((Test-Path -LiteralPath $StartMenuFolder) -eq $StartMenuExistedBefore) 'A real Start Menu folder was created despite /NOICONS.'

    # --- morpheus.exe --version works from the sandboxed install ---------------------------------------
    $VersionOutput = & $MorpheusExe '--version' 2>&1
    Assert-True ($LASTEXITCODE -eq 0) "morpheus.exe --version failed with exit code $LASTEXITCODE. Output: $VersionOutput"
    Write-Host "morpheus.exe --version: $VersionOutput"

    # --- MCP STDIO server starts and can be stopped cleanly --------------------------------------------
    # A real MCP client keeps stdin open for the life of the session; Start-Process's file-based stdin
    # redirection (including a real empty file) signals immediate EOF, which correctly makes the server shut
    # down right away -- that looked like a startup failure but was actually a realistic-input problem. Using
    # System.Diagnostics.Process directly with RedirectStandardInput keeps an open pipe instead, matching how
    # a real client connects.
    $StdioStartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $StdioStartInfo.FileName = $MorpheusExe
    $StdioStartInfo.Arguments = 'mcp --stdio'
    $StdioStartInfo.UseShellExecute = $false
    $StdioStartInfo.CreateNoWindow = $true
    $StdioStartInfo.RedirectStandardInput = $true
    $StdioStartInfo.RedirectStandardOutput = $true
    $StdioStartInfo.RedirectStandardError = $true
    $StdioProcess = [System.Diagnostics.Process]::Start($StdioStartInfo)
    try {
        Start-Sleep -Seconds 3
        Assert-True (-not $StdioProcess.HasExited) 'morpheus.exe mcp --stdio exited immediately instead of staying up as a server.'
    }
    finally {
        Stop-ProcessTreeSafely -Process $StdioProcess
    }

    # --- Idempotent reinstall ---------------------------------------------------------------------------
    $HashBefore = (Get-FileHash -LiteralPath $MorpheusExe -Algorithm SHA256).Hash
    $Log2 = Join-Path $Root 'reinstall.log'
    $ExitCode = Invoke-SilentSetup -InstallDirValue $InstallDir -LogPath $Log2
    Assert-True ($ExitCode -eq 0) "Idempotent reinstall failed with exit code $ExitCode. See $Log2"
    $HashAfter = (Get-FileHash -LiteralPath $MorpheusExe -Algorithm SHA256).Hash
    Assert-True ($HashBefore -eq $HashAfter) 'Idempotent reinstall changed morpheus.exe.'

    # --- Re-activation removes a file no longer part of the payload (real Setup.exe, not just the engine
    # in isolation) -----------------------------------------------------------------------------------
    $StaleFile = Join-Path $InstallDir 'stale-obsolete-file.txt'
    Set-Content -LiteralPath $StaleFile -Value 'left over from a previous version' -Encoding ascii
    $Log3 = Join-Path $Root 'reactivate.log'
    $ExitCode = Invoke-SilentSetup -InstallDirValue $InstallDir -LogPath $Log3
    Assert-True ($ExitCode -eq 0) "Re-activation failed with exit code $ExitCode. See $Log3"
    Assert-True (-not (Test-Path -LiteralPath $StaleFile)) 'A file absent from the current payload survived re-activation through the real Setup.exe.'
    Assert-True (Test-Path -LiteralPath $MorpheusExe -PathType Leaf) 'morpheus.exe did not survive re-activation.'

    # --- The packaged (installed) MCP client integration manager actually works, using fully injected
    # sandbox paths so this never touches a real Claude/Copilot/Codex configuration or the real
    # %LOCALAPPDATA%\MORPHEUS state. ---------------------------------------------------------------------
    $InstalledManager = Join-Path $InstallDir 'integration\configure-mcp-clients.ps1'
    $SandboxClaudeDesktopConfig = Join-Path $Root 'sandbox-claude-desktop\claude_desktop_config.json'
    $SandboxDataRoot = Join-Path $Root 'sandbox-data'
    $SandboxConfigRoot = Join-Path $Root 'sandbox-config'
    $SandboxStatePath = Join-Path $Root 'sandbox-state\mcp-client-integrations.json'
    $SandboxLogPath = Join-Path $Root 'sandbox-logs\mcp-clients.log'
    $SandboxBackupRoot = Join-Path $Root 'sandbox-backups'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $SandboxClaudeDesktopConfig) | Out-Null

    & $InstalledManager -InstallRoot $InstallDir -ClaudeDesktop -Strict `
        -DataRoot $SandboxDataRoot -ConfigRoot $SandboxConfigRoot `
        -StatePath $SandboxStatePath -LogPath $SandboxLogPath -BackupRoot $SandboxBackupRoot `
        -ClaudeDesktopConfigPath $SandboxClaudeDesktopConfig
    $SandboxConfig = Get-Content -Raw -LiteralPath $SandboxClaudeDesktopConfig | ConvertFrom-Json
    Assert-True ($SandboxConfig.mcpServers.morpheus.command -eq $MorpheusExe) 'Installed integration manager did not create a working client integration.'

    & $InstalledManager -InstallRoot $InstallDir -Action Uninstall `
        -DataRoot $SandboxDataRoot -ConfigRoot $SandboxConfigRoot `
        -StatePath $SandboxStatePath -LogPath $SandboxLogPath -BackupRoot $SandboxBackupRoot `
        -ClaudeDesktopConfigPath $SandboxClaudeDesktopConfig
    $SandboxConfigAfter = Get-Content -Raw -LiteralPath $SandboxClaudeDesktopConfig | ConvertFrom-Json
    Assert-True ($null -eq $SandboxConfigAfter.mcpServers.PSObject.Properties['morpheus']) 'Installed integration manager did not remove the sandboxed client integration on uninstall.'

    # --- Full uninstall: only MORPHEUS's own program directory is removed ------------------------------
    $Uninstaller = Join-Path $InstallDir 'unins000.exe'
    $UninstallProcess = Start-Process -FilePath $Uninstaller -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART') -Wait -PassThru -NoNewWindow
    Assert-True ($UninstallProcess.ExitCode -eq 0) "Uninstall failed with exit code $($UninstallProcess.ExitCode)."
    Start-Sleep -Seconds 1
    Assert-True (-not (Test-Path -LiteralPath $InstallDir)) 'Program directory was not fully removed by uninstall.'
    Assert-True (-not (Test-Path -LiteralPath $RegistryKey)) 'Per-user uninstall registry key was not removed.'

    Write-Host 'MORPHEUS Windows setup lifecycle verification: PASS' -ForegroundColor Green
    Write-Host 'fresh-install=PASS version=PASS mcp-stdio=PASS idempotent-reinstall=PASS obsolete-file-removal=PASS packaged-manager=PASS uninstall=PASS'
}
finally {
    Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
}
