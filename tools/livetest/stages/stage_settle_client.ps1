. "$PSScriptRoot\mc.ps1"
# Player-side test of the self-founding colonies, in the E client's fresh world.
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function WaitFor([string]$pattern, [int]$seconds, [int]$from) {
    for ($i = 0; $i -lt $seconds; $i++) {
        $l = LogLines; if ($l.Count -gt $from) { $m = $l[$from..($l.Count - 1)] | Select-String $pattern | Select-Object -First 1; if ($m) { return $m.Line } }
        Start-Sleep -Seconds 1
    }
    return $null
}
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Shot "$PSScriptRoot\settle_0_state.png" 0.5
$n0 = (LogLines).Count
MC-Cmd '/gamemode creative'; Start-Sleep -Milliseconds 600
MC-Cmd '/time set 1000'; Start-Sleep -Milliseconds 400
MC-Cmd '/weather clear'; Start-Sleep -Milliseconds 400
$ox = if ($args.Count -ge 2) { [int]$args[0] } else { 4000 }; $oz = if ($args.Count -ge 2) { [int]$args[1] } else { 4000 }
MC-Cmd "/execute positioned $ox 64 $oz run locate structure elysiumcolonies:settlement"; Start-Sleep -Seconds 6
$found = WaitFor 'nearest elysiumcolonies:settlement is at \[(-?\d+), ~, (-?\d+)\]' 20 $n0
if (-not $found) { "NO SITE: $((LogLines)[-3..-1] -join ' | ')"; MC-Shot "$PSScriptRoot\settle_nosite.png" 0.5; exit 1 }
$m = [regex]::Match($found, '\[(-?\d+), ~, (-?\d+)\]'); $x = [int]$m.Groups[1].Value; $z = [int]$m.Groups[2].Value
"site at $x, $z"
$n1 = (LogLines).Count
MC-Cmd "/tp @s $x 200 $z"; Start-Sleep -Seconds 12
MC-Cmd "/execute positioned $x 0 $z positioned over motion_blocking_no_leaves run tp @s ~ ~2 ~ 0 20"; Start-Sleep -Seconds 6
MC-Shot "$PSScriptRoot\settle_1_site.png" 0.5
# the stake ticks at random once the chunk ticks; speed it along
MC-Cmd '/gamerule randomTickSpeed 40'; Start-Sleep -Seconds 1
$line = WaitFor 'founded itself|no settlement at|refused to found' 180 $n1
"founding: $line"
MC-Cmd '/gamerule randomTickSpeed 3'; Start-Sleep -Seconds 4
MC-Cmd "/tp @s $x 190 $z"; Start-Sleep -Seconds 4
MC-Cmd "/tp @s ~ ~ ~ 30 60"; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\settle_2_after.png" 0.5
MC-Cmd "/execute positioned $x 0 $z positioned over motion_blocking_no_leaves run tp @s ~-6 ~1 ~-10 30 8"; Start-Sleep -Seconds 5
MC-Shot "$PSScriptRoot\settle_3_ground.png" 0.5
MC-Cmd '/elysium colony natural'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\settle_4_natural.png" 0.5
MC-Cmd '/elysium colony'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\settle_5_report.png" 0.5
MC-Cmd '/elysium colony claim'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\settle_6_claim.png" 0.5
MC-Cmd '/elysium colony'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\settle_7_owned.png" 0.5
"chat since:"; (LogLines)[$n1..((LogLines).Count - 1)] | Select-String 'CHAT\]|founded|ElysiumColonies' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } | Select-Object -Last 25
"settle client stage done"
