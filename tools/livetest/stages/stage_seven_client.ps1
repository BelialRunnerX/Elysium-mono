. "$PSScriptRoot\mc.ps1"
# The pack as ours alone: the world opens, and a thing from each corner of it still works.
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function ChatSince($n) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
$n = (LogLines).Count
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 6000'
MC-Cmd '/tp @s 100.5 201 100.5 0 15'; Start-Sleep -Seconds 6
MC-Cmd '/fill 90 199 90 120 199 120 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 90 200 90 120 210 120 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/setblock 96 200 106 elysiumkinetics:anchor'
MC-Cmd '/setblock 98 200 106 elysiumkinetics:wide_anchor'
MC-Cmd '/setblock 100 200 106 elysiumagronomy:skillet'
MC-Cmd '/setblock 102 200 106 elysiumcomforts:candelabra'
MC-Cmd '/setblock 104 200 106 elysiumlogistics:controller'
MC-Cmd '/summon elysiumbestiary:forgeprime 100 201 112 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:goldkin 96 201 112 {NoAI:1b}'
MC-Cmd '/tp @s 100.5 201 101.5 0 12'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\seven_1_stage.png" 0.5
MC-Cmd '/elysium colony natural'; Start-Sleep -Seconds 2
MC-Cmd '/execute positioned 3000 64 3000 run locate structure elysiumcolonies:settlement'; Start-Sleep -Seconds 4
MC-Cmd '/elysiumspace travel emerald_bastion'; Start-Sleep -Seconds 12
MC-Shot "$PSScriptRoot\seven_2_bastion.png" 0.5
MC-Cmd '/execute in minecraft:overworld run tp @s 100.5 201 101.5'; Start-Sleep -Seconds 5
"chat:"; ChatSince $n | Select-Object -Last 14 | ForEach-Object { $_.ToString().Substring(0, [Math]::Min(200, $_.ToString().Length)) }
"seven stage done"
