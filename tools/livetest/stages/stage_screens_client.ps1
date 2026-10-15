. "$PSScriptRoot\mc.ps1"
# Every block that opens a menu, opened once and photographed, with a verdict on
# whether a screen came up; then a night of natural spawns counted around the player.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { "$(Get-Date -Format HH:mm:ss) $s" }
# Whether the world answers a command right now: it does not while a screen is up.
function Answers {
  $n = (LogLines).Count
  MC-CmdOnce '/time query daytime'
  for ($w = 0; $w -lt 8; $w++) { Start-Sleep -Milliseconds 250; if ((Chat $n 'time is')) { return $true } }
  return $false
}

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 91 -258.5 180 24'; Start-Sleep -Seconds 5
MC-Cmd '/fill 880 89 -280 920 89 -240 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 880 90 -280 920 100 -240 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/time set 6000'
MC-Cmd '/kill @e[type=!player,distance=..64]'

$screens = @(
  'elysiumarmory:gear_smithing_table', 'elysiumarmory:salvager', 'elysiumarmory:reforging_table', 'elysiumarmory:stone_anvil',
  'elysium:reforge_table', 'elysium:gem_cutting_table', 'elysium:augmenting_table',
  'elysiumenchanting:arcane_table', 'elysiumenchanting:infusion_basin', 'elysiumenchanting:scriptorium', 'elysiumenchanting:grand_scriptorium',
  'elysiumarcana:transmuter', 'elysiumagronomy:oak_cabinet', 'elysiumagronomy:tackle_box',
  'elysiumspace:coal_generator', 'elysiumspace:freight_terminal', 'elysiumnpcs:gear_bench'
)
$i = 0
$verdicts = @()
foreach ($block in $screens) {
  $i++
  $name = $block -replace '[:]', '_'
  MC-Cmd '/setblock 900 90 -262 minecraft:air'
  MC-Cmd "/setblock 900 90 -262 $block"
  MC-Cmd '/tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 2
  MC-RClick; Start-Sleep -Seconds 2
  MC-Shot "$Out\screen_$($i.ToString('00'))_$name.png" 0.5
  if (Answers) { $verdicts += "$block : no screen opened"; }
  else { MC-Key 'ESC'; Start-Sleep -Milliseconds 800; if (Answers) { $verdicts += "$block : a screen opened and ESC closed it" } else { MC-Key 'ESC'; Start-Sleep -Milliseconds 800; $verdicts += "$block : a screen opened; two ESCs to get out" } }
}
MC-Cmd '/setblock 900 90 -262 minecraft:air'
"verdicts:"; $verdicts

Say "== a night of natural spawns around the player (creative, on the platform)"
MC-Cmd '/kill @e[type=!player,distance=..128]'
MC-Cmd '/time set 18000'
MC-Cmd '/tp @s 900.5 90 -259.5 180 10'
Start-Sleep -Seconds 120
$n = (LogLines).Count
foreach ($t in 'elysiummobs:scavenger', 'elysiummobs:whisper', 'elysiummobs:reaver', 'elysiummobs:drone', 'elysiummobs:adept', 'elysiummobs:lictor', 'elysiummobs:praetor', 'elysiummobs:bulwark', 'elysiummobs:harrier', 'elysiummobs:choir', 'elysiumbestiary:graftguard_bulk', 'minecraft:zombie', 'minecraft:skeleton') {
  MC-Cmd "/execute if entity @e[type=$t,distance=..128]"; Start-Sleep -Milliseconds 500
}
"scavenger, whisper, reaver, drone, adept, lictor, praetor, bulwark, harrier, choir, graftguard, zombie, skeleton within 128 after two minutes of night:"; Chat $n 'Test'
MC-Cmd '/tp @s 900.5 92 -240.5 0 15'; Start-Sleep -Seconds 3
MC-Shot "$Out\spawns_night.png" 0.5
MC-Cmd '/time set 6000'
"screens stage done"
