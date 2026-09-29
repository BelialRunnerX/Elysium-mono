. "$PSScriptRoot\mc.ps1"
# The space remainder by hand (SPEC_SPACE.md S6b): a rocket on a pad, the Uranium Motor fitted,
# uranium ingots burned as fuel, the destination screen on the Launch key showing what that fuel
# reaches; then the Oil Finder read against a chunk. The rocket answers on the action bar, so
# every answer is a screenshot with the bar in it.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 90 -258.5 180 10'; Start-Sleep -Seconds 3
MC-Cmd '/kill @e[type=elysiumspace:rocket,distance=..32]'
MC-Cmd '/time set 6000'
MC-Cmd '/gamerule doMobSpawning false'
MC-Cmd '/kill @e[type=!player,distance=..96]'
MC-Cmd '/fill 897 89 -265 903 92 -260 minecraft:air'
MC-Cmd '/fill 880 89 -280 920 89 -240 minecraft:smooth_stone'
MC-Cmd '/setblock 900 89 -263 elysiumspace:launch_pad'
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumspace:rocket'; MC-Key '1'
Say "== the rocket on its pad"
MC-Cmd '/tp @s 900.5 90 -258.5 180 20'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Seconds 2                                          # the rocket item on the pad
MC-Shot "$Out\rocket_1_pad.png" 0.5
Say "== the uranium motor"
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumspace:uranium_motor'; MC-Key '1'
MC-Cmd '/tp @s 900.5 90 -258.5 180 5'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Seconds 1                                          # fitted: "Motor fitted: burns uranium"
MC-Shot "$Out\rocket_2_motor.png" 0.5
Say "== uranium as fuel"
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumatomics:uranium_ingot 4'; MC-Key '1'
MC-Cmd '/tp @s 900.5 90 -258.5 180 5'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Milliseconds 800                                   # 2250 / 3000
MC-Shot "$Out\rocket_3_fuelled.png" 0.5
MC-RClick; Start-Sleep -Milliseconds 800                                   # a second ingot does not fit
Say "== the destination screen"
MC-Cmd '/clear @s'
MC-Cmd '/tp @s 900.5 90 -258.5 180 5'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Seconds 2                                          # sit in it
MC-Key 'H'; Start-Sleep -Seconds 32                                        # launch (H; G is Curios' key): countdown, climb, the screen
MC-Shot "$Out\rocket_4_destinations.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 12                                      # nobody chose: it lands where it is
MC-Shot "$Out\rocket_5_landed.png" 0.5
MC-Key 'LSHIFT'; Start-Sleep -Seconds 1                                    # dismount
Say "== the oil finder"
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumspace:oil_finder'; MC-Key '1'
MC-Cmd '/tp @s 900.5 90 -250.5 0 60'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Milliseconds 800                                   # "This chunk holds about N mB of oil" / "dry"
MC-Shot "$Out\rocket_6_oil_finder.png" 0.5
MC-Cmd '/kill @e[type=elysiumspace:rocket,distance=..32]'
MC-Cmd '/setblock 900 89 -263 minecraft:smooth_stone'
MC-Cmd '/gamerule doMobSpawning true'
Write-Host "rocket stage done"
