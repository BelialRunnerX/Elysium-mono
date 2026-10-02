. "$PSScriptRoot\mc.ps1"
# Player-side checks of the kitchen and the pots, in the E client, creative.
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function ChatSince($n) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } }
function Hold([string]$item) { MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 300; MC-Cmd "/give @s $item"; Start-Sleep -Milliseconds 500 }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Cmd '/gamemode creative'; Start-Sleep -Milliseconds 400
MC-Cmd '/time set 1000'; Start-Sleep -Milliseconds 300
MC-Cmd '/tp @s 200.5 201 200.5 0 20'; Start-Sleep -Seconds 4
MC-Cmd '/fill 190 199 190 220 199 220 minecraft:smooth_stone'; Start-Sleep -Milliseconds 800
MC-Cmd '/fill 190 200 190 220 210 220 minecraft:air'; Start-Sleep -Milliseconds 800

"== skillet: bacon and eggs over a campfire (240 ticks)"
$n = (LogLines).Count
MC-Cmd '/setblock 200 200 204 minecraft:campfire'; Start-Sleep -Milliseconds 400
MC-Cmd '/setblock 200 201 204 elysiumagronomy:skillet'; Start-Sleep -Milliseconds 400
MC-Cmd '/tp @s 200.5 201 201.5 0 25'; Start-Sleep -Seconds 2
Hold 'elysiumagronomy:bacon'; MC-RClick; Start-Sleep -Seconds 1
Hold 'minecraft:egg'; MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 400
MC-RClick; Start-Sleep -Seconds 1                      # empty hand: start cooking
MC-Shot "$PSScriptRoot\kit_1_skillet_cooking.png" 0.5
MC-Cmd '/tick rate 400'; Start-Sleep -Seconds 4
MC-Cmd '/tick rate 20'; Start-Sleep -Seconds 1
MC-RClick; Start-Sleep -Seconds 1                      # empty hand: take the servings
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\kit_2_skillet_result.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 400
"skillet chat:"; ChatSince $n | Select-Object -Last 6

"== cutting board: beef and a knife"
$n = (LogLines).Count
MC-Cmd '/setblock 204 200 204 elysiumagronomy:cutting_board'; Start-Sleep -Milliseconds 400
MC-Cmd '/tp @s 204.5 201 201.5 0 30'; Start-Sleep -Seconds 2
Hold 'minecraft:beef'; MC-RClick; Start-Sleep -Seconds 1
Hold 'elysiumagronomy:iron_knife'; MC-RClick; Start-Sleep -Seconds 1
MC-Shot "$PSScriptRoot\kit_3_board.png" 0.5
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 300; MC-RClick; Start-Sleep -Seconds 1
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\kit_4_board_result.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 400
"board chat:"; ChatSince $n | Select-Object -Last 6

"== pot: dirt soil and a bamboo sprout (1400 ticks)"
$n = (LogLines).Count
MC-Cmd '/setblock 208 200 204 elysiumagronomy:andesite_botany_pot'; Start-Sleep -Milliseconds 400
MC-Cmd '/tp @s 208.5 201 201.5 0 30'; Start-Sleep -Seconds 2
Hold 'minecraft:dirt'; MC-RClick; Start-Sleep -Seconds 1
Hold 'minecraft:bamboo'; MC-RClick; Start-Sleep -Seconds 1
MC-Shot "$PSScriptRoot\kit_5_pot_planted.png" 0.5
MC-Cmd '/tick rate 1000'; Start-Sleep -Seconds 6
MC-Cmd '/tick rate 20'; Start-Sleep -Seconds 1
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 300; MC-RClick; Start-Sleep -Seconds 1
MC-Shot "$PSScriptRoot\kit_6_pot_grown.png" 0.5
MC-Cmd '/data get block 208 200 204'; Start-Sleep -Seconds 2
"pot chat:"; ChatSince $n | Select-Object -Last 5 | ForEach-Object { $_.ToString().Substring(0, [Math]::Min(240, $_.ToString().Length)) }

"== mushroom colony"
$n = (LogLines).Count
MC-Cmd '/setblock 212 200 204 elysiumagronomy:mushroom_colony'; Start-Sleep -Milliseconds 400
MC-Cmd '/tp @s 212.5 201 201.5 0 30'; Start-Sleep -Seconds 2
Hold 'minecraft:brown_mushroom'; MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/gamerule randomTickSpeed 200'; Start-Sleep -Seconds 1
MC-Cmd '/tick rate 400'; Start-Sleep -Seconds 8
MC-Cmd '/tick rate 20'; MC-Cmd '/gamerule randomTickSpeed 3'; Start-Sleep -Seconds 1
MC-Shot "$PSScriptRoot\kit_7_colony.png" 0.5
MC-Cmd '/clear @s'; Start-Sleep -Milliseconds 300; MC-RClick; Start-Sleep -Seconds 1
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\kit_8_colony_harvest.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 400
"colony chat:"; ChatSince $n | Select-Object -Last 4
"kitchen stage done"
