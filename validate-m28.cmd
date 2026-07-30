@echo off
setlocal
set "SCRIPT_DIR=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%scripts\validate-m28.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo M28 validation FAILED with exit code %EXIT_CODE%.
  exit /b %EXIT_CODE%
)

echo.
echo M28 validation PASSED.
exit /b 0
