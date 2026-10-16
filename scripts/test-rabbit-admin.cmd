@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-rabbit-admin.ps1"
exit /b %errorlevel%
