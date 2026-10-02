. "$PSScriptRoot\mc.ps1"
# Skip days at Dunfield so the town raises its hall and huts by itself, and photograph it.
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
$x = if ($args.Count -ge 2) { [int]$args[0] } else { 4837 }; $z = if ($args.Count -ge 2) { [int]$args[1] } else { -5051 }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Cmd '/gamemode creative'
MC-Cmd '/weather clear'
MC-Cmd "/tp @s $x 120 $z 0 60"; Start-Sleep -Seconds 12
$n = (LogLines).Count
# a MineColonies day turns over when night falls, so toggle night and day
foreach ($d in 1..4) {
  MC-Cmd '/time set 14000'; Start-Sleep -Seconds 12
  MC-Cmd '/time set 1000'; Start-Sleep -Seconds 12
  $done = (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'completed .* by itself|laid out .* by itself' | ForEach-Object { $_.Line.Substring([Math]::Min(60, $_.Line.Length)) }
  "after day $d :"; $done | Select-Object -Last 4
}
MC-Cmd "/tp @s $x 92 $z 20 30"; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\days_1_hall.png" 0.5
MC-Cmd "/tp @s ~-12 ~ ~-14 -40 25"; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\days_2_around.png" 0.5
MC-Cmd "/tp @s $x 110 $z 0 70"; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\days_3_above.png" 0.5
MC-Cmd '/elysium colony'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\days_4_report.png" 0.5
"colony log:"; (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'ElysiumColonies|by itself' | ForEach-Object { $_.Line.Substring([Math]::Min(60, $_.Line.Length)) } | Select-Object -Last 12
"days stage done"
