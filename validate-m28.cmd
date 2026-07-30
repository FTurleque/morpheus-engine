@echo off
setlocal
set "SCRIPT_DIR=%~dp0"

rem Prefer the inbox Windows PowerShell by absolute system path. Some developer
rem shells intentionally omit WindowsPowerShell from PATH even though the host
rem executable is present and usable.
set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if exist "%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe" set "POWERSHELL_EXE=%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe"

rem Fall back to PowerShell 7 only when the inbox executable is genuinely absent.
if not exist "%POWERSHELL_EXE%" (
  where pwsh.exe >nul 2>&1
  if errorlevel 1 (
    echo Neither Windows PowerShell nor PowerShell 7 could be resolved.
    echo Expected: %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe
    exit /b 9009
  )
  set "POWERSHELL_EXE=pwsh.exe"
)

"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%scripts\validate-m28.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo M28 validation FAILED with exit code %EXIT_CODE%.
  exit /b %EXIT_CODE%
)

echo.
echo M28 validation PASSED.
exit /b 0
