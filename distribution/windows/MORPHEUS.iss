#define MyAppName "MORPHEUS"
#define MyAppPublisher "FTurleque"
#ifndef MyAppVersion
#define MyAppVersion "1.0.0"
#endif
#ifndef SourceDir
#define SourceDir "..\..\dist\.m19-windows\image\morpheus"
#endif
#ifndef OutputDir
#define OutputDir "..\..\dist"
#endif
#ifndef PayloadZip
#define PayloadZip "..\..\dist\morpheus-payload.zip"
#endif
#ifndef UpdateInstallationScript
#define UpdateInstallationScript "update-installation.ps1"
#endif

[Setup]
; A smoke build (-DSmokeMode=1) gets its own stable AppId/name/output filename so it can never be mistaken
; for -- or leave an HKCU uninstall entry identified as -- the real production installer. Production's own
; AppId is completely untouched by this: #ifdef only compiles the smoke branch in when the define is passed.
#ifdef SmokeMode
AppId={{6BF23F0E-6C9B-4B5A-9E51-8B6D1F0C7E42}
AppName=MORPHEUS Setup Smoke
OutputBaseFilename=MORPHEUS-{#MyAppVersion}-windows-x64-setup-smoke
#else
AppId={{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}
AppName={#MyAppName}
OutputBaseFilename=MORPHEUS-{#MyAppVersion}-windows-x64-setup
#endif
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
VersionInfoVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\MORPHEUS
DefaultGroupName=MORPHEUS
DisableProgramGroupPage=yes
AllowNoIcons=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupLogging=yes
ChangesEnvironment=yes
UninstallDisplayIcon={app}\morpheus.exe
UsePreviousAppDir=yes
UsePreviousTasks=yes

[Tasks]
Name: "addtopath"; Description: "Ajouter MORPHEUS au PATH utilisateur"; GroupDescription: "Intégration système :"; Flags: unchecked

[Files]
Source: "{#SourceDir}\integration\configure-mcp-clients.ps1"; DestDir: "{tmp}"; Flags: dontcopy
Source: "{#UpdateInstallationScript}"; DestDir: "{tmp}"; Flags: dontcopy
Source: "{#PayloadZip}"; DestDir: "{tmp}"; Flags: dontcopy

[Icons]
Name: "{group}\MORPHEUS"; Filename: "{app}\morpheus.exe"

[UninstallDelete]
; The program payload is activated by PrepareToInstall's transactional engine, not Inno's own [Files]
; tracking, so Inno's automatic per-file uninstall has nothing to remove without this: {app} holds only
; MORPHEUS program files (data/config roots are separate persistent locations), so a full recursive delete
; here is safe and matches what the old direct-copy [Files] entry gave Inno for free.
Type: filesandordirs; Name: "{app}"

[UninstallRun]
; {code:...} calls UninstallParameters (in [Code]) so a smoke build's uninstall also honors
; MORPHEUS_SMOKE_* overrides via BuildSmokeOverrideParameters -- a static Parameters string here would
; silently fall back to the real LocalAppData paths during smoke uninstall regardless of what was overridden
; during install.
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "{code:UninstallParameters}"; Flags: runhidden waituntilterminated skipifdoesntexist; RunOnceId: "RemoveMorpheusNativeMcpClients"

[Code]
type
  TClientRow = record
    Id: String;
    DisplayName: String;
  end;

var
  SetupTypePage: TInputOptionWizardPage;
  AdvancedRootsPage: TInputDirWizardPage;
  ClientsPage: TWizardPage;
  SummaryPage: TOutputMsgMemoWizardPage;
  ClientRows: array[0..4] of TClientRow;
  ClientCheckBoxes: array[0..4] of TNewCheckBox;
  ClientStatusLabels: array[0..4] of TNewStaticText;
  DetectReportPath: String;
  DetectionHasRun: Boolean;

const
  ClientCount = 5;

function NormalizePathEntry(Value: String): String;
begin
  Result := RemoveBackslashUnlessRoot(Trim(Value));
end;

function PathContainsEntry(PathValue, Entry: String): Boolean;
var
  Haystack: String;
  Needle: String;
begin
  Haystack := ';' + Uppercase(PathValue) + ';';
  Needle := ';' + Uppercase(NormalizePathEntry(Entry)) + ';';
  Result := Pos(Needle, Haystack) > 0;
end;

procedure AddUserPath(Entry: String);
var
  CurrentPath: String;
  NormalizedEntry: String;
begin
  NormalizedEntry := NormalizePathEntry(Entry);
  if not RegQueryStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    CurrentPath := '';

  if PathContainsEntry(CurrentPath, NormalizedEntry) then
    exit;

  if CurrentPath = '' then
    CurrentPath := NormalizedEntry
  else if Copy(CurrentPath, Length(CurrentPath), 1) = ';' then
    CurrentPath := CurrentPath + NormalizedEntry
  else
    CurrentPath := CurrentPath + ';' + NormalizedEntry;

  if not RegWriteExpandStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    RaiseException('Impossible de mettre à jour le PATH utilisateur.');
end;

procedure RemoveUserPath(Entry: String);
var
  CurrentPath: String;
  NormalizedEntry: String;
  SearchValue: String;
  SearchEntry: String;
  Position: Integer;
begin
  if not RegQueryStringValue(HKCU, 'Environment', 'Path', CurrentPath) then
    exit;

  NormalizedEntry := NormalizePathEntry(Entry);
  SearchValue := ';' + CurrentPath + ';';
  SearchEntry := ';' + NormalizedEntry + ';';

  Position := Pos(Uppercase(SearchEntry), Uppercase(SearchValue));
  while Position > 0 do
  begin
    Delete(SearchValue, Position, Length(SearchEntry) - 1);
    Position := Pos(Uppercase(SearchEntry), Uppercase(SearchValue));
  end;

  if (Length(SearchValue) > 0) and (SearchValue[1] = ';') then
    Delete(SearchValue, 1, 1);
  if (Length(SearchValue) > 0) and (SearchValue[Length(SearchValue)] = ';') then
    Delete(SearchValue, Length(SearchValue), 1);

  RegWriteExpandStringValue(HKCU, 'Environment', 'Path', SearchValue);
end;

// Standard keeps the current safe defaults (%LOCALAPPDATA%\MORPHEUS\data|config); Advanced exposes the two
// roots that genuinely exist as independent runtime settings (-DataRoot/-ConfigRoot on the integration
// manager) -- never a pseudo-option that has no effect.
function IsAdvancedSetup(): Boolean;
begin
  Result := SetupTypePage.Values[1];
end;

// {localappdata} resolves through the Windows Known Folder API exactly like .NET's
// [Environment]::GetFolderPath, so it cannot be redirected by setting LOCALAPPDATA in the setup's own
// process environment (verified empirically). IsSmokeOverrideActive/GetDataRoot/GetConfigRoot are the only
// place production's own path resolution is touched by the smoke mechanism, and only the smoke-compiled
// binary even contains the branch that reads it.
function IsSmokeOverrideActive(): Boolean;
begin
  Result := False;
#ifdef SmokeMode
  Result := GetEnv('MORPHEUS_SMOKE_MODE') = '1';
#endif
end;

function GetDataRoot(): String;
begin
#ifdef SmokeMode
  if IsSmokeOverrideActive() and (GetEnv('MORPHEUS_SMOKE_DATA_ROOT') <> '') then
  begin
    Result := GetEnv('MORPHEUS_SMOKE_DATA_ROOT');
    exit;
  end;
#endif
  if IsAdvancedSetup() then
    Result := AdvancedRootsPage.Values[0]
  else
    Result := ExpandConstant('{localappdata}\MORPHEUS\data');
end;

function GetConfigRoot(): String;
begin
#ifdef SmokeMode
  if IsSmokeOverrideActive() and (GetEnv('MORPHEUS_SMOKE_CONFIG_ROOT') <> '') then
  begin
    Result := GetEnv('MORPHEUS_SMOKE_CONFIG_ROOT');
    exit;
  end;
#endif
  if IsAdvancedSetup() then
    Result := AdvancedRootsPage.Values[1]
  else
    Result := ExpandConstant('{localappdata}\MORPHEUS\config');
end;

// The remaining paths (state/log/backup roots, the two JSON client config files) have no Standard/Advanced
// UI at all -- they only ever come from the manager's own LocalAppData-based defaults in production. Smoke
// mode is the only way to redirect them, and only when explicitly compiled in and explicitly armed via
// MORPHEUS_SMOKE_MODE=1. This single function is called from both RunDetect and ConfigureNativeMcpClients so
// preflight and the manager can never see different values for the same run.
function BuildSmokeOverrideParameters(): String;
begin
  Result := '';
#ifdef SmokeMode
  if not IsSmokeOverrideActive() then
    exit;
  if GetEnv('MORPHEUS_SMOKE_STATE_PATH') <> '' then
    Result := Result + ' -StatePath "' + GetEnv('MORPHEUS_SMOKE_STATE_PATH') + '"';
  if GetEnv('MORPHEUS_SMOKE_LOG_PATH') <> '' then
    Result := Result + ' -LogPath "' + GetEnv('MORPHEUS_SMOKE_LOG_PATH') + '"';
  if GetEnv('MORPHEUS_SMOKE_BACKUP_ROOT') <> '' then
    Result := Result + ' -BackupRoot "' + GetEnv('MORPHEUS_SMOKE_BACKUP_ROOT') + '"';
  if GetEnv('MORPHEUS_SMOKE_CLAUDE_DESKTOP_CONFIG_PATH') <> '' then
    Result := Result + ' -ClaudeDesktopConfigPath "' + GetEnv('MORPHEUS_SMOKE_CLAUDE_DESKTOP_CONFIG_PATH') + '"';
  if GetEnv('MORPHEUS_SMOKE_COPILOT_JETBRAINS_CONFIG_PATH') <> '' then
    Result := Result + ' -CopilotJetBrainsConfigPath "' + GetEnv('MORPHEUS_SMOKE_COPILOT_JETBRAINS_CONFIG_PATH') + '"';
#endif
end;

// Runs the exact same detection code Install/Uninstall use (integration\configure-mcp-clients.ps1
// -Action Detect), extracted ahead of ssInstall since the real {app}\integration copy does not exist yet on
// a first-time install. The wizard and the manager can never diverge on a client's state because there is
// only one implementation of "what state is this client in".
procedure RunDetect;
var
  ResultCode: Integer;
  Parameters: String;
  PowerShell: String;
  ScriptPath: String;
begin
  ScriptPath := ExpandConstant('{tmp}\configure-mcp-clients.ps1');
  if not FileExists(ScriptPath) then
    ExtractTemporaryFile('configure-mcp-clients.ps1');

  DetectReportPath := ExpandConstant('{tmp}\morpheus-mcp-detect.ini');
  DeleteFile(DetectReportPath);

  PowerShell := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
  Parameters :=
    '-NoProfile -ExecutionPolicy Bypass -File "' + ScriptPath + '"' +
    ' -InstallRoot "' + WizardDirValue() + '"' +
    ' -Action Detect' +
    ' -DataRoot "' + GetDataRoot() + '"' +
    ' -ConfigRoot "' + GetConfigRoot() + '"' +
    ' -NativeCommandTimeoutSeconds 5' +
    ' -Out "' + DetectReportPath + '"' +
    BuildSmokeOverrideParameters();

  if (not Exec(PowerShell, Parameters, '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or
     (ResultCode <> 0) or (not FileExists(DetectReportPath)) then
    DetectReportPath := '';
end;

// Fail-closed: if detection could not run, every row is disabled and unchecked rather than guessing. Never
// auto-overwrite a Conflict row; only Available/NeedsRepair rows are selectable, and AlreadyManaged is shown
// checked-and-disabled since MORPHEUS already owns it.
procedure RefreshClientsPage;
var
  I: Integer;
  Section: String;
  Available, AlreadyManaged, NeedsRepair: Boolean;
  Reason: String;
begin
  for I := 0 to ClientCount - 1 do
  begin
    if DetectReportPath = '' then
    begin
      ClientCheckBoxes[I].Enabled := False;
      ClientCheckBoxes[I].Checked := False;
      ClientStatusLabels[I].Caption := 'Détection indisponible : ' + ClientRows[I].DisplayName + ' ne sera pas configuré automatiquement.';
      Continue;
    end;

    Section := ClientRows[I].Id;
    Available := GetIniString(Section, 'Available', '0', DetectReportPath) = '1';
    AlreadyManaged := GetIniString(Section, 'AlreadyManaged', '0', DetectReportPath) = '1';
    NeedsRepair := GetIniString(Section, 'NeedsRepair', '0', DetectReportPath) = '1';
    Reason := GetIniString(Section, 'Reason', '', DetectReportPath);

    if AlreadyManaged then
    begin
      ClientCheckBoxes[I].Enabled := False;
      ClientCheckBoxes[I].Checked := True;
    end
    else if NeedsRepair then
    begin
      ClientCheckBoxes[I].Enabled := True;
      ClientCheckBoxes[I].Checked := True;
    end
    else if Available then
    begin
      ClientCheckBoxes[I].Enabled := True;
      ClientCheckBoxes[I].Checked := False;
    end
    else
    begin
      // NotDetected or Conflict (a foreign, non-MORPHEUS 'morpheus' entry): never selectable, never touched.
      ClientCheckBoxes[I].Enabled := False;
      ClientCheckBoxes[I].Checked := False;
    end;

    ClientStatusLabels[I].Caption := Reason;
  end;
end;

// A silent/unattended install never renders the custom "Clients IA" page, so its checkboxes are never set
// by a click -- even though Inno still walks CurPageChanged through it internally (confirmed empirically),
// nothing there ever flips ClientCheckBoxes[I].Checked. /MORPHEUSMCPCLIENTS="id1,id2" (client ids:
// copilot-jetbrains, claude-desktop, copilot-cli, claude-code, codex) lets an unattended deployment select
// clients the same way a human would --
// but it can only select a row that detection already left Enabled, so a Conflict or NotDetected client is
// never selected this way either, exactly like the interactive page.
procedure ApplyCommandLineClientSelection;
var
  Requested: String;
  I: Integer;
begin
  Requested := ',' + ExpandConstant('{param:MORPHEUSMCPCLIENTS|}') + ',';
  if Requested = ',,' then
    exit;
  for I := 0 to ClientCount - 1 do
  begin
    if not ClientCheckBoxes[I].Enabled then
      Continue;
    if Pos(',' + ClientRows[I].Id + ',', Requested) > 0 then
      ClientCheckBoxes[I].Checked := True;
  end;
end;

procedure RefreshSummaryPage;
var
  Summary: String;
  I: Integer;
begin
  Summary := 'Répertoire du programme : ' + WizardDirValue() + #13#10;
  Summary := Summary + 'Répertoire de données : ' + GetDataRoot() + #13#10;
  Summary := Summary + 'Répertoire de configuration : ' + GetConfigRoot() + #13#10;
  if WizardIsTaskSelected('addtopath') then
    Summary := Summary + 'Ajout au PATH utilisateur : oui' + #13#10
  else
    Summary := Summary + 'Ajout au PATH utilisateur : non' + #13#10;

  Summary := Summary + #13#10 + 'Clients IA :' + #13#10;
  for I := 0 to ClientCount - 1 do
  begin
    if ClientCheckBoxes[I].Checked then
      Summary := Summary + '  - ' + ClientRows[I].DisplayName + ' : sera configuré' + #13#10
    else if ClientCheckBoxes[I].Enabled then
      Summary := Summary + '  - ' + ClientRows[I].DisplayName + ' : non sélectionné' + #13#10
    else
      Summary := Summary + '  - ' + ClientRows[I].DisplayName + ' : ' + ClientStatusLabels[I].Caption + #13#10;
  end;

  SummaryPage.RichEditViewer.Lines.Text := Summary;
end;

procedure InitializeWizard;
var
  I: Integer;
  RowTop: Integer;
begin
  ClientRows[0].Id := 'copilot-jetbrains'; ClientRows[0].DisplayName := 'GitHub Copilot — JetBrains / IntelliJ';
  ClientRows[1].Id := 'claude-desktop';    ClientRows[1].DisplayName := 'Claude Desktop';
  ClientRows[2].Id := 'copilot-cli';       ClientRows[2].DisplayName := 'GitHub Copilot CLI';
  ClientRows[3].Id := 'claude-code';       ClientRows[3].DisplayName := 'Claude Code';
  ClientRows[4].Id := 'codex';             ClientRows[4].DisplayName := 'OpenAI Codex';

  SetupTypePage := CreateInputOptionPage(wpWelcome,
    'Type d''installation', 'Choisissez le niveau de configuration',
    'Standard configure MORPHEUS avec des emplacements sûrs par défaut. Avancé permet de personnaliser les répertoires de données et de configuration.',
    True, False);
  SetupTypePage.Add('Standard (recommandé)');
  SetupTypePage.Add('Avancé');
  SetupTypePage.Values[0] := True;

  AdvancedRootsPage := CreateInputDirPage(wpSelectDir,
    'Emplacements avancés', 'Où MORPHEUS doit-il stocker ses données et sa configuration ?',
    'Ces emplacements sont indépendants du répertoire du programme et sont conservés lors des mises à jour et de la désinstallation.',
    False, '');
  AdvancedRootsPage.Add('Répertoire de données :');
  AdvancedRootsPage.Add('Répertoire de configuration :');
  AdvancedRootsPage.Values[0] := ExpandConstant('{localappdata}\MORPHEUS\data');
  AdvancedRootsPage.Values[1] := ExpandConstant('{localappdata}\MORPHEUS\config');

  ClientsPage := CreateCustomPage(AdvancedRootsPage.ID,
    'Clients IA', 'Connecter le MCP natif MORPHEUS à vos outils détectés');

  RowTop := 8;
  for I := 0 to ClientCount - 1 do
  begin
    ClientCheckBoxes[I] := TNewCheckBox.Create(ClientsPage);
    ClientCheckBoxes[I].Parent := ClientsPage.Surface;
    ClientCheckBoxes[I].Left := 0;
    ClientCheckBoxes[I].Top := RowTop;
    ClientCheckBoxes[I].Width := ClientsPage.SurfaceWidth;
    ClientCheckBoxes[I].Caption := ClientRows[I].DisplayName;

    ClientStatusLabels[I] := TNewStaticText.Create(ClientsPage);
    ClientStatusLabels[I].Parent := ClientsPage.Surface;
    ClientStatusLabels[I].Left := 20;
    ClientStatusLabels[I].Top := RowTop + 18;
    ClientStatusLabels[I].Width := ClientsPage.SurfaceWidth - 20;
    ClientStatusLabels[I].AutoSize := False;
    ClientStatusLabels[I].WordWrap := True;
    ClientStatusLabels[I].Caption := 'Détection en cours...';

    RowTop := RowTop + 54;
  end;

  SummaryPage := CreateOutputMsgMemoPage(ClientsPage.ID,
    'Résumé', 'Vérifiez la configuration avant l''installation',
    'Ces paramètres seront appliqués. Aucune information sensible n''est affichée.',
    '');
end;

// Runs before ssInstall: this is the sole mechanism that places MORPHEUS's program files into {app}. [Files]
// intentionally carries no direct DestDir: "{app}" copy of the payload -- the transactional engine (stage,
// verify, activate with a journal, roll back on failure, only then remove obsolete files from the previous
// version) replaces Inno's own unconditional overwrite-in-place copy.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
  Parameters: String;
  PowerShell: String;
  UpdateScriptPath: String;
  PayloadZipPath: String;
begin
  Result := '';
  NeedsRestart := False;

  // This is the fallback that guarantees detection (and therefore the Conflict/NotDetected selectability
  // guard) still runs exactly once before files are activated, in case CurPageChanged never reached the
  // custom "Clients IA" page for any reason (ShouldSkipPage logic changes, an aborted wizard, etc).
  if not DetectionHasRun then
  begin
    RunDetect;
    RefreshClientsPage;
    DetectionHasRun := True;
  end;

  // Deliberately NOT folded into the DetectionHasRun guard above: Inno still walks a silent/unattended
  // install through each wizard page's CurPageChanged internally (confirmed empirically -- it sets
  // DetectionHasRun there without ever rendering the page), so that guard is already closed by the time
  // PrepareToInstall runs. ApplyCommandLineClientSelection is idempotent and must run unconditionally for a
  // silent install regardless of which path already ran detection.
  if WizardSilent() then
    ApplyCommandLineClientSelection;

  if not FileExists(ExpandConstant('{tmp}\update-installation.ps1')) then
    ExtractTemporaryFile('update-installation.ps1');
  if not FileExists(ExpandConstant('{tmp}\morpheus-payload.zip')) then
    ExtractTemporaryFile('morpheus-payload.zip');

  UpdateScriptPath := ExpandConstant('{tmp}\update-installation.ps1');
  PayloadZipPath := ExpandConstant('{tmp}\morpheus-payload.zip');
  PowerShell := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');

  Parameters :=
    '-NoProfile -ExecutionPolicy Bypass -File "' + UpdateScriptPath + '"' +
    ' -InstallRoot "' + WizardDirValue() + '"' +
    ' -PayloadZip "' + PayloadZipPath + '"' +
    ' -Version "{#MyAppVersion}"';

  if (not Exec(PowerShell, Parameters, '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then
    Result :=
      'MORPHEUS n''a pas pu installer ses fichiers de programme (code ' + IntToStr(ResultCode) + ').' + #13#10 +
      'Aucune modification n''a été appliquée si une installation précédente existait.' + #13#10 +
      'Diagnostic : %LOCALAPPDATA%\MORPHEUS\mcp-clients.log';
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if PageID = AdvancedRootsPage.ID then
    Result := not IsAdvancedSetup();
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = ClientsPage.ID then
  begin
    RunDetect;
    RefreshClientsPage;
    DetectionHasRun := True;
  end
  else if CurPageID = SummaryPage.ID then
    RefreshSummaryPage;
end;

function NativeMcpClientSelected(): Boolean;
var
  I: Integer;
begin
  Result := False;
  for I := 0 to ClientCount - 1 do
    if ClientCheckBoxes[I].Checked then
    begin
      Result := True;
      exit;
    end;
end;

procedure ConfigureNativeMcpClients;
var
  ResultCode: Integer;
  Parameters: String;
  PowerShell: String;
begin
  if not NativeMcpClientSelected() then
    exit;

  PowerShell := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
  Parameters :=
    '-NoProfile -ExecutionPolicy Bypass -File "' +
    ExpandConstant('{app}\integration\configure-mcp-clients-setup.ps1') +
    '" -InstallRoot "' + ExpandConstant('{app}') + '"' +
    ' -DataRoot "' + GetDataRoot() + '"' +
    ' -ConfigRoot "' + GetConfigRoot() + '"' +
    BuildSmokeOverrideParameters();

  if ClientCheckBoxes[0].Checked then Parameters := Parameters + ' -CopilotJetBrains';
  if ClientCheckBoxes[1].Checked then Parameters := Parameters + ' -ClaudeDesktop';
  if ClientCheckBoxes[2].Checked then Parameters := Parameters + ' -CopilotCli';
  if ClientCheckBoxes[3].Checked then Parameters := Parameters + ' -ClaudeCode';
  if ClientCheckBoxes[4].Checked then Parameters := Parameters + ' -Codex';

  if (not Exec(PowerShell, Parameters, '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or
     (ResultCode <> 0) then
  begin
    MsgBox(
      'MORPHEUS est installé, mais une ou plusieurs intégrations MCP natives n''ont pas pu être configurées.' + #13#10 + #13#10 +
      'La CLI et le serveur MCP natif restent utilisables directement.' + #13#10 +
      'Diagnostic : %LOCALAPPDATA%\MORPHEUS\mcp-clients.log',
      mbError,
      MB_OK);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    if WizardIsTaskSelected('addtopath') then
      AddUserPath(ExpandConstant('{app}'));
    ConfigureNativeMcpClients;
  end;
end;

// Referenced from [UninstallRun] via {code:UninstallParameters}. A static Parameters string in [UninstallRun]
// cannot call BuildSmokeOverrideParameters, so a smoke build's uninstall would otherwise ignore the very
// overrides its install honored and reach for the real LocalAppData paths instead.
function UninstallParameters(Param: String): String;
begin
  Result :=
    '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\integration\configure-mcp-clients.ps1') + '"' +
    ' -InstallRoot "' + ExpandConstant('{app}') + '"' +
    ' -Action Uninstall' +
    BuildSmokeOverrideParameters();
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    RemoveUserPath(ExpandConstant('{app}'));
end;
