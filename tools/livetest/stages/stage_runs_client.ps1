. "$PSScriptRoot\mc.ps1"
# Screenshots go to $env:ELYSIUM_SHOTS when it is set, else beside this script.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
# The runs: every pipe-like block ends in a plate against the device it feeds,
# a dye colours it, and dyed runs only join their own colour or an undyed run.
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function ChatSince($n) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | ForEach-Object { $_.Line.Substring([Math]::Min(95, $_.Line.Length)) } }
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
$n = (LogLines).Count
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 6000'
MC-Cmd '/weather clear'
MC-Cmd '/execute in minecraft:overworld run tp @s 700.5 201 700.5 0 20'; Start-Sleep -Seconds 6
MC-Cmd '/fill 690 199 690 730 199 730 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 690 200 690 730 212 730 minecraft:air'; Start-Sleep -Seconds 1

"== six runs, each ending in a device"
# row z=710: device on the west end, three run blocks eastwards
MC-Cmd '/setblock 694 200 704 elysiumspace:coal_generator'
MC-Cmd '/fill 695 200 704 697 200 704 elysiumspace:cable'
MC-Cmd '/setblock 694 200 707 elysiumspace:fluid_tank'
MC-Cmd '/fill 695 200 707 697 200 707 elysiumspace:pipe'
MC-Cmd '/setblock 694 200 710 elysiumindustry:steam_turbine'
MC-Cmd '/fill 695 200 710 697 200 710 elysiumindustry:copper_wire'
MC-Cmd '/setblock 694 200 713 elysiumsolarpunk:solar_condenser'
MC-Cmd '/fill 695 200 713 697 200 713 elysiumsolarpunk:power_conduit'
MC-Cmd '/setblock 694 200 716 elysiumlogistics:timber_drawer'
MC-Cmd '/fill 695 200 716 697 200 716 elysiumlogistics:bus'
MC-Cmd '/setblock 694 200 719 elysiumatomics:reactor_core'
MC-Cmd '/fill 695 200 719 697 200 719 elysiumatomics:coolant_channel'
MC-Cmd '/tp @s 702.5 203 711.5 90 22'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_1_plates.png" 0.5
MC-Cmd '/tp @s 699.5 201 706.5 125 20'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_1b_cable_close.png" 1.0
MC-Cmd '/tp @s 699.5 201 712.5 125 20'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_1c_wire_close.png" 1.0
MC-Cmd '/tp @s 699.5 201 702.5 135 10'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_2_cable_plate.png" 0.5

"== dye: red on the middle wire, then a red wire and a blue wire refuse each other"
$n = (LogLines).Count
MC-Cmd '/clear @s'
MC-Cmd '/give @s minecraft:red_dye 4'; Start-Sleep -Milliseconds 500
MC-Cmd '/tp @s 696.5 201 708.5 180 35'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 702.5 203 711.5 90 22'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_3_dyed_wire.png" 0.5
# Two red cables placed by hand, then a blue one: red joins red, blue stands apart,
# and an undyed cable set beside the blue one joins it.
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumspace:cable[elysiumlib:run_dye="red"] 8'
MC-Cmd '/tp @s 710.5 200 723.5 180 70'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 711.5 200 723.5 180 70'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumspace:cable[elysiumlib:run_dye="blue"] 8'
MC-Cmd '/tp @s 712.5 200 723.5 180 70'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/setblock 713 200 722 elysiumspace:cable'
MC-Cmd '/clear @s'
MC-Cmd '/data get block 711 200 722'; Start-Sleep -Seconds 1
MC-Cmd '/data get block 712 200 722'; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 711.5 202 727.5 180 25'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_4_dyed_line.png" 0.5
MC-Cmd '/tp @s 711.5 201 725.5 180 22'; Start-Sleep -Seconds 4
MC-Shot "$Out\runs_4b_dyed_close.png" 1.0
"chat:"; ChatSince $n | Select-Object -Last 10 | ForEach-Object { $_.ToString().Substring(0, [Math]::Min(220, $_.ToString().Length)) }
"runs stage done"
