. "$PSScriptRoot\mc.ps1"
# Player-side checks of the merged gap work: Cordon drops from loot, found trinkets, a logistics
# importer at work, and a fishing rod that catches. Runs in the E client, creative.
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function ChatSince($n) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Cmd '/gamemode creative'; Start-Sleep -Milliseconds 500
MC-Cmd '/time set 1000'; Start-Sleep -Milliseconds 300
MC-Cmd '/weather clear'; Start-Sleep -Milliseconds 300
# a flat stage in the sky, out of everybody's way
MC-Cmd '/tp @s 100.5 201 100.5 0 10'; Start-Sleep -Seconds 4
MC-Cmd '/fill 90 199 90 120 199 120 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 90 200 90 120 210 120 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 400

"== Cordon drops and found trinkets from loot"
$n = (LogLines).Count
foreach ($i in 1..40) { MC-Cmd '/loot give @s loot minecraft:chests/bastion_treasure' }
MC-Cmd '/clear @s elysiumores:ember_slag 0'; Start-Sleep -Milliseconds 500
foreach ($i in 1..40) { MC-Cmd '/loot give @s loot minecraft:chests/ancient_city' }
MC-Cmd '/clear @s elysiumores:blight_crystal 0'; Start-Sleep -Milliseconds 500
MC-Cmd '/clear @s elysiumtrinkets:mote 0'; Start-Sleep -Milliseconds 300
MC-Cmd '/tp @s 100.5 201 100.5 0 10'; Start-Sleep -Seconds 2
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\feat_1_loot_inventory.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 500
"loot chat:"; ChatSince $n | Select-String 'Removed|Dropped|No items' | Select-Object -Last 6
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 400

"== logistics: an importer empties a chest into the network"
$n = (LogLines).Count
MC-Cmd '/setblock 100 200 105 elysiumlogistics:controller'; Start-Sleep -Milliseconds 400
MC-Cmd '/setblock 101 200 105 elysiumlogistics:bus'; Start-Sleep -Milliseconds 400
MC-Cmd '/setblock 102 200 105 elysiumlogistics:timber_drawer'; Start-Sleep -Milliseconds 400
MC-Cmd '/setblock 99 200 105 elysiumlogistics:importer[facing=west]'; Start-Sleep -Milliseconds 400
MC-Cmd '/setblock 98 200 105 minecraft:chest'; Start-Sleep -Milliseconds 400
MC-Cmd '/item replace block 98 200 105 container.0 with minecraft:cobblestone 64'; Start-Sleep -Milliseconds 300
MC-Cmd '/item replace block 98 200 105 container.1 with minecraft:cobblestone 64'; Start-Sleep -Milliseconds 300
MC-Cmd '/give @s minecraft:cobblestone 1'; Start-Sleep -Milliseconds 400
MC-Cmd '/tp @s 99.5 201 103.5 0 25'; Start-Sleep -Seconds 3
# set the importer's filter with the cobblestone in hand, then wait for it to work
MC-RClick; Start-Sleep -Seconds 1
MC-Shot "$PSScriptRoot\feat_2_importer_set.png" 0.5
Start-Sleep -Seconds 12
MC-Cmd '/data get block 98 200 105 Items'; Start-Sleep -Seconds 2
MC-Cmd '/data get block 102 200 105'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\feat_3_importer_after.png" 0.5
"logistics chat:"; ChatSince $n | Select-String 'has the following|Items|filter|imports|Importer|Changed' | Select-Object -Last 6 | ForEach-Object { $_.ToString().Substring(0, [Math]::Min(220, $_.ToString().Length)) }

"== fishing: a rod that catches"
$n = (LogLines).Count
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 300
MC-Cmd '/fill 105 199 92 115 199 98 minecraft:water'; Start-Sleep -Milliseconds 600
MC-Cmd '/give @s elysiumagronomy:reed_rod'; Start-Sleep -Milliseconds 500
MC-Cmd '/tp @s 110.5 201 100.5 180 30'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\feat_4_cast.png" 0.5
Start-Sleep -Seconds 28
MC-RClick; Start-Sleep -Seconds 3
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\feat_5_after_fishing.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 500
"fishing log:"; (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'fish|Fish|catch|reel' | Select-Object -Last 5 | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length), [Math]::Min(180, $_.Line.Length - [Math]::Min(95, $_.Line.Length))) }
"features stage done"
