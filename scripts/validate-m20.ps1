[CmdletBinding()]
param(
    [string]$Version = '1.0.0',
    # Builds the release test-signed with an identity created for this run and removed with it. Without it the
    # validator creates no certificate, builds unsigned, and says the signing path was not exercised.
    [switch]$TestSigning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repo
$outputRoot = Join-Path $repo 'validation-output\m20'
$logRoot = Join-Path $outputRoot 'logs'
$installRoot = Join-Path $outputRoot 'installed\MORPHEUS'
# The installed launcher resolves its PROD data/config/logs from LOCALAPPDATA, and MORPHEUS hardens what it
# creates there. Pointing it inside the repository made that hardening inspect the checkout's inherited
# ACLs, so the PROD-path proof depended on the permissions of a development directory.
$validationLocalAppData = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m20-localappdata-' + [Guid]::NewGuid().ToString('N'))
$stateRoot = Join-Path $validationLocalAppData 'MORPHEUS'
$sentinel = Join-Path $stateRoot 'config\m20-upgrade-preservation.txt'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$script:Results = [ordered]@{}
$script:ValidationSha = $null
$script:CurrentStage = $null
$script:CurrentLog = $null
$script:ValidationTag = $null
$script:TagCreated = $false
$script:FullTestSummary = $null
$script:ArchitectureTestSummary = $null
$script:PowerShellExecutable = (Get-Process -Id $PID).Path
$script:TestIdentity = $null
$script:SigningExercised = $false
. (Join-Path $repo 'scripts\lib\MorpheusAuthenticode.ps1')

# The subject the pinned Inno Setup compiler is signed with: the positive control's expected publisher, a third party's
# and never MORPHEUS's. Its pattern is pinned by distribution/ensure-inno-setup.ps1.
$script:CompilerPublisher = 'CN=Pyrsys B.V., O=Pyrsys B.V., S=Noord-Holland, C=NL'
# Who a binary is checked against when MORPHEUS's publisher is not declared yet: a name no certificate carries.
$script:StandInPublisher = 'CN=MORPHEUS rehearsal stand-in publisher'

function Write-Section([string]$Title) {
    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host $Title
    Write-Host ('=' * 78)
}

function Assert-Tool([string]$Name) {
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required tool is not available on PATH: $Name"
    }
}

function Invoke-NativeProcessToLog {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogPath,
        [string]$WorkingDirectory = $repo
    )
    $stderrPath = $LogPath + '.stderr'
    Remove-Item -LiteralPath $LogPath, $stderrPath -Force -ErrorAction SilentlyContinue
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments `
            -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $LogPath -RedirectStandardError $stderrPath
        $stdoutLines = @(Get-Content -LiteralPath $LogPath -ErrorAction SilentlyContinue)
        $stderrLines = @(Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue)
        if ($stderrLines.Count -gt 0) { Add-Content -LiteralPath $LogPath -Value $stderrLines }
        $stdoutLines | ForEach-Object { Write-Host $_ }
        $stderrLines | ForEach-Object { Write-Host $_ }
        return [int]$process.ExitCode
    } finally {
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-LoggedStage {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogName
    )
    $script:CurrentStage = $Name
    $script:CurrentLog = Join-Path $logRoot $LogName
    Write-Section $Name
    Write-Host ('Command: ' + $FilePath + ' ' + ($Arguments -join ' '))
    Write-Host ('Log:     ' + $script:CurrentLog)
    $started = Get-Date
    $exitCode = Invoke-NativeProcessToLog -FilePath $FilePath -Arguments $Arguments -LogPath $script:CurrentLog
    if ($exitCode -ne 0) {
        $script:Results[$Name] = "FAIL ($exitCode)"
        throw "Stage '$Name' failed with exit code $exitCode"
    }
    $elapsed = (Get-Date) - $started
    $script:Results[$Name] = ('PASS ({0:n1}s)' -f $elapsed.TotalSeconds)
}

function Get-SurefireTotals([string]$Root) {
    $totals = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0; Suites = 0 }
    $reports = @(Get-ChildItem -Path $Root -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $document.testsuite
        if ($null -eq $suite) { continue }
        $totals.Tests += [int]$suite.tests
        $totals.Failures += [int]$suite.failures
        $totals.Errors += [int]$suite.errors
        $totals.Skipped += [int]$suite.skipped
        $totals.Suites++
    }
    return [pscustomobject]$totals
}

# A test that an operating-system condition disables is recorded by JUnit with this reason. It is the only skip this
# validator accepts: any other one -- a disabled test, an assumption that stopped holding -- is named and refused.
function Get-UnexplainedSkips([string]$Root) {
    $unexplained = New-Object System.Collections.Generic.List[string]
    $reports = @(Get-ChildItem -Path $Root -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        foreach ($case in @($document.SelectNodes('//testcase[skipped]'))) {
            $reason = [string]$case.SelectSingleNode('skipped').GetAttribute('message')
            if (-not $reason.StartsWith('Disabled on operating system')) {
                $unexplained.Add(('{0}.{1} ({2})' -f $case.GetAttribute('classname'), $case.GetAttribute('name'), $reason))
            }
        }
    }
    return ,$unexplained
}

function Assert-Sha256([string]$AssetPath) {
    if (-not (Test-Path -LiteralPath $AssetPath)) { throw "Missing release asset: $AssetPath" }
    $checksumPath = $AssetPath + '.sha256'
    if (-not (Test-Path -LiteralPath $checksumPath)) { throw "Missing checksum: $checksumPath" }
    $recorded = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $AssetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($recorded -ne $actual) { throw "SHA-256 mismatch for $AssetPath recorded=$recorded actual=$actual" }
    Write-Host "SHA-256 PASS: $(Split-Path -Leaf $AssetPath) $actual"
}

function Assert-InstallerContract {
    $iss = Join-Path $repo 'distribution\windows\MORPHEUS.iss'
    $text = Get-Content -LiteralPath $iss -Raw
    $required = @(
        'DefaultDirName={localappdata}\Programs\MORPHEUS',
        'PrivilegesRequired=lowest',
        'Name: "addtopath"',
        'AppId={{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}'
    )
    foreach ($token in $required) {
        if ($text -notlike "*$token*") { throw "Installer contract missing: $token" }
    }
    if ($text -match '(?i)UninstallDelete.*MORPHEUS') {
        throw 'Installer must not delete persistent MORPHEUS state during uninstall'
    }
    $script:Results['Installer contract'] = 'PASS'
}

function Invoke-Setup([string]$SetupPath, [string]$Target, [bool]$AddToPath) {
    $taskArgument = if ($AddToPath) { '/TASKS="addtopath"' } else { '/TASKS=""' }
    $arguments = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', "/DIR=`"$Target`"", $taskArgument)
    $process = Start-Process -FilePath $SetupPath -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Setup failed with exit code $($process.ExitCode)" }
}

function Invoke-Uninstall([string]$Target) {
    $uninstaller = Get-ChildItem -LiteralPath $Target -Filter 'unins*.exe' -File | Select-Object -First 1
    if ($null -eq $uninstaller) { throw "Uninstaller not found under $Target" }
    $process = Start-Process -FilePath $uninstaller.FullName `
        -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART') -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Uninstall failed with exit code $($process.ExitCode)" }
    Start-Sleep -Milliseconds 750
}

function Normalize-PathEntry([string]$Value) {
    return $Value.Trim().TrimEnd('\')
}

function UserPathContains([string]$Entry) {
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($null -eq $userPath) { return $false }
    $target = Normalize-PathEntry $Entry
    return @($userPath -split ';' | ForEach-Object { Normalize-PathEntry $_ }) -contains $target
}

function Invoke-InstalledNoJdkSmoke([string]$Launcher) {
    $savedJavaHome = $env:JAVA_HOME
    $savedPath = $env:Path
    $savedLocalAppData = $env:LOCALAPPDATA
    try {
        $env:JAVA_HOME = ''
        $env:Path = "$env:SystemRoot\System32;$env:SystemRoot"
        $env:LOCALAPPDATA = $validationLocalAppData

        $versionText = (& $Launcher --json version 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed no-JDK version smoke failed: $versionText" }
        $versionView = $versionText | ConvertFrom-Json
        if ([string]$versionView.version -ne $Version) {
            throw "Installed version is $($versionView.version); expected $Version"
        }

        $pathsText = (& $Launcher --json paths 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed paths smoke failed: $pathsText" }
        $paths = $pathsText | ConvertFrom-Json
        $expectedData = (Join-Path $stateRoot 'data')
        $expectedConfig = (Join-Path $stateRoot 'config')
        $expectedLogs = (Join-Path $stateRoot 'logs')
        if ([IO.Path]::GetFullPath([string]$paths.dataDirectory) -ne [IO.Path]::GetFullPath($expectedData)) {
            throw "Unexpected PROD data path: $($paths.dataDirectory) expected=$expectedData"
        }
        if ([IO.Path]::GetFullPath([string]$paths.configDirectory) -ne [IO.Path]::GetFullPath($expectedConfig)) {
            throw "Unexpected PROD config path: $($paths.configDirectory) expected=$expectedConfig"
        }
        if ([IO.Path]::GetFullPath([string]$paths.logsDirectory) -ne [IO.Path]::GetFullPath($expectedLogs)) {
            throw "Unexpected PROD logs path: $($paths.logsDirectory) expected=$expectedLogs"
        }

        $minosText = (& $Launcher --json minos-status 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed MINOS status failed: $minosText" }
        $minos = $minosText | ConvertFrom-Json
        if ([string]$minos.state -ne 'DISABLED') { throw "MINOS must be opt-in: $minosText" }

        $nexusText = (& $Launcher --json nexus-status 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed NEXUS status failed: $nexusText" }
        $nexus = $nexusText | ConvertFrom-Json
        if ([string]$nexus.state -ne 'DISABLED') { throw "NEXUS must be opt-in: $nexusText" }

        $projects = (& $Launcher --json projects list 2>&1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Installed SQLite creation smoke failed: $projects" }
    } finally {
        $env:JAVA_HOME = $savedJavaHome
        $env:Path = $savedPath
        $env:LOCALAPPDATA = $savedLocalAppData
    }
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Test-InstalledApi([string]$Launcher) {
    $port = Get-FreeLoopbackPort
    # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
    # here inherits the ACLs of whatever it sits under, and the real owner-controlled storage path is never
    # exercised. Under the repository that inheritance is what the hardener refuses, which made a packaged
    # product gate depend on the permissions of a development checkout.
    $apiData = Join-Path ([IO.Path]::GetTempPath()) ('morpheus-m20-api-' + [Guid]::NewGuid().ToString('N'))
    $stdout = Join-Path $logRoot 'installed-api.stdout.log'
    $stderr = Join-Path $logRoot 'installed-api.stderr.log'
    $process = Start-Process -FilePath $Launcher `
        -ArgumentList @('--data-dir', $apiData, 'api', '--host', '127.0.0.1', '--port', "$port") `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    try {
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) {
                $diagnostic = if (Test-Path $stderr) { Get-Content $stderr -Raw } else { '' }
                throw "Installed API exited before becoming ready: $diagnostic"
            }
            try {
                $health = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/health" -UseBasicParsing -TimeoutSec 2
                $ready = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/readiness" -UseBasicParsing -TimeoutSec 2
                $metrics = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/v1/metrics" -UseBasicParsing -TimeoutSec 2
                if ($health.StatusCode -eq 200 -and $ready.StatusCode -eq 200 -and $metrics.StatusCode -eq 200) { return }
            } catch { Start-Sleep -Milliseconds 100 }
        }
        throw 'Installed API health/readiness/metrics timed out'
    } finally {
        # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
        Remove-Item -LiteralPath $apiData -Recurse -Force -ErrorAction SilentlyContinue
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        try { $process.WaitForExit(5000) | Out-Null } catch { }
    }
}

# One verification, one expected answer. The qualification calls the same Test-MorpheusAuthenticode, so a
# verification that stops refusing fails the night it does, under the name of the proof that no longer holds.
function Assert-AuthenticodeAnswer([string]$Proof, [string]$Path, [AllowEmptyString()][string]$Publisher, [string[]]$Expected) {
    $answer = Test-MorpheusAuthenticode -Path $Path -ExpectedSubject $Publisher -SignatureRequired
    if ($Expected -notcontains $answer.Outcome) {
        throw "Authenticode proof '$Proof' answered $($answer.Outcome), expected $($Expected -join ' or '): $($answer.Detail)"
    }
    Write-Host "Authenticode proof '$Proof': $($answer.Outcome) as expected"
    $script:Results["Authenticode: $Proof"] = "PASS ($($answer.Outcome))"
}

function Invoke-AuthenticodeProofs([string]$Setup) {
    $declaration = Read-MorpheusSigningDeclaration -Repository $repo
    $publisher = if ([string]::IsNullOrEmpty($declaration.PublisherSubject)) { $script:StandInPublisher } else { $declaration.PublisherSubject }
    $compiler = [string](@(& (Join-Path $repo 'distribution\ensure-inno-setup.ps1'))[-1])
    if (-not (Test-Path -LiteralPath $compiler -PathType Leaf)) { throw "The pinned Inno Setup compiler was not resolved: $compiler" }

    if ($TestSigning) {
        Assert-AuthenticodeAnswer 'test-signed installer refused' $Setup $publisher @('AUTHENTICODE_SIGNATURE_NOT_TRUSTED', 'AUTHENTICODE_SIGNER_UNEXPECTED')
    } else {
        Assert-AuthenticodeAnswer 'unsigned installer refused' $Setup $publisher @('AUTHENTICODE_SIGNATURE_MISSING')
    }
    Assert-AuthenticodeAnswer 'another publisher refused' $compiler $publisher @('AUTHENTICODE_SIGNER_UNEXPECTED')
    if ([string]::IsNullOrEmpty($declaration.PublisherSubject)) {
        Assert-AuthenticodeAnswer 'undeclared publisher refused' $compiler '' @('AUTHENTICODE_PUBLISHER_NOT_DECLARED')
    }
    $altered = Join-Path $outputRoot 'authenticode-altered-compiler.exe'
    $bytes = [IO.File]::ReadAllBytes($compiler)
    $offset = [int]($bytes.Length / 2)
    $bytes[$offset] = $bytes[$offset] -bxor 0xFF
    [IO.File]::WriteAllBytes($altered, $bytes)
    try {
        Assert-AuthenticodeAnswer 'altered binary refused' $altered $script:CompilerPublisher @('AUTHENTICODE_SIGNATURE_INVALID')
    } finally {
        Remove-Item -LiteralPath $altered -Force -ErrorAction SilentlyContinue
    }
    Assert-AuthenticodeAnswer 'positive control accepted' $compiler $script:CompilerPublisher @('PASS')
}

function New-TestSigningIdentity {
    # Created in the personal store of the current user and nowhere else by this script; New-SelfSignedCertificate
    # also files a copy among the intermediate authorities, which Remove-TestSigningIdentity removes with it. No root
    # store is touched, so nothing on the machine trusts what this identity signs.
    $subject = 'CN=MORPHEUS release rehearsal test identity ' + [Guid]::NewGuid().ToString('N')
    $certificate = New-SelfSignedCertificate -Type CodeSigningCert -Subject $subject -CertStoreLocation 'Cert:\CurrentUser\My' `
        -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddDays(1)
    $script:TestIdentity = $certificate
    $env:MORPHEUS_WINDOWS_SIGNING_TEST_THUMBPRINT = $certificate.Thumbprint
    Write-Host "Test signing identity created for this run: $subject"
}

function Get-StoredCopies([string]$Thumbprint) {
    return @(Get-ChildItem -Path 'Cert:\' -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.PSObject.Properties['Thumbprint'] -and $_.Thumbprint -eq $Thumbprint })
}

function Remove-TestSigningIdentity {
    if ($null -eq $script:TestIdentity) { return }
    $thumbprint = $script:TestIdentity.Thumbprint
    foreach ($copy in Get-StoredCopies $thumbprint) {
        Remove-Item -LiteralPath $copy.PSPath -DeleteKey -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $copy.PSPath) { Remove-Item -LiteralPath $copy.PSPath -ErrorAction SilentlyContinue }
    }
    Remove-Item Env:\MORPHEUS_WINDOWS_SIGNING_TEST_THUMBPRINT -ErrorAction SilentlyContinue
    $left = Get-StoredCopies $thumbprint
    $script:TestIdentity = $null
    if ($left.Count -ne 0) {
        throw "The test signing identity outlived the run: $(@($left | ForEach-Object { $_.PSParentPath }) -join ', ')"
    }
    Write-Host 'Test signing identity removed from every certificate store'
}

function Assert-SignedByTestIdentity([string]$What, [string]$Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ([string]$signature.SignatureType -ne 'Authenticode' -or $null -eq $signature.SignerCertificate `
            -or $signature.SignerCertificate.Thumbprint -ne $script:TestIdentity.Thumbprint) {
        throw "WINDOWS_SIGNING_FAILED: $What is not signed by the run's test identity: $Path"
    }
    $script:Results["Test-signed: $What"] = 'PASS'
}

function Get-ExtractedLauncher([string]$Archive, [string]$Into, [string]$Root) {
    Remove-Item -LiteralPath $Into -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -LiteralPath $Archive -DestinationPath $Into -Force
    $executables = Get-MorpheusImageExecutables -ImageRoot (Join-Path $Into $Root)
    if ($executables.Product.Count -eq 0) { throw "No executable outside the runtime in $Archive" }
    return $executables.Product
}

function Assert-TestSignedArtifacts([string]$Zip, [string]$Setup, $Manifest) {
    $work = Join-Path $outputRoot 'signing-check'
    $archived = @(Get-ExtractedLauncher $Zip (Join-Path $work 'archive') 'morpheus')
    $payloadZip = Join-Path $repo 'dist\morpheus-payload.zip'
    $payload = @(Get-ExtractedLauncher $payloadZip (Join-Path $work 'payload') '.')
    foreach ($executable in $archived) { Assert-SignedByTestIdentity "archive $(Split-Path -Leaf $executable)" $executable }
    foreach ($executable in $payload) { Assert-SignedByTestIdentity "payload $(Split-Path -Leaf $executable)" $executable }
    $archivedLauncher = $archived | Where-Object { (Split-Path -Leaf $_) -eq 'morpheus.exe' } | Select-Object -First 1
    $payloadLauncher = $payload | Where-Object { (Split-Path -Leaf $_) -eq 'morpheus.exe' } | Select-Object -First 1
    if ($null -eq $archivedLauncher -or $null -eq $payloadLauncher `
            -or (Get-FileHash -LiteralPath $archivedLauncher).Hash -ne (Get-FileHash -LiteralPath $payloadLauncher).Hash) {
        throw 'The launcher of the archive and the launcher of the installer payload are not byte-identical'
    }
    $script:Results['Archive and payload launchers identical'] = 'PASS'
    Assert-SignedByTestIdentity 'installer' $Setup
    foreach ($asset in $Manifest.assets) {
        if ($asset.signature.status -ne 'signed' -or $asset.signature.signer -cne $script:TestIdentity.Subject) {
            throw "The release manifest does not state $($asset.name) as signed by the run's test identity"
        }
    }
    $script:Results['Manifest states the test signer'] = 'PASS'
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}

function Write-FailureSummary([System.Exception]$Failure) {
    $path = Join-Path $outputRoot 'failure-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M20 VALIDATION FAILURE')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    if ($script:ValidationSha) { $lines.Add(('SHA:       ' + $script:ValidationSha)) }
    if ($script:CurrentStage) { $lines.Add(('Stage:     ' + $script:CurrentStage)) }
    $lines.Add(('Error:     ' + $Failure.Message))
    if ($script:CurrentLog -and (Test-Path -LiteralPath $script:CurrentLog)) {
        $lines.Add('Relevant log tail:')
        foreach ($line in (Get-Content -LiteralPath $script:CurrentLog -Tail 100)) { $lines.Add($line) }
    }
    $lines | Set-Content -LiteralPath $path -Encoding utf8
    $lines | ForEach-Object { Write-Host $_ }
    Write-Host "Failure summary: $path"
}

function Write-Summary([bool]$Success) {
    $path = Join-Path $outputRoot 'validation-summary.txt'
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('M20 VALIDATION SUMMARY')
    $lines.Add(('Timestamp: ' + (Get-Date).ToString('o')))
    $lines.Add(('SHA:       ' + $script:ValidationSha))
    $lines.Add(('Version:   ' + $Version))
    $lines.Add(('Result:    ' + $(if ($Success) { 'PASS' } else { 'FAIL' })))
    $lines.Add('Linux proof: NOT EXECUTED BY THIS WINDOWS VALIDATOR')
    if ($Success) {
        $lines.Add('RELEASE REHEARSAL: the build half of the release ran; nothing was tagged on origin, attested or published.')
    }
    $lines.Add('Not exercised by a rehearsal: the tag being reachable from main')
    $lines.Add('Not exercised by a rehearsal: the provenance attestation')
    $lines.Add('Not exercised by a rehearsal: the publication of the release')
    $lines.Add('Not exercised by a rehearsal: the public read-back of a published release')
    $lines.Add('Not exercised by a rehearsal: the Authenticode signature of the release binaries')
    $lines.Add('Not exercised by a rehearsal: the release signing identity')
    $lines.Add('Not exercised by a rehearsal: the signing service')
    if ($script:SigningExercised) {
        $lines.Add('Signing path: exercised with an ephemeral test identity; no timestamp authority was contacted')
    } else {
        $lines.Add('Signing path: not exercised (run with -TestSigning to sign with an ephemeral test identity)')
    }
    if ($script:FullTestSummary) {
        $lines.Add(('Full reactor tests: {0}; failures: {1}; errors: {2}; skipped: {3}; suites: {4}' -f
            $script:FullTestSummary.Tests, $script:FullTestSummary.Failures, $script:FullTestSummary.Errors,
            $script:FullTestSummary.Skipped, $script:FullTestSummary.Suites))
    }
    if ($script:ArchitectureTestSummary) {
        $lines.Add(('Architecture tests: {0}; failures: {1}; errors: {2}; skipped: {3}' -f
            $script:ArchitectureTestSummary.Tests, $script:ArchitectureTestSummary.Failures,
            $script:ArchitectureTestSummary.Errors, $script:ArchitectureTestSummary.Skipped))
    }
    $lines.Add('')
    foreach ($entry in $script:Results.GetEnumerator()) {
        $lines.Add(('{0,-38} {1}' -f $entry.Key, $entry.Value))
    }
    $lines | Set-Content -LiteralPath $path -Encoding utf8
    Write-Section 'M20 summary'
    $lines | ForEach-Object { Write-Host $_ }
    Write-Host "Summary file: $path"
}

try {
    Write-Section 'Workspace / SHA / version'
    Assert-Tool 'git'
    $script:ValidationSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Git HEAD' }
    $status = @(git status --porcelain)
    if ($status.Count -gt 0) { throw 'Exact-head validation requires a clean Git workspace' }
    $pom = [xml](Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw)
    $projectVersion = [string]$pom.project.version
    if ($projectVersion -ne $Version) { throw "pom.xml version is $projectVersion; expected $Version" }
    Write-Host "SHA:     $script:ValidationSha"
    Write-Host "Version: $projectVersion"
    $script:Results['Workspace / SHA / version'] = 'PASS'

    Assert-Tool 'java'
    $mvnw = Join-Path $repo 'mvnw.cmd'
    # verify, not test: the architecture suite this reactor runs reads the JaCoCo reports and the packaged
    # morpheus-provider-reference JAR, and neither exists after `clean test`. The coverage gate and the
    # provider-plugin contract failed here for that reason alone. Every other validator uses verify.
    Invoke-LoggedStage -Name 'Full Maven reactor' -FilePath $mvnw -Arguments @('clean', 'verify') -LogName '01-full-reactor.log'
    $script:FullTestSummary = Get-SurefireTotals $repo
    $script:ArchitectureTestSummary = Get-SurefireTotals (Join-Path $repo 'morpheus-architecture-tests')
    if ($script:FullTestSummary.Failures -ne 0 -or $script:FullTestSummary.Errors -ne 0) {
        throw 'Full Maven reactor contains failures/errors'
    }
    # A skipped test used to be refused whatever its cause, which made this validator fail on every current tree: some
    # tests are conditional on the operating system and are skipped on the other one. Those are accepted, by the reason
    # JUnit records, and counted in the summary; every other skip is still a refusal, and is named.
    $unexplainedSkips = Get-UnexplainedSkips $repo
    if ($unexplainedSkips.Count -ne 0) {
        throw ('Full Maven reactor skipped tests for another reason than an operating-system condition: ' + ($unexplainedSkips -join '; '))
    }

    $script:CurrentStage = 'Installer contract'
    $script:CurrentLog = $null
    Assert-InstallerContract

    $script:CurrentStage = 'Validation tag preparation'
    $script:CurrentLog = $null
    $shortSha = $script:ValidationSha.Substring(0, 12)
    $script:ValidationTag = "m20-validation-$shortSha"
    $existingValidationTags = @(git tag --list $script:ValidationTag)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect local validation tags' }
    if ($existingValidationTags.Count -gt 0) {
        git tag -d $script:ValidationTag | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to delete stale local validation tag $($script:ValidationTag)" }
    }
    git tag $script:ValidationTag $script:ValidationSha
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create local validation tag' }
    $script:TagCreated = $true
    $script:Results['Validation tag preparation'] = 'PASS'

    $signingState = 'none'
    if ($TestSigning) {
        $script:CurrentStage = 'Test signing identity'
        New-TestSigningIdentity
        $signingState = 'test'
        $script:Results['Test signing identity'] = 'PASS (created for this run)'
    }

    $releaseScript = Join-Path $repo 'distribution\build-release.ps1'
    Invoke-LoggedStage -Name 'Tagged Windows release build' -FilePath $script:PowerShellExecutable `
        -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $releaseScript, '-Version', $Version, '-ExpectedTag', $script:ValidationTag, '-SigningState', $signingState) `
        -LogName '02-release-build.log'

    $zip = Join-Path $repo "dist\morpheus-$Version-windows-x64.zip"
    $setup = Join-Path $repo "dist\MORPHEUS-$Version-windows-x64-setup.exe"
    Assert-Sha256 $zip
    Assert-Sha256 $setup
    $manifestPath = Join-Path $repo "dist\morpheus-$Version-windows-x64-release-manifest.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.version -ne $Version -or $manifest.gitSha -ne $script:ValidationSha -or $manifest.tag -ne $script:ValidationTag) {
        throw 'Release manifest version/tag/SHA does not match validation head'
    }
    if ($manifest.userJdkRequired -ne $false -or $manifest.uninstallPreservesPersistentState -ne $true) {
        throw 'Release manifest runtime/data invariants are invalid'
    }
    $script:Results['SHA-256 + release manifest'] = 'PASS'

    $script:CurrentStage = 'Authenticode proofs'
    if ($TestSigning) {
        Assert-TestSignedArtifacts -Zip $zip -Setup $setup -Manifest $manifest
    } else {
        foreach ($asset in $manifest.assets) {
            if ($asset.signature.status -ne 'unsigned') { throw "The unsigned rehearsal manifest states $($asset.name) as $($asset.signature.status)" }
        }
        $script:Results['Manifest states unsigned'] = 'PASS'
    }
    Invoke-AuthenticodeProofs -Setup $setup

    Remove-Item -LiteralPath $installRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $validationLocalAppData -Recurse -Force -ErrorAction SilentlyContinue

    Write-Section 'Windows install + PATH + no-JDK + API'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $true
    $launcher = Join-Path $installRoot 'morpheus.exe'
    if (-not (Test-Path -LiteralPath $launcher)) { throw "Installed launcher missing: $launcher" }
    if (-not (UserPathContains $installRoot)) { throw 'PATH option did not add the install directory to the user PATH' }
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    Test-InstalledApi -Launcher $launcher
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $sentinel) | Out-Null
    'preserve-across-upgrade-and-uninstall' | Set-Content -LiteralPath $sentinel -Encoding utf8
    $database = Join-Path $stateRoot 'data\morpheus.db'
    if (-not (Test-Path -LiteralPath $database)) { throw "Installed runtime did not create persistent database: $database" }
    $script:Results['Install + PATH + no-JDK + API'] = 'PASS'
    if ($TestSigning) {
        $installedUninstaller = Get-ChildItem -LiteralPath $installRoot -Filter 'unins*.exe' -File | Select-Object -First 1
        if ($null -eq $installedUninstaller) { throw "Uninstaller not found under $installRoot" }
        Assert-SignedByTestIdentity 'installed uninstaller' $installedUninstaller.FullName
        if ((Get-FileHash -LiteralPath $launcher).Hash -ne (Get-FileHash -LiteralPath (Join-Path $repo 'dist\.m20-windows\image\morpheus\morpheus.exe')).Hash) {
            throw 'The installed launcher is not byte-identical to the signed launcher of the image'
        }
        $script:Results['Installed launcher identical'] = 'PASS'
    }

    Write-Section 'Upgrade preservation'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $true
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Upgrade replaced or deleted persistent state'
    }
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    $script:Results['Upgrade preserves data/config'] = 'PASS'

    Write-Section 'Uninstall preservation'
    Invoke-Uninstall -Target $installRoot
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Uninstall deleted persistent MORPHEUS state'
    }
    if (UserPathContains $installRoot) { throw 'Uninstall left the MORPHEUS install directory in the user PATH' }
    $script:Results['Uninstall preserves persistent state'] = 'PASS'

    Write-Section 'Reinstall over preserved state'
    Invoke-Setup -SetupPath $setup -Target $installRoot -AddToPath $false
    $launcher = Join-Path $installRoot 'morpheus.exe'
    Invoke-InstalledNoJdkSmoke -Launcher $launcher
    if (-not (Test-Path -LiteralPath $sentinel) -or -not (Test-Path -LiteralPath $database)) {
        throw 'Reinstall did not preserve existing data/config'
    }
    $script:Results['Reinstall reuses persistent state'] = 'PASS'
    Invoke-Uninstall -Target $installRoot

    Write-Section 'Exact-head stability'
    $endingSha = (git rev-parse HEAD).Trim()
    $endingStatus = @(git status --porcelain)
    if ($endingSha -ne $script:ValidationSha) { throw "HEAD changed during validation: $script:ValidationSha -> $endingSha" }
    if ($endingStatus.Count -gt 0) { throw 'Tracked or untracked workspace state changed during validation' }
    $script:Results['Exact-head stability'] = 'PASS'

    if ($TestSigning) {
        $script:CurrentStage = 'Test signing identity removal'
        Remove-TestSigningIdentity
        $script:SigningExercised = $true
        $script:Results['Test signing identity removal'] = 'PASS'
    }

    Write-Summary $true
    exit 0
} catch {
    if ($script:CurrentStage -and -not $script:Results.Contains($script:CurrentStage)) {
        $script:Results[$script:CurrentStage] = 'FAIL'
    }
    Write-FailureSummary $_.Exception
    Write-Summary $false
    exit 1
} finally {
    # Every exit path, a failed build included: the identity never outlives the run.
    $identityFailure = $null
    try { Remove-TestSigningIdentity } catch { $identityFailure = $_.Exception.Message }
    if ($identityFailure) { Write-Host $identityFailure }
    if ($script:TagCreated -and $script:ValidationTag) {
        git tag -d $script:ValidationTag 2>$null | Out-Null
    }
    if ($identityFailure) { exit 1 }
}
