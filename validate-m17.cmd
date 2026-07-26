@echo off
setlocal
cd /d "%~dp0"

set "WINPS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if exist "%WINPS%" (
    "%WINPS%" -NoProfile -ExecutionPolicy Bypass -File ".\scripts\validate-m17.ps1" %*
    exit /b %ERRORLEVEL%
)

where pwsh.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\validate-m17.ps1" %*
    exit /b %ERRORLEVEL%
)

echo ERROR: no PowerShell host found.
echo Checked: %WINPS% and pwsh.exe on PATH.
exit /b 9009
