param(
    [int]$Sleep = 3
)
. "$PSScriptRoot\mc.ps1"
# The way into the Vale by hand (CONVERSION.md §16.15): ten Vale Frames set in a Nether portal's
# ring on the stage, the surveyor's compass used on a frame, the gate lit and photographed, the
# player walked through, the Vale read back from the entity data and photographed, and the ring
# on the far side walked back through.
#
# The ring stands at x 905..908, y 90..94, z -262, facing the player at (906.5, 90, -258.5) who
# looks north (yaw 180 is north; yaw 0 is south) and a little down (pitch 24), so the crosshair
# rests on the sill's front face: the compass is used on the block under the crosshair, and
# used on air it opens its own screen instead, and that screen takes one ESC to clear what was
# typed into its search and a second to close (ElysiumChooser). The opening is 2 across, 3 up.
# Not yet run to the end: the 29 Sep runs found the two faults above, and the desktop was then
# handed back to Dylan; the server-side pieces (frames, gate, dimension, tags) are held by the
# wilds validator and negtest_wilds_portal (62 cases).
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
# Another process (a javac of a negtest, say) can take the foreground between two steps, and the
# driver refuses to type into whatever has it; so the game is fronted again before every step.
function Ask($cmd, $pattern) { MC-Focus | Out-Null; $n = (LogLines).Count; MC-Cmd $cmd; Start-Sleep -Milliseconds 900; $got = (Chat $n $pattern) -join ' | '; Write-Host ("  " + $cmd + " -> " + $got.Substring(0, [Math]::Min(240, $got.Length))) }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/time set noon'
MC-Cmd '/execute in minecraft:overworld run tp @s 906.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
MC-Cmd '/clear @s'
Say "== the ring"
# A 4 wide, 5 tall ring of frames with a 2x3 opening, corners left out as the rules allow.
MC-Cmd '/fill 905 90 -262 908 94 -262 minecraft:air'
MC-Cmd '/fill 906 90 -262 907 90 -262 elysiumwilds:vale_frame'                      # the sill
MC-Cmd '/fill 906 94 -262 907 94 -262 elysiumwilds:vale_frame'                      # the lintel
MC-Cmd '/fill 905 91 -262 905 93 -262 elysiumwilds:vale_frame'                      # the left side
MC-Cmd '/fill 908 91 -262 908 93 -262 elysiumwilds:vale_frame'                      # the right side
MC-Cmd '/item replace entity @s hotbar.0 with elysiumwilds:biome_compass'; MC-Key '1'; Start-Sleep -Milliseconds 600
MC-Cmd '/tp @s 906.5 90 -258.5 180 24'; Start-Sleep -Seconds 2      # the look again: chat and keys can nudge it
MC-Shot "$Out\vale_1_ring.png" 0.5
Say "== the compass on a frame"
# The crosshair rests on the sill's front face (the block at 906 or 907, y 90, z -262); no mouse
# movement, since moving the captured cursor turns the view.
MC-Focus | Out-Null; MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$Out\vale_2_lit.png" 0.5
Ask '/execute if block 906 91 -262 elysiumwilds:vale_portal' 'passed|failed'
Say "== through the gate"
MC-Cmd '/gamemode survival'
MC-Cmd '/tp @s 906.5 90 -258.5 180 0'; Start-Sleep -Seconds 1      # level again, so the walk is straight
MC-Focus | Out-Null; [Win32MC]::Key(0x57, $true); Start-Sleep -Seconds $Sleep; [Win32MC]::Key(0x57, $false)   # W: walk north into it
Start-Sleep -Seconds 6
Ask '/data get entity @s Dimension' 'elysiumwilds:vale|overworld'
MC-Shot "$Out\vale_3_arrived.png" 0.5
Ask '/data get entity @s Pos' 'has the following'
Ask '/locate biome elysiumwilds:verdant_vale' 'nearest|Could not'
Say "== and back"
# The return ring stands beside the landing; face about and walk into it.
MC-Focus | Out-Null; MC-Cmd '/gamemode creative'
Start-Sleep -Seconds 3
Ask '/data get entity @s Dimension' 'elysiumwilds:vale|overworld'
MC-Focus | Out-Null; MC-Shot "$Out\vale_4_home_or_not.png" 0.5
Write-Host "vale stage done"
