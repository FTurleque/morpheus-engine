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
Name: "mcp_copilot_jetbrains"; Description: "GitHub Copilot — JetBrains / IntelliJ"; GroupDescription: "Connecter le MCP natif MORPHEUS à :"; Flags: unchecked
Name: "mcp_copilot_cli"; Description: "GitHub Copilot CLI"; GroupDescription: "Connecter le MCP natif MORPHEUS à :"; Flags: unchecked
Name: "mcp_claude_code"; Description: "Claude Code"; GroupDescription: "Connecter le MCP natif MORPHEUS à :"; Flags: unchecked
Name: "mcp_claude_desktop"; Description: "Claude Desktop"; GroupDescription: "Connecter le MCP natif MORPHEUS à :"; Flags: unchecked
Name: "mcp_codex"; Description: "OpenAI Codex"; GroupDescription: "Connecter le MCP natif MORPHEUS à :"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\MORPHEUS"; Filename: "{app}\morpheus.exe"

[UninstallRun]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\integration\configure-mcp-clients.ps1"" -InstallRoot ""{app}"" -Action Uninstall"; Flags: runhidden waituntilterminated skipifdoesntexist; RunOnceId: "RemoveMorpheusNativeMcpClients"

[Code]
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

function NativeMcpClientSelected(): Boolean;
begin
  Result :=
    WizardIsTaskSelected('mcp_copilot_jetbrains') or
    WizardIsTaskSelected('mcp_copilot_cli') or
    WizardIsTaskSelected('mcp_claude_code') or
    WizardIsTaskSelected('mcp_claude_desktop') or
    WizardIsTaskSelected('mcp_codex');
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
    '" -InstallRoot "' + ExpandConstant('{app}') + '"';

  if WizardIsTaskSelected('mcp_copilot_jetbrains') then
    Parameters := Parameters + ' -CopilotJetBrains';
  if WizardIsTaskSelected('mcp_copilot_cli') then
    Parameters := Parameters + ' -CopilotCli';
  if WizardIsTaskSelected('mcp_claude_code') then
    Parameters := Parameters + ' -ClaudeCode';
  if WizardIsTaskSelected('mcp_claude_desktop') then
    Parameters := Parameters + ' -ClaudeDesktop';
  if WizardIsTaskSelected('mcp_codex') then
    Parameters := Parameters + ' -Codex';

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
