param(
    [int]$RowY0 = 318,
    [int]$RowStep = 78,
    [int]$RowX = 800
)
. "$PSScriptRoot\mc.ps1"
# The questbook by hand (docs/handoff/2026-09-28-questbook-handoff.md): the book reset by
# command, a step finished by having the thing, the finish announced on the action bar, the
# book opened and photographed (contents, a chapter, a step), the reward taken by the step's
# last row and again by command, the paid count read back, and the character level read
# before and after so the experience is seen to land.
#
# Geometry (1600x900, GUI scale 3): the chooser's panel is centred; the row positions below
# were read off the first run's shots (almanac_1_contents.png) and hold while the chapter
# count and the first chapter's quest count do.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
function Ask($cmd, $pattern) { $n = (LogLines).Count; MC-Cmd $cmd; Start-Sleep -Milliseconds 900; $got = (Chat $n $pattern) -join ' | '; Write-Host ("  " + $cmd + " -> " + $got.Substring(0, [Math]::Min(240, $got.Length))) }
function Row([int]$i) { return @($RowX, ($RowY0 + $i * $RowStep)) }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
MC-Cmd '/clear @s'
Say "== the book by command"
Ask '/almanac reset' 'forgotten'
Ask '/almanac status' 'steps done'
Ask '/data get entity @s neoforge:attachments."elysiumlib:level"' 'has the following|level'
MC-Cmd '/give @s elysiumalmanac:almanac'; MC-Key '1'; Start-Sleep -Milliseconds 600
# The first page finishes by being reachable; the crank by being held. The finish is an
# action-bar line, which the log never sees: the shot catches it.
MC-Cmd '/give @s elysiumkinetics:hand_crank'; Start-Sleep -Milliseconds 1300
MC-Shot "$Out\almanac_0_finished.png" 0.5
Ask '/almanac status' 'steps done'
Ask '/almanac claim welcome' 'paid'
Ask '/almanac claim welcome' 'already'
Ask '/almanac claim no_such' 'No such'
Ask '/almanac claim luna' 'not finished'

Say "== the book on screen"
MC-Key '1'; Start-Sleep -Milliseconds 300
MC-RClick; Start-Sleep -Seconds 2                                          # the contents
MC-Shot "$Out\almanac_1_contents.png" 0.5
$r = Row 0; MC-Click $r[0] $r[1]; Start-Sleep -Seconds 1                   # First Light
MC-Shot "$Out\almanac_2_chapter.png" 0.5
$r = Row 1; MC-Click $r[0] $r[1]; Start-Sleep -Seconds 1                   # the second row: the crank, done
MC-Shot "$Out\almanac_3_step.png" 0.5
# The step's last row is the reward. The step page is shorter than the contents, so its
# panel sits lower: a one-task step's reward row is at y 524 (read off almanac_3_step.png).
MC-Click 800 524; Start-Sleep -Seconds 2
MC-Shot "$Out\almanac_4_claimed.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
Ask '/almanac status' 'paid'
Ask '/data get entity @s neoforge:attachments."elysiumlib:level"' 'has the following|level'
Ask '/data get entity @s neoforge:attachments."elysiumlib:xp"' 'has the following|xp'

Say "== experience from an ordinary action: mining a column of stone in survival"
# Stand on a six-block column of stone looking straight down and hold the attack button:
# each block broken drops the player onto the next. Mining pays 3 a block (ElysiumXpRules),
# so the xp attachment rises by the blocks broken times three, less nothing (no cap met).
# By day and warded: the stage stands in the open, and a level-46 scavenger killed the
# tester here once at night.
MC-Cmd '/time set noon'
MC-Cmd '/effect give @s minecraft:resistance 120 4 true'
MC-Cmd '/fill 900 83 -259 900 89 -259 minecraft:stone'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 90 -258.5 180 90'; Start-Sleep -Seconds 2
# The pickaxe into the first slot and that slot selected: /give lands in the first free slot,
# which was the fourth, and stone by hand outlasts the hold.
MC-Cmd '/item replace entity @s hotbar.0 with minecraft:diamond_pickaxe'; MC-Key '1'; Start-Sleep -Milliseconds 400
MC-Cmd '/gamemode survival'; Start-Sleep -Milliseconds 600
Ask '/data get entity @s neoforge:attachments."elysiumlib:xp"' 'has the following|xp'
[Win32MC]::Mouse(0x0002, 0, 0, 0); Start-Sleep -Seconds 5; [Win32MC]::Mouse(0x0004, 0, 0, 0)
Start-Sleep -Milliseconds 800
MC-Shot "$Out\almanac_5_mined.png" 0.5
Ask '/data get entity @s neoforge:attachments."elysiumlib:xp"' 'has the following|xp'
Ask '/data get entity @s Pos' 'has the following'
MC-Cmd '/gamemode creative'
Write-Host "almanac stage done"
