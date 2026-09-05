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
AppId={{4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
VersionInfoVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\MORPHEUS
DefaultGroupName=MORPHEUS
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
OutputBaseFilename=MORPHEUS-{#MyAppVersion}-windows-x64-setup
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
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\integration\configure-mcp-clients.ps1"" -InstallRoot ""{app}"" -Action Uninstall"; Flags: runhidden waituntilterminated skipifdoesntexist; RunOnceId: "RemoveMorpheusNativeMcpClients"

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

function GetDataRoot(): String;
begin
  if IsAdvancedSetup() then
    Result := AdvancedRootsPage.Values[0]
  else
    Result := ExpandConstant('{localappdata}\MORPHEUS\data');
end;

function GetConfigRoot(): String;
begin
  if IsAdvancedSetup() then
    Result := AdvancedRootsPage.Values[1]
  else
    Result := ExpandConstant('{localappdata}\MORPHEUS\config');
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
    ' -Out "' + DetectReportPath + '"';

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
    ' -ConfigRoot "' + GetConfigRoot() + '"';

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

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    RemoveUserPath(ExpandConstant('{app}'));
end;
