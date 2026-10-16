param(
    [int]$Sleep = 3
)
. "$PSScriptRoot\mc.ps1"
. "$PSScriptRoot\answers.ps1"   # how a command is sent and its answers read: every command here goes through it
# The way into the Vale by hand (CONVERSION.md §16.15): ten Vale Frames set in a Nether portal's
# ring on the stage, the surveyor's compass used on a frame, the gate lit and photographed, the
# player walked through, the Vale read back and photographed, and the ring on the far side walked
# back through.
#
# The ring stands at x 913..916, y 90..94, z -262, facing the player at (914.5, 90, -258.5) who
# looks north (yaw 180 is north; yaw 0 is south) and a little down (pitch 24), so the crosshair
# rests on the sill's front face: the compass is used on the block under the crosshair, and
# used on air it opens its own screen instead, and that screen takes one ESC to clear what was
# typed into its search and a second to close (ElysiumChooser). The opening is 2 across, 3 up.
# The server-side pieces (frames, gate, dimension, tags) are held by the wilds validator and
# negtest_wilds_portal; this is the hand on the door.
#
# The floor (15 Oct): the client's world is built new from reference/ on a rebuilt rig, and the
# stone platform the 1 Oct runs stood on came with the old world; so the stage lays its own,
# smooth stone at y 89 over x 880..940, z -290..-250 with eight layers of air above it. The stage
# ends on one line, VALE OK or VALE BAD with every failed check listed, and exits 0 or 1.
#
# The crossing, by the game's own word (16 Oct; answers.ps1 has the whole of it). Nothing is kept
# by hand: until 16 Oct this stage filed its ring and landing in a site list in the world folder for
# the footing stage, and three rounds of review found that list lied fourteen ways, as did the
# belief that the client logs "Loaded N advancements" on every change of world (it logs that only
# with an advancement update, and a change of world sends none). So the step into the gate waits,
# up to two minutes, for the mod's first-trip line for this player and this ring, read after the
# echo of a fence sent just before the step; then the game is asked where the player is, with
# /execute if dimension elysiumwilds:vale run data get entity @s Dimension, until it says the Vale.
# This stage needs no first trip: a world whose ring at 914 91 -262 has been through before logs no
# line, and the wait then runs its course before the game is asked. What the line said, if any, is
# shown and not judged (stage_vale_footing_client.ps1 is the stage that asserts a first trip).
#
# Where, and in which world, in one read (15 Oct, round 2). The landing is one block in front of
# the return ring and the player is in survival there, so a hit, a current or a stale step can put
# them back through it in the seconds between two reads. So the landing is read as
# /execute if dimension elysiumwilds:vale run data get entity @s Pos, which answers nothing at all
# outside the Vale, and a landing not read there is a failure. Home is read the same way in the
# overworld, and must be by its ring: the way back puts a player where they walked in, one block
# out of the ring's plane on their side (ValeTravel.back: 914.5 91 -260.5). (@s[distance=..4]
# would not do: a selector on @s measures the distance without asking which world the player is
# in.) Where they are cannot tell the way back from the other ways to the overworld, though: a
# death and a respawn, or the world's spawn the mod falls back on when no way in is on record, put
# the player at the world's spawn or their own, and in a world whose spawn is by the ring (the
# stage's own start spot, 914.5 90 -258.5, is inside the box home is allowed) that is home too
# (16 Oct, round 4, H1). So the stage counts deaths in an objective of its own, made fresh each run
# (deathCount) and set to nought, read before the gate and again at home with marked Asks, and
# refuses any change; and it refuses the mod's line for a way out with no way in on record
# ("NAME left the Vale by a gate with no way in on record; ...") read after the gate's fence.
# A line not read is no proof it never came, though: the log can roll over at midnight with the line
# in the old one, unread (round 5, RZ1). So the stage reads the world's own count of this player's
# ways out to the spawn (answers.ps1, Record: /elysium vale trips, kept in the mod's saved data)
# before the gate and again at home and refuses any change, and it refuses outright when a new log
# began after the gate's fence (GapSince), since the log can then show nothing absent.
#
# Whose answers (round 5, PD1). Each read is matched whole, with the player's name: the landing and
# home Pos only as "ElysiumTester has the following entity data: [Xd, Yd, Zd]" from end to end,
# and home only as that player's line naming the overworld. Outside the Vale the in-Vale reads say
# nothing of their own, and a pet's death message (one line of system chat, its name and its
# killer's given with an anvil) shaped like the end of an answer was taken for one.
#
# This run's ring (round 5, L1). The ring's opening is cleared and its frames set with Fill, which
# wants "Successfully filled" or "No blocks were filled" and nothing else: as steps, any line but an
# error passed them, so on a world from an earlier run a clearing fill typed into a screen, with an
# advancement announced in its window, went through, and the earlier run's lit gate passed the
# check that the compass lit this one. And before the compass the gate's cell is asked not to be a
# lit gate already. The steps left (the clock, the tps that set the look, /clear, the compass put in
# hand, the game modes) can only fail a check by not running: unrun, the compass is not in hand or
# not aimed at this ring and lights nothing, and survival is asked of the game, not taken from its
# step. The stage does not stand against an operator who sets out to fool it (answers.ps1).
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$script:Bad = New-Object System.Collections.ArrayList
$script:Said = ''
$script:Why = ''
$script:Landing = 'none'
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
function Bad([string]$what) { [void]$script:Bad.Add($what); Write-Host "    BAD  $what" }
# Whose answer a line is (answers.ps1, 15 Oct): every command here - Ask, Fill, Step, and Stand for
# the gate - is typed once and followed by a marker of its own, /tellraw @s "elysium mark <pid> <n>",
# and judged only on the chat lines between the marker before it and its own: the server answers in
# order, so those lines are its answers and nobody else's. A marker not back in ten seconds fails its
# command, and a lone marker then shuts in whatever it still says; a read wants exactly one answer,
# so a command run twice fails, and player chat is no answer. A step wants an answer too, and one
# with none before its marker failed (16 Oct): only /gamemode creative is sent -Quiet, since a game
# mode the player is already in answers nothing. A new log fails every command still open. (The
# waiting list this replaced gave a late answer to the oldest command that could take it, and was
# fooled when MC-Cmd, seeing an earlier command's stale garble, typed a verdict twice: the second
# "Test passed" went to the next verdict, whose own answer was "Test failed".)
function Heard { if ($script:Said) { $script:Said } else { $script:Why } }
function Finish {
    Say "== the verdict"
    if ($script:Bad.Count -eq 0) { Write-Host "VALE OK: the ring lit, carried a survival player into the Vale at $script:Landing, and the return ring carried it home"; exit 0 }
    Write-Host "VALE BAD ($($script:Bad.Count)): landing $script:Landing"
    foreach ($b in $script:Bad) { Write-Host "  - $b" }
    exit 1
}
$Anchor = '914 91 -262'   # the ring's anchor: the opening's lowest cell, then its least across (PortalRules.Ring)
$Deaths = 'elysium_vale_deaths'   # the stage's own deathCount objective (see the header)
# A Pos read's three numbers, or $null when it is not this player's whole answer or holds none that
# parse (see the header).
function Coords([string]$said) {
    $m = [regex]::Match($said, '^' + [regex]::Escape($Me) + ' has the following entity data: \[(-?[0-9][0-9.E-]*)d, (-?[0-9][0-9.E-]*)d, (-?[0-9][0-9.E-]*)d\]$')
    if (-not $m.Success) { return $null }
    $c = [Globalization.CultureInfo]::InvariantCulture; $st = [Globalization.NumberStyles]::Float
    $v = New-Object double[] 3
    for ($i = 0; $i -lt 3; $i++) { $d = 0.0; if (-not [double]::TryParse($m.Groups[$i + 1].Value, $st, $c, [ref]$d)) { return $null }; $v[$i] = $d }
    , $v
}
# A command whose one answer must be exactly what is wanted: $true, or a failure said and $false.
function Sure([string]$what, [string]$cmd, [string]$want) {
    Ask $cmd $want
    if ($script:Said -cmatch $want) { return $true }
    Bad "$what ($(Heard))"
    $false
}
# The player's deaths by the stage's own count (see the header), or -1 when that was not read.
function Deaths {
    $want = '^' + [regex]::Escape($Me) + ' has (\d+) \[' + [regex]::Escape($Deaths) + '\]$'
    Ask "/scoreboard players get @s $Deaths" $want
    if ($script:Said -cmatch $want) { return [int]$Matches[1] }
    -1
}

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
Resume
Step '/gamemode creative' -Quiet
Step '/time set noon'
Step '/execute in minecraft:overworld run tp @s 914.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
Step '/clear @s'
Say "== the floor"
Fill '/fill 880 89 -290 940 89 -250 minecraft:smooth_stone'          # 61 x 41
Fill '/fill 880 90 -290 940 97 -250 minecraft:air'                   # 20008 blocks, under fill's 32768
Step '/tp @s 914.5 90 -258.5 180 24'; Start-Sleep -Seconds 1
Say "== the ring"
# A second ring eight blocks east of the first (1 Oct): a ring's Vale side is settled once, on its
# first trip, so a new anchor is what exercises the search. A 4 wide, 5 tall ring of frames with a 2x3 opening, corners left out as the rules allow.
# Each a Fill, which wants the fill's own answer (see the header).
Fill '/fill 913 90 -262 916 94 -262 minecraft:air'
Fill '/fill 914 90 -262 915 90 -262 elysiumwilds:vale_frame'  # the sill
Fill '/fill 914 94 -262 915 94 -262 elysiumwilds:vale_frame'  # the lintel
Fill '/fill 913 91 -262 913 93 -262 elysiumwilds:vale_frame'  # the left side
Fill '/fill 916 91 -262 916 93 -262 elysiumwilds:vale_frame'  # the right side
Step '/item replace entity @s hotbar.0 with elysiumwilds:biome_compass'; MC-Focus | Out-Null; MC-Key '1'; Start-Sleep -Milliseconds 600
Step '/tp @s 914.5 90 -258.5 180 24'; Start-Sleep -Seconds 2      # the look again: chat and keys can nudge it
MC-Focus | Out-Null; MC-Shot "$Out\vale_1_ring.png" 0.5
Say "== the compass on a frame"
# No lit gate in the ring before the compass (see the header): one standing from an earlier run would
# pass the check after it whatever the compass did.
Ask '/execute unless block 914 91 -262 elysiumwilds:vale_portal' $Verdict
if ($script:Said -notmatch '^Test passed') { Bad "the ring's opening at $Anchor was not read as unlit before the compass, so its being lit after would not be this run's doing ($(Heard))"; Finish }
# The crosshair rests on the sill's front face (the block at 914 or 915, y 90, z -262); no mouse
# movement, since moving the captured cursor turns the view.
MC-Focus | Out-Null; MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$Out\vale_2_lit.png" 0.5
Ask '/execute if block 914 91 -262 elysiumwilds:vale_portal' $Verdict
# An unlit ring carries nobody, and an unread one is not stood in.
if ($script:Said -notmatch '^Test passed') { Bad "the ring did not light ($(Heard))"; Finish }
Say "== the deaths, counted"
# Made fresh, so it counts deaths whatever an older objective of that name counted (see the header).
$o = [regex]::Escape($Deaths)
if (-not (Sure "the stage's count of deaths could not be cleared" "/scoreboard objectives remove $Deaths" ('^(Removed objective \[' + $o + '\]|Unknown scoreboard objective ''' + $o + ''')$'))) { Finish }
if (-not (Sure "the stage's count of deaths could not be made" "/scoreboard objectives add $Deaths deathCount" ('^Created new objective \[' + $o + '\]$'))) { Finish }
if (-not (Sure "the stage's count of deaths could not be set to nought" "/scoreboard players set @s $Deaths 0" ('^Set \[' + $o + '\] for ' + [regex]::Escape($Me) + ' to 0$'))) { Finish }
$deaths0 = Deaths
if ($deaths0 -ne 0) { Bad "the stage's count of deaths read $deaths0 before the gate, not the nought it was set to ($(Heard))"; Finish }
Write-Host "  the stage's count of deaths is $deaths0 before the gate"
# And the world's count of this player's ways out to the spawn (see the header).
$record0 = Record
if ($null -eq $record0) { Bad "the world's record of trips (/elysium vale trips) could not be read before the gate ($(Heard)), so a way out to the world's spawn could not be told from the return ring"; Finish }
Write-Host "  the world's record counts $($record0.Spawned) way(s) out to the spawn for $Me before the gate"
Say "== through the gate"
# A player who walks through is carried the moment they stand in it, lands one block out from a
# ring the Vale builds for the way back, and is ignored by every gate for COOLDOWN_TICKS (3 s).
# The first run walked for three seconds: the first trip's biome search held the server some
# ten seconds, the client kept walking meanwhile, and on arrival the stale steps took the
# player straight back out through the return ring. So: stand in the gate (this client has
# autoJump off, and the sill is a block up), wait, then step back into the return ring on purpose.
Step '/gamemode survival'
# The verdict line says a survival player was carried, so that is asked, not taken from the step
# (16 Oct): a /gamemode typed into a screen, or whose letters came back after its marker, never ran.
Ask '/execute if entity @s[gamemode=survival]' $Verdict
if ($script:Said -notmatch '^Test passed') { Bad "the player was not read as in survival before the gate ($(Heard))"; Step '/gamemode creative' -Quiet; Finish }
# Inside the opening, still. The first trip's search, if this is one, waited for in the log rather
# than asked for (see the header): a command typed while the world changes leaves its letters to
# the game, and an S among them is a step back into the return ring - so the tp's own marker waits
# too (Stand), until ten seconds after the line, or five after the hold. Fourteen seconds was the
# old world's first trip, before the far stage and the settling (15 Oct); a fresh world's first
# trip runs them all.
$first = Stand '/tp @s 914.5 91 -261.5 180 0' 120 $Anchor
if ($first) {
    $t = @(TripsFor $Anchor $script:GateEcho)[0]
    Say "  the ring's first trip, after $($script:Took) s: its Vale side at $($t.Side) axis $($t.Axis), $($t.How)"
} else {
    Say "  no first-trip line for the ring at $Anchor after $($script:Took) s: it has been through before in this world, or the player was not carried"
}
# Asked, now, until the game says the Vale (answers.ps1, InVale): a read that answers only there.
$inVale = InVale 30
MC-Focus | Out-Null; MC-Shot "$Out\vale_3_arrived.png" 0.5
if (-not $inVale) {
    Bad "not carried into the Vale ($(Heard))"
    Step '/gamemode creative' -Quiet
    Finish
}
# Read only in the Vale (see the header): carried back home since the read above, it answers nothing.
Ask '/execute if dimension elysiumwilds:vale run data get entity @s Pos' $Data
$p = Coords $script:Said
if ($null -eq $p) { Bad "the landing was not read back in the Vale ($(Heard))" } else { $script:Landing = $p -join ' ' }
Ask '/execute if block ~ ~ ~ minecraft:air' $Verdict
Say "== and back"
# The landing faces away from the return ring, one block out: a short step back enters it.
Start-Sleep -Seconds 3                                                # the cooldown
MC-Focus | Out-Null; [Win32MC]::Key(0x53, $false); Start-Sleep -Milliseconds 700; [Win32MC]::Key(0x53, $true)   # S held 0.7 s: step back into the ring (Key's second argument is UP; the 1 Oct runs had it reversed and left the key held)
Start-Sleep -Seconds 6
Ask '/data get entity @s Dimension' $Data
$atHome = $script:Said -ceq ($Me + ' has the following entity data: "minecraft:overworld"')   # this player's whole answer (see the header)
if (-not $atHome) { Bad "the return ring did not carry the player home ($(Heard))" }
# Home by the ring, read in the overworld (see the header): within 3 blocks across, 2 up or down and
# 4 along of where the way back puts the player, so a short step or a drop off the landing is home.
Ask '/execute if dimension minecraft:overworld run data get entity @s Pos' $Data
if ($atHome) {
    $h = Coords $script:Said
    if ($null -eq $h) { Bad "the player was not read back at home in the overworld ($(Heard))" }
    elseif ([Math]::Abs($h[0] - 914.5) -gt 3 -or [Math]::Abs($h[1] - 91) -gt 2 -or [Math]::Abs($h[2] + 261) -gt 4) { Bad "home, but not by the overworld ring: at $($h -join ' '), so not carried by the return ring ($(Heard))" }
}
# Nor by a death or the mod's way out with no way in on record (see the header): either can put the
# player by the ring too, where the world's spawn is.
$deaths1 = Deaths
if ($deaths1 -ne $deaths0) {
    $now = if ($deaths1 -lt 0) { "nothing that could be read ($(Heard))" } else { "$deaths1" }
    Bad "the stage's count of deaths read $deaths0 before the gate and $now at home, so the player died on the way and was respawned, not carried home by the return ring"
}
if ((NoWayInSince $script:GateEcho) -gt 0) { Bad "the mod logged a way out of the Vale with no way in on record after the gate's fence, so the player was put at the world's spawn, not carried home by the return ring" }
# The world's own count, which no rollover loses, and the log's gaps, which can hide that line (see
# the header).
$record1 = Record
if ($null -eq $record1) { Bad "the world's record of trips (/elysium vale trips) could not be read at home ($(Heard)), so a way out to the world's spawn is not ruled out" }
elseif ($record1.Spawned -ne $record0.Spawned) { Bad "the world's record counts $($record0.Spawned) way(s) out of the Vale to the world's spawn for $Me before the gate and $($record1.Spawned) at home, so a gate put the player at the spawn for want of a way in on record, and the return ring did not carry them home" }
if (GapSince $script:GateEcho) { Bad "the log rolled over (or the client restarted) after the gate's fence, so lines written in between may be unread in the old log and the way home is not shown to be the return ring" }
MC-Focus | Out-Null; MC-Shot "$Out\vale_4_home_or_not.png" 0.5
Step '/gamemode creative' -Quiet
Finish
