@echo off
title Yeongil Sales Helper - Install
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
if errorlevel 1 (
  echo.
  echo [Install failed] Please send a screenshot of this window.
  pause
)
