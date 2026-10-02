. "$PSScriptRoot\mc.ps1"
# The three kitchen stations side by side on the new build: their models should be the plates and the pot, not cubes.
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 1000'
MC-Cmd '/tp @s 450.5 201 400.5 0 20'; Start-Sleep -Seconds 4
MC-Cmd '/fill 445 199 395 460 199 410 minecraft:smooth_stone'; Start-Sleep -Milliseconds 600
MC-Cmd '/fill 445 200 395 460 210 410 minecraft:air'; Start-Sleep -Milliseconds 600
MC-Cmd '/setblock 448 200 404 elysiumagronomy:cooking_pot'
MC-Cmd '/setblock 450 200 404 elysiumagronomy:skillet'
MC-Cmd '/setblock 452 200 404 elysiumagronomy:cutting_board'
MC-Cmd '/setblock 454 200 404 elysiumagronomy:stone_oven'
MC-Cmd '/tp @s 451.5 200 400.5 0 22'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\stations_1_models.png" 0.5
"stations stage done"
