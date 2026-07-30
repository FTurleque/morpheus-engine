@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."

set "DISPATCHER=.\scripts\validate.ps1"
set "WINPS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if not exist "%DISPATCHER%" (
    echo ERROR: validation dispatcher not found: %DISPATCHER%
    exit /b 2
)

if exist "%WINPS%" (
    "%WINPS%" -NoProfile -ExecutionPolicy Bypass -File "%DISPATCHER%" %*
    exit /b %ERRORLEVEL%
)

where pwsh.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%DISPATCHER%" %*
    exit /b %ERRORLEVEL%
)

echo ERROR: no PowerShell host found.
echo Checked: %WINPS% and pwsh.exe on PATH.
exit /b 9009
