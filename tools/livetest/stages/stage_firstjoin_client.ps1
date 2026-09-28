. "$PSScriptRoot\mc.ps1"
# First join (POLISH.md P3): a fresh world, what a new player sees, the declaration
# screen (ESC refused before Confirm, a choice recorded, the tabbed sheet after), the
# kit, the character commands, and a death in deep water keeping its grave.
# Needs elysium-run-e.ps1 beside mc.ps1 (the launcher with -FreshWorld).
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { "$(Get-Date -Format HH:mm:ss) $s" }

Say "== a fresh world, joined as a new player"
& "$PSScriptRoot\elysium-run-e.ps1" -NoInstall -FreshWorld -TimeoutSec 600 | Select-Object -Last 2
Start-Sleep -Seconds 2
MC-Place 0 0 1600 900
MC-Shot "$Out\firstjoin_1_on_join.png" 0.5
"hotbar on join: codex, almanac, bread, torches (see the shot)"

Say "== the declaration screen: ESC refused, a choice, Confirm"
MC-Focus | Out-Null
Start-Sleep -Milliseconds 500
MC-Key 'ESC'; Start-Sleep -Seconds 1
MC-Shot "$Out\firstjoin_2_after_esc.png" 0.5
MC-Click 346 330; Start-Sleep -Milliseconds 700      # Imperial
MC-Click 732 642; Start-Sleep -Milliseconds 700      # Marksman
MC-Shot "$Out\firstjoin_3_chosen.png" 0.5
MC-Click 1230 716; Start-Sleep -Seconds 2            # Confirm
MC-Shot "$Out\firstjoin_4_confirmed.png" 0.5
$n = (LogLines).Count
MC-CmdOnce '/time query daytime'; Start-Sleep -Seconds 2
"the world answers once the screen is gone:"; Chat $n 'time is|Recorded'

Say "== safe, on land"
MC-Cmd '/gamemode creative'
MC-Cmd '/kill @e[type=!player,distance=..64]'
$n = (LogLines).Count
MC-Cmd '/locate biome minecraft:plains'; Start-Sleep -Seconds 2
$found = Chat $n 'nearest' | Select-Object -Last 1
$m = [regex]::Match("$found", '\[(-?\d+), ~?-?\d*, (-?\d+)\]')
if ($m.Success) { MC-Cmd "/spreadplayers $($m.Groups[1].Value) $($m.Groups[2].Value) 0 8 false @s"; Start-Sleep -Seconds 5 }
MC-Cmd '/spawnpoint @s ~ ~ ~'
MC-Cmd '/time set 6000'
MC-Cmd '/kill @e[type=!player,distance=..64]'

Say "== the kit"
$n = (LogLines).Count
foreach ($id in 'elysium:imperial_codex', 'elysiumalmanac:almanac', 'minecraft:bread', 'minecraft:torch') {
  MC-Cmd "/execute if entity @s[nbt={Inventory:[{id:""$id""}]}]"; Start-Sleep -Milliseconds 600
}
"codex, almanac, bread, torch in the inventory (a kit lost to a death is in its grave):"; Chat $n 'Test'

Say "== the character commands, and the sheet after a choice"
$n = (LogLines).Count
MC-Cmd '/elysium stats'; Start-Sleep -Seconds 2
MC-Cmd '/elysium standing'; Start-Sleep -Seconds 2
MC-Cmd '/elysium tier'; Start-Sleep -Seconds 2
MC-Shot "$Out\firstjoin_5_commands.png" 0.5
"chat:"; Chat $n '.' | Select-Object -Last 14
MC-CmdOnce '/elysium sheet'; Start-Sleep -Seconds 2
MC-Shot "$Out\firstjoin_6_sheet_after.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
$n = (LogLines).Count
MC-Cmd '/time query daytime'
"the sheet closes on ESC once chosen:"; Chat $n 'time is'

Say "== a death in deep water keeps its grave"
MC-Cmd '/gamerule doImmediateRespawn true'
MC-Cmd '/execute in minecraft:overworld run tp @s 64 52 -96'; Start-Sleep -Seconds 5
MC-Cmd '/clear @s'
MC-Cmd '/give @s minecraft:gold_ingot 3'
MC-Cmd '/gamemode survival'
$n = (LogLines).Count
MC-Cmd '/kill @s'; Start-Sleep -Seconds 5
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 64 52 -96'; Start-Sleep -Seconds 5
$said = Chat $n 'Your things are at' | Select-Object -Last 1
"$said"
$m = [regex]::Match("$said", 'at (-?\d+), (-?\d+), (-?\d+)')
if ($m.Success) {
  $gx = $m.Groups[1].Value; $gy = $m.Groups[2].Value; $gz = $m.Groups[3].Value
  $n = (LogLines).Count
  MC-Cmd "/data get block $gx $gy $gz Held"; Start-Sleep -Milliseconds 800
  MC-Cmd '/execute if entity @e[type=minecraft:item,distance=..20]'; Start-Sleep -Milliseconds 800
  "the grave's Held, and items floating (none):"; Chat $n 'block data|not a block|Test'
  MC-Shot "$Out\firstjoin_7_water_grave.png" 0.5
}
MC-Cmd '/gamerule doImmediateRespawn false'
"first-join stage done"
