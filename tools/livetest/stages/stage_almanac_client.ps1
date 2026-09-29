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
param(
    [int]$RowY0 = 318,        # the centre of the first row of a chooser, in pixels
    [int]$RowStep = 78,       # from one row's centre to the next
    [int]$RowX = 800          # the panel's centre
)
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
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
# The step's last row is the reward. A crank has one task, so the reward is row 1.
$r = Row 1; MC-Click $r[0] $r[1]; Start-Sleep -Seconds 2
MC-Shot "$Out\almanac_4_claimed.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
Ask '/almanac status' 'paid'
Ask '/data get entity @s neoforge:attachments."elysiumlib:level"' 'has the following|level'
Ask '/data get entity @s neoforge:attachments."elysiumlib:xp"' 'has the following|xp'

Say "== a counted step: break stone"
MC-Cmd '/gamemode survival'
MC-Cmd '/give @s minecraft:diamond_pickaxe'; MC-Key '2'
MC-Cmd '/fill 899 89 -262 901 89 -260 minecraft:stone'
Ask '/almanac status' 'open now'
MC-Cmd '/gamemode creative'
Write-Host "almanac stage done"
