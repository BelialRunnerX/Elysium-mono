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
MC-Cmd '/fill 870 89 -240 940 89 -200 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 870 90 -240 940 105 -200 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/time set 6000'
MC-Cmd '/weather clear'
MC-Cmd '/kill @e[type=!player,distance=..80]'
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
MC-Cmd '/setblock 892 90 -212 minecraft:water'
$x = 882
foreach ($id in $rowTwo) {
  $y = if ($id -eq 'elysiumagronomy:arctic_char') { 90 } else { 90 }
  MC-Cmd "/summon $id $x.5 $y -212.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}"
  $x += 5
}
Start-Sleep -Seconds 3
$n = (LogLines).Count
MC-Cmd '/execute if entity @e[type=!player,distance=..60]'; Start-Sleep -Milliseconds 800
"summoned:"; Chat $n 'Test'
MC-Cmd '/tp @s 900.5 93 -232.5 180 12'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_1_row.png" 0.5
MC-Cmd '/tp @s 889.5 91 -225.5 180 8'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_2_beasts_close.png" 0.5
MC-Cmd '/tp @s 899.5 91 -225.5 180 8'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_3_animals_close.png" 0.5
MC-Cmd '/tp @s 889.5 91 -217.5 180 10'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_4_small_close.png" 0.5
MC-Cmd '/tp @s 902.5 91 -217.5 180 10'; Start-Sleep -Seconds 4
MC-Shot "$Out\creatures_5_borrowers_close.png" 0.5
# Let them walk: AI back on for a moment, then a shot mid-stride.
MC-Cmd '/execute as @e[type=!player,distance=..60] run data merge entity @s {NoAI:0b}'
MC-Cmd '/tp @s 900.5 94 -236.5 180 14'; Start-Sleep -Seconds 6
MC-Shot "$Out\creatures_6_moving.png" 0.5
MC-Cmd '/gamerule doMobSpawning true'
"creatures stage done"
