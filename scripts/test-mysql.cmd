@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-mysql.ps1"
exit /b %errorlevel%
