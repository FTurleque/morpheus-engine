@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."

rem Canonical dispatcher: scripts\validate.ps1
set "DISPATCHER=%SCRIPT_DIR%validate.ps1"
set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if exist "%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe" set "POWERSHELL_EXE=%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe"

if not exist "%DISPATCHER%" (
    echo ERROR: validation dispatcher not found: %DISPATCHER%
    exit /b 2
)

if not exist "%POWERSHELL_EXE%" (
    where pwsh.exe >nul 2>&1
    if errorlevel 1 (
        echo ERROR: neither Windows PowerShell nor PowerShell 7 could be resolved.
        echo Expected: %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe
        exit /b 9009
    )
    set "POWERSHELL_EXE=pwsh.exe"
)

"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%DISPATCHER%" %*
exit /b %ERRORLEVEL%
