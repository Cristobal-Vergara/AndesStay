@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-rabbit-benchmark.ps1" %*
exit /b %errorlevel%
