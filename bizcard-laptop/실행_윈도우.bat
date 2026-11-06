@echo off
title Yeongil Sales Helper (laptop)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server.ps1"
echo.
echo [Server stopped] See server-log.txt
pause
