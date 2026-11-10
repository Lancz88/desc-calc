Option Explicit
Dim shell, files, root, command
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
root = files.GetParentFolderName(WScript.ScriptFullName)
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & root & "\Start-GlidePath.ps1"""
If WScript.Arguments.Count > 0 Then
    If LCase(WScript.Arguments(0)) = "stop" Then command = command & " -Stop"
End If
shell.Run command, 0, False
