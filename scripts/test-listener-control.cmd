@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-listener-control.ps1"
exit /b %errorlevel%
