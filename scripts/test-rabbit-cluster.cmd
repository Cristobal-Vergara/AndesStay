@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-rabbit-cluster.ps1"
exit /b %errorlevel%
