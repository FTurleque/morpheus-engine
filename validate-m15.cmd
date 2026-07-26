@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\validate-m15.ps1" %*
exit /b %ERRORLEVEL%
