param(
    [int]$Slot = -1,               # which footing ring; -1 takes the next one the world's own count gives
    [int]$CrossingSeconds = 120    # how long the first trip may hold the server: the search, the far stage, the settling
)
. "$PSScriptRoot\mc.ps1"
. "$PSScriptRoot\answers.ps1"   # how a command is sent and its answers read: every command here goes through it
# The Vale footing, live (PortalRules.SETTLE_IN_BLOCKS, 2 Oct): a fresh ring's first trip, which runs
# the whole search - the verdant vale, the dry spiral (DRY_SEARCH_BLOCKS), the far shore (FAR_DRY_*),
# the walk inland until the sill row and both landing rows are dry - and builds its own ring. Then
# where it lands is read back and the footing asserted: nothing wet where the player stands,
# underfoot neither wet nor a built mossy-stone-brick pad, the ring's own sill and both of its
# landing rows on dry ground with nothing wet over them; and it is photographed. The rules are held
# by the wilds validator and negtest_wilds_portal; this is the world under them. Written on 3 Oct as
# a scratchpad check that never ran (the Windows firewall prompt held the client), recovered from
# the session transcript and made a stage on 15 Oct, and on 16 Oct made to take what it knows of the
# trip from the game rather than from a list of its own. It ends on one line, VALE FOOTING OK or
# VALE FOOTING BAD with every failed check listed, and exits 0 or 1.
#
# A first trip, by the game's own word (16 Oct). A ring's first trip logs one line (ValeTravel,
# elysium-wilds), and no other trip logs anything like it:
#   Vale first trip: player NAME ring DIMENSION X Y Z side X Y Z axis x|z built|standing
# That line, for this player and this ring, read after the echo of the fence sent just before the
# gate step (answers.ps1, Stand), and backed by the world's own record of it (below), is the only
# proof this stage takes that the trip was the ring's first and ran the search. No line means it
# was not (the ring had been through before in this world), and a line ending "standing" means the
# search ended by a ring already standing in the Vale and used it rather than build: the footing
# there is not one a fresh search chose. Either is a failure, and the stage says to run it again
# (the count gives the next slot) or start a fresh world. The words are matched case-sensitively.
#
# The world's record (16 Oct, round 4; in the mod's own saved data since round 5). A log line alone
# is no proof: the integrated server logs some text as it is, line breaks and all, so a mob whose
# custom name holds line breaks round a copy of an earlier run's first-trip line writes that copy
# into the log as a whole line of its own when it dies, in either layout (attack_r4, F1v and F1n).
# So the mod keeps every first trip in the world too, where no log line can write: in ValeLinks, the
# saved data the ways into the Vale keep, how many first trips the world has had and the line the
# latest of them logged, read back by /elysium vale trips (answers.ps1, Record) as one line:
#   Vale trips: first N, out to spawn M, last LINE
# It is read before the gate and after the crossing, each with a marked Ask, and the line is taken
# only when the count went up by exactly one and LINE is that line, word for word ("none" before a
# world's first trip). Only a command can name a mob with line breaks, so the forger of F1v is an
# operator; in round 4 the record was command storage elysiumwilds:trips, and the same operator set
# it to agree with /data modify from a command block in the hold (round 5, OP1). No command writes
# the mod's saved data, and /elysium vale trips only reads it. The stage does not stand against an
# operator who sets out to fool it in other ways (answers.ps1 says how).
# Until 16 Oct the stages kept a site list in the world folder instead (elysium_vale_sites.txt: each
# ring filed before its trip, each landing kept after, every Vale side on record taken down before a
# trip) and took the client's "Loaded N advancements" for a change of world; three rounds of review
# found fourteen ways that let a run pass when the game had not done what it claimed, and the client
# logs that line only with an advancement update, which a change of world does not send. Both are
# gone.
#
# Which ring. Slot k's ring stands at x 913..916 + 32000 (k + 1), z -278, its anchor at
# 914 + 32000 (k + 1), 91, -278, with its own floor round it; so every slot's ring, and the vale
# stage's at 914 91 -262, are 32 000 blocks apart along x or more. A first trip looks at most
# PortalRules.ARRIVAL_SEARCH_BLOCKS (12 800) out for the verdant vale and a few hundred more for dry
# ground, so no two of these searches can end by each other's Vale side. The slot is the world's own
# count: a scoreboard objective, elysium_vale_slot, held by #footing, which the stage adds one to
# before the trip and reads back with a marked Ask; slot k is the count less one, and a count that
# cannot be made, added to or read back is a failure before any trip. -Slot k names one instead
# (the count is then left alone, so a later run may take that slot again: its trip then logs no
# line, and it fails). A fresh world counts from nought.
#
# The floor. The client's world is built new from reference/ on a rebuilt rig (client_up.ps1), and
# the stone platform the October runs stood on came with the old world. So the stage lays its own
# round the slot's ring: smooth stone at y 89 over x 880..940 + 32000 (k + 1), z -290..-250, and
# eight layers of air above it (61 x 41 x 8 = 20008 blocks, under fill's 32768) - the floor
# stage_vale_client.ps1 lays round its own ring. A chunk not generated yet answers "That position is
# not loaded", so a fill is asked again. The ring's opening is cleared and its frames set by Fill too,
# which wants a fill's own answer, and its gate cell is asked not to be lit before the compass (round
# 5, L1: as steps, any line but an error passed them).
#
# The geometry, from the line. The Vale side's anchor and axis are the line's, never worked out from
# where the player is read. Standing in the gate at z -277.5 is the south half of the anchor's block
# (PortalRules.side +1), so the Vale puts the player one block from its ring on the other side,
# against ValePortal.out (south along x, east along z), facing on (ValeTravel: atBottomCenterOf,
# facing). The player's Pos and Rotation are each read as /execute if dimension elysiumwilds:vale run
# data get ..., which answers nothing outside the Vale (15 Oct, round 2), and Pos must be the bottom
# centre of that landing block and the facing that way, or the run fails: a player moved since -
# pushed by a mob, hit, carried by a current - or put down anywhere else is not where this trip put
# them. Every block is then asked by absolute position in elysiumwilds:vale: the ring stands at the
# anchor (a lit gate there, none before it across the ring: PortalRules.Ring's least-across cell),
# the sill is the frame row under the opening (anchor y - 1), the landings are the rows either side
# of it, through the ring. Pos and Rotation are taken only as this player's whole answer, matched
# from end to end (round 5, PD1): outside the Vale they say nothing of their own, and a pet's death
# message shaped by two anvil names like the end of one was taken for it.
#
# Wet, by the mod's own test (ValeTravel.wet, PortalRules.dry): a fluid stands in the block -
# water, lava, or a waterlogged block, seagrass or kelp, whose block is not water but whose fluid
# is. So each block the footing asks about - the one the player stands in, under it, the sill's
# underside, both landings' floors and the block over each floor, where a player stands - is asked
# twice: unless block ... minecraft:water, and if predicate of a location_check whose fluid is
# minecraft:empty. A position not loaded fails that predicate (its fluid test asks isLoaded first),
# and an error is no pass, so neither passes a block it did not read. A floor must also hold
# something: not air, by the tag #minecraft:air (air, cave_air, void_air - three blocks, and a
# structure's hollow is cave_air). The predicate is asked once where its answer is known, for water
# in the air over the player's head (round 3): a predicate the game took but read as nothing would
# pass everywhere, seagrass with it, and this one would fail.
#
# The traps (stage_vale_client.ps1's header has the rest): face north (yaw 180) and a little down
# (pitch 24) so the crosshair rests on the sill, since the compass used on air opens its chooser,
# which takes one ESC to clear what was typed into its search and a second to close; stand in the
# gate rather than walk; and send nothing while the world changes - the first trip holds the
# server for seconds, a key that lands in the game then is a step, and a step back from the landing
# is the return ring. So the first trip is waited for in the log, and only then is the game asked
# where the player is. The game is fronted before every step. A step that ever needs a held key:
# [Win32MC]::Key(vk, $up) - the second argument is UP.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$Vale = 'elysiumwilds:vale'
$RingZ = -278
$Spacing = 32000                  # between slots' rings along x, and from the vale stage's ring
$Slots = 900                      # slot 899's ring is at x 28 800 914, inside the world border (29 999 984)
$Objective = 'elysium_vale_slot'  # the world's own count of slots taken
$Holder = '#footing'
$Store = '/elysium vale trips'    # the world's own record of its first trips, as the mod reads it back (see the header)
$inv = [Globalization.CultureInfo]::InvariantCulture
$script:Bad = New-Object System.Collections.ArrayList
$script:Said = ''
$script:Why = ''
$script:Landing = 'none'
$script:ValeSide = 'none'
$anchor = 'none'

function Say([string]$s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
function Bad([string]$what) { [void]$script:Bad.Add($what); Write-Host "    BAD  $what" }
function Heard { if ($script:Said) { $script:Said } else { $script:Why } }
# Whose answer a line is (answers.ps1, 15 Oct): every command here - Ask, Fill, Step, and Stand for
# the gate - is typed once and followed by a marker of its own, /tellraw @s "elysium mark <pid> <n>",
# and judged only on the chat lines between the marker before it and its own: the server answers in
# order, so those lines are its answers and nobody else's. A marker not back in ten seconds fails its
# command, and a lone marker then shuts in whatever it still says; a read wants exactly one answer,
# so a command run twice fails, and player chat is no answer. A step wants an answer too, and one
# with none before its marker failed (16 Oct): only /gamemode creative is sent -Quiet, since a game
# mode the player is already in answers nothing. A new log fails every command still open. Nothing
# here claims the trip was made in survival (the gate carries any player), so the survival step's
# own answer is all that is asked of it. (The waiting list this replaced gave a late answer to the
# oldest command that could take it, and was fooled when MC-Cmd, seeing an earlier command's stale
# garble, typed a verdict twice: the second "Test passed" went to the next verdict, whose own answer
# was "Test failed".)
# A verdict, phrased so that "Test passed" is the good answer: the command's own one answer. A
# "Test failed", an error, no answer or more than one answer is each a failure, not a skip.
function Expect([string]$what, [string]$cmd) {
    Ask $cmd $Verdict
    if ($script:Said -match '^Test passed') { Write-Host "    ok   $what" } elseif ($script:Said) { Bad "$what ($($script:Said))" } else { Bad "$what ($($script:Why))" }
}
# Beside the verdicts: what the ground is, not whether it passes. A note that is neither yes nor no
# was not read at all (an error says the command itself is wrong), and that is a failure.
function Note([string]$what, [string]$cmd) {
    Ask $cmd $Verdict
    if ($script:Said -match '^Test passed') { Write-Host "    yes  $what" }
    elseif ($script:Said -match '^Test failed') { Write-Host "    no   $what" }
    elseif ($script:Said) { Bad "a note could not be read: $what ($($script:Said))" }
    else { Bad "a note could not be read: $what ($($script:Why))" }
}
# The world's own count of footing slots, one added and read back: the slot to take, or -1 (and a
# failure said) when the count could not be made, added to or read.
function NextSlot {
    $o = [regex]::Escape($Objective); $h = [regex]::Escape($Holder)
    $made = '^(Created new objective \[' + $o + '\]|An objective already exists by that name)$'
    Ask "/scoreboard objectives add $Objective dummy" $made
    if ($script:Said -notmatch $made) { Bad "the world's count of footing slots could not be made ($(Heard)), so no slot was taken"; return -1 }
    Ask "/scoreboard players add $Holder $Objective 1" ('^Added 1 to \[' + $o + '\] for ' + $h + ' \(now (\d+)\)$')
    if (-not ($script:Said -match ('^Added 1 to \[' + $o + '\] for ' + $h + ' \(now (\d+)\)$'))) { Bad "the world's count of footing slots was not added to ($(Heard)), so no slot was taken"; return -1 }
    $now = [int]$Matches[1]
    Ask "/scoreboard players get $Holder $Objective" ('^' + $h + ' has (\d+) \[' + $o + '\]$')
    if (-not ($script:Said -match ('^' + $h + ' has (\d+) \[' + $o + '\]$'))) { Bad "the world's count of footing slots was not read back ($(Heard)), so no slot was taken"; return -1 }
    $n = [int]$Matches[1]
    if ($n -ne $now -or $n -lt 1) { Bad "the world's count of footing slots read back as $n where adding one said $now, so no slot was taken"; return -1 }
    if ($n -gt $Slots) { Bad "this world has taken all $Slots footing slots (its count is $n): start a fresh world (elysium-run-e.ps1 -FreshWorld)"; return -1 }
    Write-Host "  the world's count of footing slots is now $($n), so slot $($n - 1)"
    $n - 1
}
function Home { Step '/gamemode creative' -Quiet; Step "/execute in minecraft:overworld run tp @s $sx 90 $sz 180 24" }
function Finish {
    Say "== the verdict"
    if ($script:Bad.Count -eq 0) { Write-Host "VALE FOOTING OK: ring $Slot (anchor $anchor), its first trip's Vale side built at $script:ValeSide, landed at $script:Landing on dry ground, no pad, sill and both landings dry; the trip the world's record of first trips counted"; exit 0 }
    Write-Host "VALE FOOTING BAD ($($script:Bad.Count)): ring $Slot (anchor $anchor), Vale side $script:ValeSide, landing $script:Landing"
    foreach ($b in $script:Bad) { Write-Host "  - $b" }
    exit 1
}
if ($Slot -ge $Slots) { Write-Host "-Slot runs 0..$($Slots - 1): slot $($Slots - 1)'s ring is the last inside the world border"; exit 1 }

Say "== the game"
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
Resume
Step '/gamemode creative' -Quiet

Say "== which ring"
if ($Slot -lt 0) {
    $Slot = NextSlot
    if ($Slot -lt 0) { Finish }
} else { Write-Host "  slot $Slot, named (the world's count is left alone)" }
$D = $Spacing * ($Slot + 1)
$x0 = 913 + $D; $x1 = $x0 + 1; $x2 = $x0 + 2; $x3 = $x0 + 3
$anchor = "$x1 91 $RingZ"                  # the opening's lowest, westmost cell: what the Vale side is filed under
$sx = $x1 + 0.5; $sz = $RingZ + 3.5        # where the player stands to light it
$gz = $RingZ + 0.5                         # and where, in the opening, to stand to go through
$fw = 880 + $D; $fe = 940 + $D             # the floor's west and east edges
Say "== footing ring $Slot, posts x $x0 and $x3 at z $RingZ, anchor $anchor"
Step '/time set noon'
Step "/execute in minecraft:overworld run tp @s $sx 90 $sz 180 24"; Start-Sleep -Seconds 3
Step '/clear @s'

Say "== the floor"
Fill "/fill $fw 89 -290 $fe 89 -250 minecraft:smooth_stone"
Fill "/fill $fw 90 -290 $fe 97 -250 minecraft:air"
Step "/tp @s $sx 90 $sz 180 24"; Start-Sleep -Seconds 1

Say "== the ring"
# Four wide and five tall round a 2 x 3 opening, corners left out as the rules allow.
# Each a Fill, which wants the fill's own answer (see the header).
Fill "/fill $x0 90 $RingZ $x3 94 $RingZ minecraft:air"
Fill "/fill $x1 90 $RingZ $x2 90 $RingZ elysiumwilds:vale_frame"      # the sill
Fill "/fill $x1 94 $RingZ $x2 94 $RingZ elysiumwilds:vale_frame"      # the lintel
Fill "/fill $x0 91 $RingZ $x0 93 $RingZ elysiumwilds:vale_frame"      # the west side
Fill "/fill $x3 91 $RingZ $x3 93 $RingZ elysiumwilds:vale_frame"      # the east side
Step '/item replace entity @s hotbar.0 with elysiumwilds:biome_compass'
MC-Focus | Out-Null; MC-Key '1'; Start-Sleep -Milliseconds 600
Step "/tp @s $sx 90 $sz 180 24"; Start-Sleep -Seconds 2               # the look again: chat and keys can nudge it
MC-Focus | Out-Null; MC-Shot "$Out\vale_footing_1_ring.png" 0.5

Say "== the compass on the sill"
# No lit gate in the ring before the compass (see the header).
Ask "/execute unless block $x1 91 $RingZ elysiumwilds:vale_portal" $Verdict
if ($script:Said -notmatch '^Test passed') { Bad "the ring's opening at $anchor was not read as unlit before the compass, so its being lit after would not be this run's doing ($(Heard))"; Finish }
# No mouse movement: moving the captured cursor turns the view. A miss lands on air and opens the
# chooser with the check typed into its search: two ESCs, the game asked again, and one more try.
$lit = $false
for ($try = 0; $try -lt 2 -and -not $lit; $try++) {
    if ($try -gt 0) {
        MC-Focus | Out-Null; MC-Key 'ESC'; Start-Sleep -Milliseconds 700
        MC-Focus | Out-Null; MC-Key 'ESC'; Start-Sleep -Milliseconds 700
        Resume
        Step "/tp @s $sx 90 $sz 180 24"; Start-Sleep -Seconds 2
    }
    MC-Focus | Out-Null; MC-RClick; Start-Sleep -Seconds 2
    Ask "/execute if block $x1 91 $RingZ elysiumwilds:vale_portal" $Verdict
    $lit = $script:Said -match '^Test passed'
}
if (-not $lit) { Bad "the ring at $anchor did not light"; Finish }

Say "== through the gate"
# The world's record of first trips before the gate (see the header): this trip must add one.
$before = Record
if ($null -eq $before) { Bad "the world's record of first trips ($Store) could not be read before the gate ($(Heard)), so no first-trip line can be held against it"; Finish }
$tripsBefore = $before.First
Write-Host "  the world's record of first trips counts $tripsBefore before the gate"
Step '/gamemode survival'
# Inside the opening, still; the first-trip line waited for, then the landing has ten seconds (the
# client loads the Vale round it; the cooldown) before the step's marker (answers.ps1, Stand).
$first = Stand "/tp @s $sx 91 $gz 180 0" $CrossingSeconds $anchor
$trips = @(TripsFor $anchor $script:GateEcho)
foreach ($t in @($script:TripsRead)) {
    if ($t.At -gt $script:GateEcho -and -not ($t.Player -ceq $Me -and $t.Dim -ceq 'minecraft:overworld' -and $t.Ring -ceq $anchor)) { Write-Host "  a first-trip line that is not this one, passed over: player $($t.Player), ring $($t.Dim) $($t.Ring)" }
}
if (-not $first -or $trips.Count -eq 0) {
    Bad "no first-trip line for $Me through the ring at $anchor after $($script:Took) s, so this trip was not that ring's first and its footing is not one a fresh search chose: run the stage again (the world's count gives the next slot), or start a fresh world (elysium-run-e.ps1 -FreshWorld)"
    MC-Focus | Out-Null; MC-Shot "$Out\vale_footing_2_landed.png" 0.5
    Home; Finish
}
if ($trips.Count -gt 1) {
    Bad "$($trips.Count) first-trip lines for $Me through the ring at $anchor, where a ring has one: its record was lost between them, so which trip this was is not known"
    Home; Finish
}
$t = $trips[0]
$script:ValeSide = "$($t.Side) axis $($t.Axis)"
Say "  the ring's first trip, after $($script:Took) s: its Vale side at $($t.Side) axis $($t.Axis), $($t.How)"
# Held against the world's record (see the header): one first trip more than before the gate, and
# its last line, word for word, the one read.
$after = Record
if ($null -eq $after) {
    Bad "the world's record of first trips ($Store) could not be read after the crossing ($(Heard)), so the first-trip line read ($($t.Text)) is not shown to be one the game kept"
    Home; Finish
}
$tripsAfter = $after.First
$last = $after.Last
if ($tripsAfter -ne $tripsBefore + 1) {
    Bad "the world's record of first trips ($Store) counted $tripsBefore before the gate and $tripsAfter after the crossing, where a first trip adds one: the first-trip line read ($($t.Text)) is not one the game kept, so something else wrote it into the log (a custom name holding line breaks, say), and this trip is not shown to be the ring's first"
    Home; Finish
}
if ($last -cne $t.Text) {
    $kept = if ($last -ceq '') { 'nothing' } else { '"' + $last + '"' }
    Bad "the world's record of first trips ($Store) keeps $kept as its last line, not the first-trip line read ($($t.Text)), so that line is not the trip the game counted"
    Home; Finish
}
Write-Host "  the world's record of first trips counts $tripsAfter after the crossing, and its last line is the one read"
if ($t.How -cne 'built') {
    Bad "the first trip through the ring at $anchor used a ring already standing in the Vale at $($t.Side) rather than build one, so its footing is not one a fresh search chose: run the stage again (the world's count gives the next slot), or start a fresh world (elysium-run-e.ps1 -FreshWorld)"
    Home; Finish
}
$sp = $t.Side -split ' '
$ax = [int]$sp[0]; $ay = [int]$sp[1]; $az = [int]$sp[2]
# Across the ring and out through it (ValePortal.across, out); the landing is one block against out,
# facing on (see the header).
if ($t.Axis -ceq 'x') { $cx = 1; $cz = 0; $ox = 0; $oz = 1 } elseif ($t.Axis -ceq 'z') { $cx = 0; $cz = 1; $ox = 1; $oz = 0 } else { Bad "the first-trip line's axis is $($t.Axis), neither x nor z"; Home; Finish }
$fx = 0 - $ox; $fz = 0 - $oz
$lx = $ax + $fx; $ly = $ay; $lz = $az + $fz

Say "== where it lands"
if (-not (InVale 30)) { Bad "not carried into the Vale ($(Heard)), though the ring's first trip was logged"; Home; Finish }
# Each read only in the Vale (see the header): put back home since the read above, it answers nothing.
Ask "/execute if dimension $Vale run data get entity @s Pos" $Data
$pos = [regex]::Match($script:Said, '^' + [regex]::Escape($Me) + ' has the following entity data: \[(-?[0-9][0-9.E-]*)d, (-?[0-9][0-9.E-]*)d, (-?[0-9][0-9.E-]*)d\]$')   # this player's whole answer (see the header)
Ask "/execute if dimension $Vale run data get entity @s Rotation" $Data
$rot = [regex]::Match($script:Said, '^' + [regex]::Escape($Me) + ' has the following entity data: \[(-?[0-9][0-9.E-]*)f, (-?[0-9][0-9.E-]*)f\]$')
MC-Focus | Out-Null; MC-Shot "$Out\vale_footing_2_landed.png" 0.5     # as it stands, facing on
Step '/gamemode creative' -Quiet           # the reads take a minute; nothing in the Vale gets a hit in meanwhile
$st = [Globalization.NumberStyles]::Float
$posX = 0.0; $posY = 0.0; $posZ = 0.0; $yaw = 0.0
$read = $pos.Success -and $rot.Success -and [double]::TryParse($pos.Groups[1].Value, $st, $inv, [ref]$posX) -and [double]::TryParse($pos.Groups[2].Value, $st, $inv, [ref]$posY) -and [double]::TryParse($pos.Groups[3].Value, $st, $inv, [ref]$posZ) -and [double]::TryParse($rot.Groups[1].Value, $st, $inv, [ref]$yaw)
if (-not $read) { Bad "the landing could not be read back in the Vale (Pos, Rotation)"; Home; Finish }
# Set down at the bottom centre of the landing block the line implies, facing on, or moved since or
# put down elsewhere (see the header).
$wx = $lx + 0.5; $wz = $lz + 0.5
if ([Math]::Abs($posX - $wx) -gt 0.001 -or [Math]::Abs($posY - $ly) -gt 0.001 -or [Math]::Abs($posZ - $wz) -gt 0.001) {
    Bad "the player was read at $posX $posY $posZ, not $wx $ly $wz, the bottom centre of the landing the first-trip line implies (its Vale side $($t.Side) axis $($t.Axis)): moved since landing, or put down elsewhere"
    Home; Finish
}
$r = $yaw * [Math]::PI / 180      # yaw 0 is south (+z), 90 west, 180 north, 270 east
$hx = 0 - [int][Math]::Round([Math]::Sin($r)); $hz = [int][Math]::Round([Math]::Cos($r))
if ($hx -ne $fx -or $hz -ne $fz) { Bad "the player faces yaw $yaw, not on away from the ring the first-trip line names: turned since landing"; Home; Finish }
$script:Landing = "$lx $ly $lz"

Say "== the footing: landing $lx $ly $lz facing yaw $yaw, ring anchor $ax $ay $az (from the first-trip line)"
Expect "the ring stands behind the landing (a lit gate at its anchor $ax $ay $az)" "/execute in $Vale if block $ax $ay $az elysiumwilds:vale_portal"
# The anchor is the opening's lowest cell, then its least across (PortalRules.Ring): the cell before
# it across the ring is a frame, never the gate. (The sill checks below put a frame under it.)
$bx = $ax - $cx; $bz = $az - $cz
Expect "the anchor is the opening's least-across cell (no gate before it, at $bx $ay $bz)" "/execute in $Vale unless block $bx $ay $bz elysiumwilds:vale_portal"
# Not wet (see the header): no water in the block, and no fluid of any kind - the predicate passes
# only where the block's fluid is none, so a waterlogged block, seagrass, kelp or lava fails it.
$NoFluid = '{condition:"minecraft:location_check",predicate:{fluid:{fluids:"minecraft:empty"}}}'
# And the predicate itself, where its answer is known (see the header): no water over the player's head.
$Water = '{condition:"minecraft:location_check",predicate:{fluid:{fluids:"minecraft:water"}}}'
$hy = $ly + 2
Expect "the fluid test reads its predicate: no water found in the air over the player's head ($lx $hy $lz)" "/execute in $Vale positioned $lx $hy $lz unless predicate $Water"
function Unwet([string]$what, [int]$x, [int]$y, [int]$z) {
    Expect "$what is not water" "/execute in $Vale unless block $x $y $z minecraft:water"
    Expect "$what holds no fluid" "/execute in $Vale positioned $x $y $z if predicate $NoFluid"
}
Unwet "the landing itself ($lx $ly $lz), where the player stands," $lx $ly $lz
$u = $ly - 1
Unwet "underfoot ($lx $u $lz)" $lx $u $lz
Expect "underfoot ($lx $u $lz) is not a built pad of mossy stone brick" "/execute in $Vale unless block $lx $u $lz minecraft:mossy_stone_bricks"
Note "underfoot is dirt or grass" "/execute in $Vale if block $lx $u $lz #minecraft:dirt"
Note "underfoot is sand" "/execute in $Vale if block $lx $u $lz #minecraft:sand"
Note "underfoot is stone" "/execute in $Vale if block $lx $u $lz #minecraft:base_stone_overworld"
Note "the landing is in the verdant vale" "/execute in $Vale if biome $lx $ly $lz elysiumwilds:vale_verdant_vale"   # ValeRules.Biome.id(): "vale_" + its name
# Dry ground, as far as a block can be asked: nothing wet in it, and something in it.
function Dry([string]$what, [int]$x, [int]$y, [int]$z) {
    Unwet $what $x $y $z
    Expect "$what is not air" "/execute in $Vale unless block $x $y $z #minecraft:air"
}
$sy = $ay - 1
for ($a = 0; $a -lt 2; $a++) {
    $x = $ax + $cx * $a; $z = $az + $cz * $a
    Expect "the sill at $x $sy $z is the ring's own frame" "/execute in $Vale if block $x $sy $z elysiumwilds:vale_frame"
    Dry "under the sill, $x $($sy - 1) $z," $x ($sy - 1) $z
}
foreach ($row in 1, -1) {
    $side = if ($row -eq 1) { 'the near landing' } else { 'the far landing' }
    for ($a = 0; $a -lt 2; $a++) {
        $x = $ax + $cx * $a + $fx * $row; $z = $az + $cz * $a + $fz * $row
        Dry "$side's floor, $x $sy $z," $x $sy $z
        Expect "$side's floor, $x $sy $z, is ground, not a built pad" "/execute in $Vale unless block $x $sy $z minecraft:mossy_stone_bricks"
        # Where a player stands on it: on a seabed the floors are dry sand and this block is the sea.
        Unwet "$side, over its floor ($x $($sy + 1) $z)," $x ($sy + 1) $z
    }
}

Say "== the footing photographed"
# A spectator does not fall and is not carried by a gate; both views look at the sill's middle.
Step '/gamemode spectator'
$tx = $ax + 0.5 + $cx * 0.5; $ty = $ay - 0.5; $tz = $az + 0.5 + $cz * 0.5
$py = $ly + 3
$px = $lx + 0.5 + $fx * 3 - $cx * 2; $pz = $lz + 0.5 + $fz * 3 - $cz * 2      # in front, off to one side
Step "/execute in $Vale run tp @s $px $py $pz facing $tx $ty $tz"; Start-Sleep -Seconds 2
MC-Focus | Out-Null; MC-Shot "$Out\vale_footing_3_front.png" 0.5
$px = $ax + 0.5 - $fx * 4 + $cx * 3; $pz = $az + 0.5 - $fz * 4 + $cz * 3      # behind, off to the other
Step "/execute in $Vale run tp @s $px $py $pz facing $tx $ty $tz"; Start-Sleep -Seconds 2
MC-Focus | Out-Null; MC-Shot "$Out\vale_footing_4_back.png" 0.5
Home
Finish
