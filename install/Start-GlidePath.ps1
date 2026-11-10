[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [switch]$NoBrowser,
    [switch]$SkipBuild,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $PSScriptRoot) 'desc-calc'))
$runtimeFolder = Join-Path $projectRoot '.glidepath'
$url = "http://127.0.0.1:$Port/"
$lock = $null
$locked = $false
$app = $null
$ready = $false

function Test-GlidePath {
    try {
        $page = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
        return $page.StatusCode -eq 200 -and $page.Content.Contains('<title>GlidePath: Home</title>')
    } catch { return $false }
}

function Test-PortInUse {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch { return $false } finally { $client.Dispose() }
}

try {
    New-Item -ItemType Directory -Force -Path $runtimeFolder | Out-Null
    # Serialize double-clicks across launchers for this user's local port.
    $lock = New-Object Threading.Mutex($false, "Local\GlidePath-Launcher-$Port")
    try { $locked = $lock.WaitOne([TimeSpan]::FromMinutes(10)) }
    catch [Threading.AbandonedMutexException] { $locked = $true }
    if (-not $locked) { throw 'Another GlidePath launch is still in progress.' }

    if ($Stop) {
        $stateFile = Join-Path $runtimeFolder "app-$Port.json"
        if (Test-Path -LiteralPath $stateFile) {
            $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
            $running = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
            # A stale PID file must never terminate an unrelated, newer process.
            if ($running -and $running.StartTime.ToUniversalTime().Ticks.ToString() -eq $state.startedUtcTicks `
                -and $running.Path -eq $state.java) {
                Stop-Process -Id $running.Id
                $running.WaitForExit(10000) | Out-Null
            }
        }
        exit 0
    }

    if (Test-GlidePath) {
        if (-not $NoBrowser) { Start-Process $url }
        exit 0
    }
    if (Test-PortInUse) { throw "Port $Port is already in use by another application. Close it before starting GlidePath." }

    $java = $null
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate) { $java = $candidate }
    }
    if (-not $java) { $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source }
    if (-not $java) { throw 'Install JDK 21 and set JAVA_HOME or add its bin folder to PATH.' }
    $javaBin = Split-Path -Parent $java
    if (-not (Test-Path -LiteralPath (Join-Path $javaBin 'javac.exe'))) {
        throw 'Building GlidePath requires a JDK, not only a Java runtime. Install JDK 21.'
    }
    $env:JAVA_HOME = Split-Path -Parent $javaBin
    $env:PATH = "$javaBin;$env:PATH"

    Push-Location -LiteralPath $projectRoot
    try {
        if (-not $SkipBuild) {
            $buildLog = Join-Path $runtimeFolder 'build.log'
            # Native Gradle diagnostics are captured, including stderr, even in hidden mode.
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & (Join-Path $projectRoot 'gradlew.bat') --console=plain bootJar *> $buildLog
                $buildExit = $LASTEXITCODE
            } finally { $ErrorActionPreference = $previousPreference }
            if ($buildExit -ne 0) { throw "Build failed. See $buildLog" }
        }
        $jars = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter '*.jar' |
            Where-Object { $_.Name -notlike '*-plain.jar' })
        if ($jars.Count -ne 1) { throw 'Expected one executable JAR in build/libs. Run gradlew.bat clean bootJar.' }
        $app = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jars[0].FullName + '"'), "--server.port=$Port", '--server.address=127.0.0.1') `
            -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $runtimeFolder "app-$Port.log") `
            -RedirectStandardError (Join-Path $runtimeFolder "app-$Port-error.log")
        Set-Content -LiteralPath (Join-Path $runtimeFolder "app-$Port.pid") -Value $app.Id
        @{ pid = $app.Id; startedUtcTicks = $app.StartTime.ToUniversalTime().Ticks.ToString(); java = $java } |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeFolder "app-$Port.json")
        $deadline = [DateTime]::UtcNow.AddSeconds(90)
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($app.HasExited) { throw "GlidePath exited during startup. See $runtimeFolder\app-$Port-error.log" }
            if (Test-GlidePath) { $ready = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw "GlidePath did not become ready within 90 seconds. See $runtimeFolder\app-$Port.log" }
    } finally { Pop-Location }
    if (-not $NoBrowser) { Start-Process $url }
} catch {
    if ($app -and -not $ready -and -not $app.HasExited) { Stop-Process -Id $app.Id -ErrorAction SilentlyContinue }
    $message = $_.Exception.Message
    if (Test-Path -LiteralPath $runtimeFolder) {
        Set-Content -LiteralPath (Join-Path $runtimeFolder 'launcher-error.log') -Value $message
    }
    if ($NoBrowser) { Write-Error $message -ErrorAction Continue }
    else {
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.MessageBox]::Show($message, 'GlidePath', 'OK', 'Error') | Out-Null
    }
    exit 1
} finally {
    if ($locked) { $lock.ReleaseMutex() }
    if ($lock) { $lock.Dispose() }
}
