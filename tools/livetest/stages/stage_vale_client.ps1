param(
    [int]$Sleep = 3
)
. "$PSScriptRoot\mc.ps1"
# The way into the Vale by hand (CONVERSION.md Â§16.15): ten Vale Frames set in a Nether portal's
# ring on the stage, the surveyor's compass used on a frame, the gate lit and photographed, the
# player walked through, the Vale read back from the entity data and photographed, and the ring
# on the far side walked back through.
#
# The ring stands at x 913..916, y 90..94, z -262, facing the player at (914.5, 90, -258.5) who
# looks north (yaw 180 is north; yaw 0 is south) and a little down (pitch 24), so the crosshair
# rests on the sill's front face: the compass is used on the block under the crosshair, and
# used on air it opens its own screen instead, and that screen takes one ESC to clear what was
# typed into its search and a second to close (ElysiumChooser). The opening is 2 across, 3 up.
# The server-side pieces (frames, gate, dimension, tags) are held by the wilds validator and
# negtest_wilds_portal (82 cases); this is the hand on the door.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
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
MC-Cmd '/execute in minecraft:overworld run tp @s 914.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
MC-Cmd '/clear @s'
Say "== the ring"
# A second ring eight blocks east of the first (1 Oct): a ring's Vale side is settled once, on its
# first trip, so a new anchor is what exercises the search. A 4 wide, 5 tall ring of frames with a 2x3 opening, corners left out as the rules allow.
MC-Cmd '/fill 913 90 -262 916 94 -262 minecraft:air'
MC-Cmd '/fill 914 90 -262 915 90 -262 elysiumwilds:vale_frame'                      # the sill
MC-Cmd '/fill 914 94 -262 915 94 -262 elysiumwilds:vale_frame'                      # the lintel
MC-Cmd '/fill 913 91 -262 913 93 -262 elysiumwilds:vale_frame'                      # the left side
MC-Cmd '/fill 916 91 -262 916 93 -262 elysiumwilds:vale_frame'                      # the right side
MC-Cmd '/item replace entity @s hotbar.0 with elysiumwilds:biome_compass'; MC-Key '1'; Start-Sleep -Milliseconds 600
MC-Cmd '/tp @s 914.5 90 -258.5 180 24'; Start-Sleep -Seconds 2      # the look again: chat and keys can nudge it
MC-Shot "$Out\vale_1_ring.png" 0.5
Say "== the compass on a frame"
# The crosshair rests on the sill's front face (the block at 906 or 907, y 90, z -262); no mouse
# movement, since moving the captured cursor turns the view.
MC-Focus | Out-Null; MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$Out\vale_2_lit.png" 0.5
Ask '/execute if block 914 91 -262 elysiumwilds:vale_portal' 'passed|failed'
Say "== through the gate"
# A player who walks through is carried the moment they stand in it, lands one block out from a
# ring the Vale builds for the way back, and is ignored by every gate for COOLDOWN_TICKS (3 s).
# The first run walked for three seconds: the first trip's biome search held the server some
# ten seconds, the client kept walking meanwhile, and on arrival the stale steps took the
# player straight back out through the return ring (the log's "Loaded N advancements" marks
# each change of world). So: stand in the gate (this client has autoJump off, and the sill is
# a block up), wait, then step back into the return ring on purpose.
MC-Cmd '/gamemode survival'
MC-Cmd '/tp @s 914.5 91 -261.5 180 0'; Start-Sleep -Seconds 1       # inside the opening, still
Start-Sleep -Seconds 14                                               # the first trip's search, and the crossing
Ask '/data get entity @s Dimension' 'elysiumwilds:vale|overworld'
MC-Focus | Out-Null; MC-Shot "$Out\vale_3_arrived.png" 0.5
Ask '/data get entity @s Pos' 'has the following'
Ask '/execute if block ~ ~ ~ minecraft:air' 'passed|failed'
Say "== and back"
# The landing faces away from the return ring, one block out: a short step back enters it.
Start-Sleep -Seconds 3                                                # the cooldown
MC-Focus | Out-Null; [Win32MC]::Key(0x53, $false); Start-Sleep -Milliseconds 700; [Win32MC]::Key(0x53, $true)   # S held 0.7 s: step back into the ring (Key's second argument is UP; the 1 Oct runs had it reversed and left the key held)
Start-Sleep -Seconds 6
Ask '/data get entity @s Dimension' 'elysiumwilds:vale|overworld'
Ask '/data get entity @s Pos' 'has the following'
MC-Focus | Out-Null; MC-Shot "$Out\vale_4_home_or_not.png" 0.5
MC-Focus | Out-Null; MC-Cmd '/gamemode creative'
Write-Host "vale stage done"
