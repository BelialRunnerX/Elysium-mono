# Launch the dev client on the installed jar if it is not up, and wait until the player has joined.
#
#     powershell -NoProfile -ExecutionPolicy Bypass -File tools\livetest\stages\client_up.ps1
#
# The client is the one under ELYSIUM_TEST_ROOT (default C:\testingbuilds\Elysium). It starts
# elysium-run-e.ps1 -NoInstall beside this script (minimised, so this one returns), then waits up
# to about eight minutes for "joined the game" in the client's logs\latest.log. A client already
# in its world is left alone. Install a new build first with tools/livetest/install.sh.
#
# The launcher opens the world saves\ElysiumBoot. When the client has none (a rebuilt rig starts
# with an empty saves folder) it also passes -FreshWorld, and the launcher builds the world from
# tools\livetest\stages\reference (the test server's level.dat, run through StripPlayer.java, and
# its serverconfig) before it launches. A world that exists is never rebuilt from here.
#
# It came back from the session transcript on 14 October 2026: it was the scratchpad's
# obtain/client_up.ps1 (written 2 October 04:12 UTC), lost when Windows emptied %TEMP% on
# 11 October; the launcher it starts is now the repository's own copy beside it.
$root = if ($env:ELYSIUM_TEST_ROOT) { $env:ELYSIUM_TEST_ROOT } else { 'C:\testingbuilds' }
$log = [System.IO.Path]::Combine($root, 'Elysium', 'logs', 'latest.log')
$up = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' -and $_.MainWindowTitle -notlike '*Loading*' }
if ($up) { "client already up"; exit 0 }
$launcher = Join-Path $PSScriptRoot 'elysium-run-e.ps1'
$launch = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $launcher), '-NoInstall')
$world = [System.IO.Path]::Combine($root, 'Elysium', 'saves', 'ElysiumBoot')
if (-not (Test-Path $world)) { $launch += '-FreshWorld'; "no $world yet: the launcher builds it from the reference world" }
Start-Process powershell -ArgumentList $launch -WindowStyle Minimized
Start-Sleep -Seconds 100; $n = 100
do {
    $p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' -and $_.MainWindowTitle -notlike '*Loading*' -and $_.StartTime -gt (Get-Date).AddMinutes(-10) }
    $joined = $p -and (Test-Path $log) -and ((Get-Item $log).LastWriteTime -gt (Get-Date).AddMinutes(-2)) -and (Select-String -Path $log -Pattern 'joined the game' -Quiet)
    if (-not $joined) { Start-Sleep -Seconds 5; $n += 5 }
} until ($joined -or $n -ge 500)
"client ready after ${n}s: $joined"; Start-Sleep -Seconds 8
