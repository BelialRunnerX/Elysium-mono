. "$PSScriptRoot\mc.ps1"
# The creatures on their body plans, summoned in a row and photographed in the game.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 91 -230.5 0 10'; Start-Sleep -Seconds 5
# Two layers of floor: the terrain under the stage at one spot killed a fish in its
# water hole the moment it was summoned (the hole sat on whatever was there).
MC-Cmd '/fill 870 88 -240 940 89 -200 minecraft:smooth_stone'; Start-Sleep -Seconds 1
# Two slabs: one fill of the whole volume is over the 32768-block limit and does nothing.
MC-Cmd '/fill 870 90 -240 940 97 -200 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/fill 870 98 -240 940 105 -200 minecraft:air'; Start-Sleep -Seconds 1
# Night with night vision: the undead kinds burn in daylight and were dead
# before the close-ups; night vision keeps the shots bright.
MC-Cmd '/time set 18000'
MC-Cmd '/effect give @s minecraft:night_vision 1200 0 true'
MC-Cmd '/weather clear'
MC-Cmd '/kill @e[type=!player,distance=..80]'
MC-Cmd '/kill @e[type=item,distance=..80]'; Start-Sleep -Seconds 1
MC-Cmd '/gamerule doMobSpawning false'

# Row one: the beasts, an animal of each kind, the shade.
$rowOne = @('elysiumbestiary:spallet', 'elysiumbestiary:drayhulk', 'elysiumbestiary:carrier', 'elysiumbestiary:fenstag',
            'elysiumbestiary:brackhide', 'elysiumbestiary:ashwing', 'elysiumbestiary:glassfin', 'elysiumbestiary:shade')
$x = 882
foreach ($id in $rowOne) {
  MC-Cmd "/summon $id $x.5 90 -220.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}"
  $x += 5
}
# Row two: a pigeon, a bee, a fish (in a puddle), and three of the borrowing kinds.
$rowTwo = @('elysiumcomforts:pigeon', 'elysiumagronomy:amber_bee', 'elysiumagronomy:arctic_char',
            'elysiumbestiary:siegehulk', 'elysiumbestiary:nullprime', 'elysiumbestiary:prowlframe')
# A 3x3 pool sunk in the floor: a source on the floor spreads, and an Elysium fish
# in a one-block hole is dead in seconds (KNOWN_ISSUES: fish in narrow water).
MC-Cmd '/fill 891 89 -213 893 89 -211 minecraft:water'
$x = 882
foreach ($id in $rowTwo) {
  $y = if ($id -eq 'elysiumagronomy:arctic_char') { 89 } else { 90 }
  MC-Cmd "/summon $id $x.5 $y -212.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}"
  $x += 5
}
Start-Sleep -Seconds 3
$n = (LogLines).Count
MC-Cmd '/execute if entity @e[type=!player,distance=..60]'; Start-Sleep -Milliseconds 800
"summoned:"; Chat $n 'Test'
MC-Cmd '/tp @s 900.5 93 -232.5 0 12'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_1_row.png" 0.5
MC-Cmd '/tp @s 889.5 91 -225.5 0 8'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_2_beasts_close.png" 0.5
MC-Cmd '/tp @s 899.5 91 -225.5 0 8'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_3_animals_close.png" 0.5
MC-Cmd '/tp @s 889.5 91 -217.5 0 10'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_4_small_close.png" 0.5
MC-Cmd '/tp @s 902.5 91 -217.5 0 10'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_5_borrowers_close.png" 0.5
# Portraits: the camera five blocks in front of each creature, three for the small ones.
$small = @('elysiumcomforts:pigeon', 'elysiumagronomy:amber_bee', 'elysiumagronomy:arctic_char')
$big = @('elysiumbestiary:drayhulk', 'elysiumbestiary:siegehulk', 'elysiumbestiary:carrier', 'elysiumbestiary:nullprime')
$x = 882
foreach ($id in $rowOne) {
  # The shade goes out after its lifetime: a fresh one for its portrait.
  if ($id -eq 'elysiumbestiary:shade') { MC-Cmd "/summon $id $x.5 90 -220.5 {NoAI:1b,Silent:1b,Rotation:[180f,0f]}"; Start-Sleep -Seconds 2 }
  if ($big -contains $id) { MC-Cmd "/tp @s $x.5 90 -224.5 0 6" } else { MC-Cmd "/tp @s $x.5 90 -223.5 0 12" }
  Start-Sleep -Seconds 3
  MC-Shot "$Out\creatures_p_$($id.Split(':')[1]).png" 0.5
  $x += 5
}
$x = 882
foreach ($id in $rowTwo) {
  if ($small -contains $id) { MC-Cmd "/tp @s $x.5 90 -214.5 0 34" } elseif ($big -contains $id) { MC-Cmd "/tp @s $x.5 90 -216.5 0 6" } else { MC-Cmd "/tp @s $x.5 90 -215.5 0 12" }
  Start-Sleep -Seconds 3
  MC-Shot "$Out\creatures_p_$($id.Split(':')[1]).png" 0.5
  $x += 5
}
# Let them walk: AI back on for a moment, then a shot mid-stride.
MC-Cmd '/execute as @e[type=!player,distance=..60] run data merge entity @s {NoAI:0b}'
MC-Cmd '/tp @s 900.5 94 -236.5 0 14'; Start-Sleep -Seconds 6
MC-Shot "$Out\creatures_6_moving.png" 0.5
# What they say: with subtitles on (options.txt showSubtitles:true) the captions at the
# bottom right name each call and hum the client is playing (POLISH.md P5).
MC-Cmd '/tp @s 900.5 91 -222.5 0 10'; Start-Sleep -Seconds 8
MC-Shot "$Out\creatures_7_subtitles.png" 0.5
MC-Cmd '/gamerule doMobSpawning true'
"creatures stage done"
