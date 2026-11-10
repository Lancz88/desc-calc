[CmdletBinding()]
param([string]$ShortcutDirectory = [Environment]::GetFolderPath('Programs'))

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $PSScriptRoot) 'desc-calc'))
if (-not $ShortcutDirectory) { throw 'The Windows Start menu folder could not be located.' }
New-Item -ItemType Directory -Force -Path $ShortcutDirectory | Out-Null
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut((Join-Path $ShortcutDirectory 'GlidePath.lnk'))
$shortcut.TargetPath = Join-Path $env:SystemRoot 'System32/wscript.exe'
$shortcut.Arguments = '"' + (Join-Path $PSScriptRoot 'GlidePath.vbs') + '"'
$shortcut.WorkingDirectory = $projectRoot
$shortcut.IconLocation = (Join-Path $PSScriptRoot 'glidepath.ico') + ',0'
$shortcut.Description = 'GlidePath - flight plan and descent planning'
$shortcut.WindowStyle = 7
$shortcut.Save()
$stopShortcut = $shell.CreateShortcut((Join-Path $ShortcutDirectory 'Stop GlidePath.lnk'))
$stopShortcut.TargetPath = $shortcut.TargetPath
$stopShortcut.Arguments = $shortcut.Arguments + ' stop'
$stopShortcut.WorkingDirectory = $projectRoot
$stopShortcut.IconLocation = $shortcut.IconLocation
$stopShortcut.Description = 'Stop the GlidePath application running in the background'
$stopShortcut.WindowStyle = 7
$stopShortcut.Save()
Write-Host "GlidePath shortcut created in: $ShortcutDirectory"
