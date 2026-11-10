@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-GlidePath.ps1"
if errorlevel 1 (
    echo GlidePath shortcut installation failed.
    pause
    exit /b 1
)
echo You can now find GlidePath in the Windows Start menu.
pause
