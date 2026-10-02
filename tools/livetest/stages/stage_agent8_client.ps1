. "$PSScriptRoot\mc.ps1"
# Agent 8's work in the E client: creatures by kindred and court, comforts fittings, arcana budding blocks.
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function ChatSince($n) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 6000'
MC-Cmd '/weather clear'
MC-Cmd '/gamerule doDaylightCycle false'
MC-Cmd '/tp @s 600.5 201 600.5 0 20'; Start-Sleep -Seconds 6
MC-Cmd '/fill 580 199 580 640 199 640 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 580 200 580 640 215 640 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/fill 580 200 580 640 200 640 minecraft:barrier hollow'; Start-Sleep -Seconds 1
MC-Cmd '/fill 581 200 581 639 200 639 minecraft:air'; Start-Sleep -Seconds 1

"== a kindred (the barbs) and a court (the forge)"
$n = (LogLines).Count
MC-Cmd '/summon elysiumbestiary:ferricbarb 596 201 610 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:dunebarb 599 201 610 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:rimebarb 602 201 610 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:cinderbarb 605 201 610 {NoAI:1b}'
MC-Cmd '/tp @s 600.5 201 603.5 0 15'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_1_barbs.png" 0.5
MC-Cmd '/summon elysiumbestiary:forgeprime 600 201 625 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:forgecaller 594 201 625 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:forgeguard 606 201 625 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:forgemortar 597 201 628 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:forgelet 603 201 628 {NoAI:1b}'
MC-Cmd '/tp @s 600.5 202 614.5 0 12'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_2_forge_court.png" 0.5
MC-Cmd '/data get entity @e[type=elysiumbestiary:forgeprime,limit=1] Health'; Start-Sleep -Seconds 1
MC-Cmd '/data get entity @e[type=elysiumbestiary:ferricbarb,limit=1] Health'; Start-Sleep -Seconds 1
"creature chat:"; ChatSince $n | Select-String 'summoned|Health|Unknown|entity' | Select-Object -Last 12 | ForEach-Object { $_.ToString().Substring(0, [Math]::Min(200, $_.ToString().Length)) }

"== companions and the Vale's fauna"
$n = (LogLines).Count
MC-Cmd '/summon elysiumbestiary:goldkin 620 201 600 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:haulwing 624 201 600 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:fenstag 628 201 600 {NoAI:1b}'
MC-Cmd '/summon elysiumbestiary:moonfang 632 201 600 {NoAI:1b}'
MC-Cmd '/tp @s 626.5 202 592.5 0 12'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_3_companions.png" 0.5
"companion chat:"; ChatSince $n | Select-String 'summoned|Unknown' | Select-Object -Last 6

"== comforts: a lit room, a hammock, chimes, the post"
$n = (LogLines).Count
MC-Cmd '/setblock 585 200 585 elysiumcomforts:candelabra'
MC-Cmd '/setblock 587 200 585 elysiumcomforts:blackstone_lamp'
MC-Cmd '/setblock 589 200 585 elysiumcomforts:blue_hammock'
MC-Cmd '/setblock 591 203 585 elysiumcomforts:black_copper_chimes'
MC-Cmd '/setblock 593 200 585 elysiumcomforts:oak_pigeonhole'
MC-Cmd '/setblock 595 200 585 elysiumcomforts:doormat'
MC-Cmd '/setblock 597 200 585 elysiumcomforts:red_candle_holder'
MC-Cmd '/tp @s 591.5 202 592.5 180 15'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_4_comforts.png" 0.5
"comforts chat:"; ChatSince $n | Select-String 'Changed|Unknown|Could not' | Select-Object -Last 8

"== arcana: budding blocks planted from shards"
$n = (LogLines).Count
MC-Cmd '/setblock 610 200 585 elysiumarcana:budding_copper'
MC-Cmd '/setblock 612 200 585 elysiumarcana:budding_iron'
MC-Cmd '/setblock 614 200 585 elysiumarcana:budding_diamond'
MC-Cmd '/setblock 616 200 585 elysiumarcana:budding_lapis'
MC-Cmd '/tp @s 613.5 202 591.5 180 15'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_5_budding.png" 0.5
MC-Cmd '/give @s elysiumarcana:budding_gold'; Start-Sleep -Seconds 1
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$PSScriptRoot\a8_6_inventory.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 400
"arcana chat:"; ChatSince $n | Select-String 'Changed|Unknown|Gave' | Select-Object -Last 6

"== the Vale: ring 0 crossing"
$n = (LogLines).Count
MC-Cmd '/execute in elysiumwilds:vale run tp @s 0 120 0'; Start-Sleep -Seconds 12
MC-Cmd '/execute in elysiumwilds:vale run locate biome elysiumwilds:vale_verdant_vale'; Start-Sleep -Seconds 4
MC-Shot "$PSScriptRoot\a8_7_vale.png" 0.5
"vale chat:"; ChatSince $n | Select-Object -Last 4
MC-Cmd '/execute in minecraft:overworld run tp @s 600.5 201 600.5'; Start-Sleep -Seconds 6
MC-Cmd '/gamerule doDaylightCycle true'
"agent8 stage done"
