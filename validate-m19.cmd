@echo off
setlocal
cd /d "%~dp0"

set "WINPS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if exist "%WINPS%" goto run_winps

where pwsh.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 goto run_pwsh

echo ERROR: no PowerShell host found.
echo Checked: %WINPS% and pwsh.exe on PATH.
exit /b 9009

:run_winps
"%WINPS%" -NoProfile -ExecutionPolicy Bypass -File ".\scripts\validate-m19.ps1" %*
exit /b %ERRORLEVEL%

:run_pwsh
pwsh.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\validate-m19.ps1" %*
exit /b %ERRORLEVEL%
