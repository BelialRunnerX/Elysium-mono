# Install a chosen elysium-all jar into the dev client under ELYSIUM_TEST_ROOT (default C:\testingbuilds\Elysium), optionally recreate the ElysiumBoot world from the
# reference level.dat, launch straight into it, and watch the log until the player joins / crash / exit.
param([int]$TimeoutSec = 420, [string]$Jar = '', [switch]$NoInstall, [switch]$FreshWorld)
$root = if ($env:ELYSIUM_TEST_ROOT) { $env:ELYSIUM_TEST_ROOT } else { 'C:\testingbuilds' }
$inst = [System.IO.Path]::Combine($root, 'Elysium')
# the repository's own dist, three folders up from tools\livetest\stages; -Jar defaults to its elysium-all
$dist = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\dist'))
if (-not $Jar) { $Jar = Join-Path $dist 'elysium-all-1.0.0.jar' }
$javaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

# stop any previous run of ours and wait until it is really gone (log4j otherwise appends two processes into one log)
if (Test-Path "$inst\logs\launch.pid") {
    $old = Get-Content "$inst\logs\launch.pid"
    $p = Get-Process -Id $old -ErrorAction SilentlyContinue
    if ($p -and $p.ProcessName -eq 'java') { Stop-Process -Id $old -Force -ErrorAction SilentlyContinue; Wait-Process -Id $old -Timeout 30 -ErrorAction SilentlyContinue; Write-Host "stopped previous java $old" }
}
Get-Process java -ErrorAction SilentlyContinue | ? { $_.MainWindowTitle -like 'Minecraft*' } | % { Stop-Process -Id $_.Id -Force; Wait-Process -Id $_.Id -Timeout 30 -ErrorAction SilentlyContinue; Write-Host "stopped stray Minecraft java $($_.Id)" }
Start-Sleep -Seconds 2

if (-not $NoInstall) {
    Get-ChildItem "$inst\mods" -Filter 'elysium*.jar' | Remove-Item -Force
    Copy-Item $Jar "$inst\mods\elysium-all-1.0.0.jar"
    Copy-Item "$dist\elysiumcolonies-1.0.0.jar" "$inst\mods\"
}
Write-Host "mods: $((Get-ChildItem "$inst\mods" -Filter 'elysium*.jar' | % { "$($_.Name) ($([int]($_.Length/1KB)) KB)" }) -join ', ')"

if ($FreshWorld) {
    $world = "$inst\saves\ElysiumBoot"
    if (Test-Path $world) { Remove-Item $world -Recurse -Force }
    New-Item -ItemType Directory -Force $world | Out-Null
    # The reference world: the dedicated test server's own level.dat and serverconfig,
    # saved from a world the seven-jar pack generated under the current wilds preset
    # (POLISH.md P4); no Valoria dimension, no retired mod's config, the game's own spawn.
    $ref = Join-Path $PSScriptRoot 'reference'
    foreach ($d in 'datapacks', 'serverconfig') { if (Test-Path "$ref\$d") { Copy-Item "$ref\$d" $world -Recurse -Force } }
    $lib = 'C:\Users\weaks\curseforge\minecraft\Install\libraries'
    $last = (Get-Content 'C:\Users\weaks\curseforge\minecraft\Instances\Claude\logs\instance_audit.txt' | ? { $_ -match 'Launch attempted' })[-1]
    $cpL = ([regex]::Match($last, ' -cp (\S+) ').Groups[1].Value -split ';') | ? { $_ -notmatch '\\versions\\' }
    $cp = (@("$lib\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar") + $cpL) -join ';'
    & "$javaHome\bin\java.exe" -cp $cp "$PSScriptRoot\StripPlayer.java" "$ref\level.dat" "$world\level.dat" 'ElysiumBoot' | % { Write-Host "world: $_" }
}

Remove-Item "$inst\logs\latest.log", "$inst\logs\debug.log" -Force -ErrorAction SilentlyContinue
Get-ChildItem "$inst\crash-reports" -File -ErrorAction SilentlyContinue | Remove-Item -Force
& "$inst\launch.ps1" -World ElysiumBoot
$pid2 = [int](Get-Content "$inst\logs\launch.pid")
$log = "$inst\logs\latest.log"
$sw = [Diagnostics.Stopwatch]::StartNew(); $state = 'starting'; $seen = @{}
while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
    Start-Sleep -Seconds 5
    $alive = Get-Process -Id $pid2 -ErrorAction SilentlyContinue
    if (Test-Path $log) {
        $lines = Get-Content $log -ErrorAction SilentlyContinue
        foreach ($l in $lines) {
            if ($l -match 'joined the game|logged in with entity id|Preparing spawn area: 100|Mod loading has failed|Failed to load registries|Encountered an unexpected exception|Crash report saved|Loaded [0-9]+ recipes|Elysium Library ready|Failed to decode key') {
                $k = $l.Substring([Math]::Min(35, $l.Length)); if (-not $seen.ContainsKey($k)) { $seen[$k] = 1; Write-Host ("[{0,3}s] {1}" -f [int]$sw.Elapsed.TotalSeconds, $l.Substring(0, [Math]::Min(180, $l.Length))) }
            }
        }
        if ($lines -match 'joined the game') { $state = 'JOINED'; break }
    }
    if (-not $alive) { $state = 'EXITED'; break }
}
if ($state -eq 'starting') { $state = 'TIMEOUT' }
Write-Host "STATE: $state after $([int]$sw.Elapsed.TotalSeconds)s (java alive: $([bool](Get-Process -Id $pid2 -ErrorAction SilentlyContinue)))"
$crash = Get-ChildItem "$inst\crash-reports" -File -ErrorAction SilentlyContinue | Sort LastWriteTime -Desc | Select -First 1
if ($crash) { Write-Host "=== crash report $($crash.Name)"; Get-Content $crash.FullName -TotalCount 45 | Select -Skip 5 }
if ($state -ne 'JOINED') {
    Write-Host '=== latest.log ERROR/FATAL tail'; if (Test-Path $log) { Get-Content $log | ? { $_ -match '/ERROR\]|/FATAL\]' -and $_ -notmatch 'applyMask|Cowardly|Missing sound|Invalid path|Failed to load model|Couldn.t parse element' } | Select -Last 30 | % { $_.Substring(0, [Math]::Min(260, $_.Length)) } }
    Write-Host '=== stderr'; Get-Content "$inst\logs\launch-stderr.txt" -Tail 15 -ErrorAction SilentlyContinue
} else {
    Start-Sleep -Seconds 20   # let the first autosave / dimension folders appear
    Write-Host "decode errors: $((Select-String -Path $log -Pattern 'Failed to decode key' | Measure-Object).Count)"
    Write-Host "dimension folders: $(((Get-ChildItem "$inst\saves\ElysiumBoot\dimensions" -Directory -ErrorAction SilentlyContinue | % { Get-ChildItem $_.FullName -Directory | % { "$($_.Parent.Name)/$($_.Name)" } }) -join ', '))"
}
