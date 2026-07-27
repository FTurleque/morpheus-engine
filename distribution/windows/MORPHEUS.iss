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
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
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
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\MORPHEUS"; Filename: "{app}\morpheus.exe"

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

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and WizardIsTaskSelected('addtopath') then
    AddUserPath(ExpandConstant('{app}'));
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    RemoveUserPath(ExpandConstant('{app}'));
end;
