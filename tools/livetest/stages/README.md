# Client stages

The dedicated server answers questions over RCON (`../livetest.py`, or one
command at a time with `../rcon.py`); these stages drive the test client at
`C:\testingbuilds\Elysium` for what only a player reaches, and take the
screenshots. Both rigs live under `ELYSIUM_TEST_ROOT` (default
`C:\testingbuilds`), which `mc.ps1` reads; the E: drive that held them was
lost, and on 14 October 2026 the rigs were rebuilt on C:. `mc.ps1` is the
driver: it finds the Minecraft window, sends keys and mouse through `SendInput` (only while that
window is foreground), reads the chat back out of `logs/latest.log`, and
captures the window. `MC-Resume` gets the game answering first: it asks the
clock, presses ESC if the pause menu is up, and clicks Respawn if the world was
saved with a dead player.

Run one from PowerShell with the client already in its world (`client_up.ps1`
launches it on the installed jar if it is not up and waits for the join):

    powershell -NoProfile -ExecutionPolicy Bypass -File stage_mechanics_client.ps1

A stage is only as new as the jar the client booted with: rerun the launcher
after every install (`bash ../install.sh` puts a build on both rigs).
Screenshots land beside the script, or in
`$env:ELYSIUM_SHOTS` when set.

* `stage_runs_client.ps1` - the runs' plates and the dye rule, by hand placement.
* `stage_mechanics_client.ps1` - relic passives, axe stripping, grades, the
  reactor throttle, the cart's hold, capturing, budding and crop growth, the
  rift key and portal, the grave (CONVERSION.md 16.4).
* `stage_firstjoin_client.ps1` - a fresh world joined as a new player: the
  kit, the declaration screen (ESC refused, a choice recorded, the sheet
  after), the character commands, and a death in deep water keeping its grave
  (POLISH.md P3). It remakes the client's world through `elysium-run-e.ps1
  -FreshWorld`, which copies a reference `level.dat` through `StripPlayer.java`
  (player removed, spawn set on land for this seed). Both live here with it;
  the launcher finds the client under `ELYSIUM_TEST_ROOT` and the jars in
  the repository's own `dist`.
* `stage_screens_client.ps1` - every block that opens a menu, opened once and
  photographed with a verdict, then two minutes of night with the natural
  spawns counted around the player.
* `stage_creatures_client.ps1` - the creatures on their body plans, summoned in
  two rows and photographed standing and moving (POLISH.md P10).
* `stage_jei_client.ps1` - with a JEI jar dropped beside the seven, the item
  panel and a machine's own recipe category from the library's plugin
  (POLISH.md P6). Take the jar out again afterwards.
* `stage_seven_client.ps1` - the pack as ours alone (26 Sep): the world opens
  and a thing from each corner of it still works; it is the stage that found
  the Emerald Bastion chunk crash (CONVERSION.md §16.7).
* `stage_rocket_client.ps1` - a rocket built, fuelled, boarded and launched,
  the countdown and the landing (28-29 Sep; the launch key is H).
* `stage_almanac_client.ps1` - the quest book: a step finished by holding its
  item, the three pages, a reward taken from the step's row (29 Sep).
* `stage_web_client.ps1` - the Ledger Web tab: the three rings and the
  frontier, a road out to a frontier node taken in survival (29 Sep).
* `stage_vale_client.ps1` - the way into the Vale by hand: a ring of frames
  lit by the compass, a survival player carried through and back (1 Oct;
  the survival is asked before the gate since 16 Oct).
  Its header lists the traps it found: face north, aim at the sill, stand in
  the gate rather than walk, and the driver's key flag is UP. It ends on
  `VALE OK` or `VALE BAD` with each failed check, exit 0 or 1. It needs no
  first trip: a ring that has been through before logs no first-trip line, and
  the two-minute hold then runs its course before the game is asked. Home is
  read by the ring and, since 16 Oct (round 4), backed by more than where the
  player stands, since a respawn or the mod's way out to the world's spawn can
  put them there too: the stage's own deathCount objective
  (`elysium_vale_deaths`, made fresh and set to nought) is read before the gate
  and at home and must not move, and the mod's line for this player's way out
  with no way in on record, read after the gate's fence, is a failure. Since
  round 5 the world's own count of this player's ways out to the spawn
  (`/elysium vale trips`, below) is read before the gate and at home and must
  not move either, and a new log begun after the gate's fence is a failure in
  itself, since a log that rolled over can hold that line unread. The ring is
  cleared and built with fills that must say they filled, and its gate cell
  is read unlit before the compass, so an earlier run's gate cannot pass for
  this run's.
* `stage_vale_footing_client.ps1` - the Vale footing (15 Oct, from the 3 Oct
  check that never ran): a fresh ring's first trip, so the search, the far
  shore and the walk inland all run; then the landing is read back and
  asserted: underfoot neither water nor a built mossy-stone-brick pad, the
  ring's own sill and both landing rows on dry ground; photographed from the
  landing and from either side (`vale_footing_1-4`). It ends on
  `VALE FOOTING OK` or `VALE FOOTING BAD` with each failed check, exit 0 or 1.
  A first trip is proven by the mod's own first-trip line (16 Oct), held
  against the world's own record of first trips (round 4): the mod keeps the
  count of first trips and the latest one's line, word for word, where no log
  line can write, and the stage reads them before the gate and after the
  crossing, taking the line only when the count went up by one and the line
  kept is that line. Since round 5 the record is the mod's own saved data
  (ValeLinks), read with `/elysium vale trips`; it was command storage
  `elysiumwilds:trips`, which `/data modify` writes. No line, or one saying the Vale side
  was a ring already standing (the settling's own word, from a ring it found
  by a lit gate), is a failure that says to run again or start a fresh world. The Vale side's
  anchor and axis come from the line, and the player must be read at the
  bottom centre of the landing they imply, facing on. The slot comes from the
  world's own count, a scoreboard objective (`elysium_vale_slot`, held by
  `#footing`) the stage adds one to and reads back before the trip; slot k's
  ring stands at x 913..916 + 32000 (k + 1), z -278, with its own floor, so
  no two slots' searches (12 800 blocks out at most) can meet, nor one meet
  the vale stage's ring. `-Slot k` names one and leaves the count alone.

Both Vale stages lay their own floor round their ring (smooth stone at y 89
over x 880..940, z -290..-250, the footing stage's shifted 32000 (k + 1) east
with its slot, eight layers of air above), since the client's world is built
new from `reference/` and the platform the October runs stood on went with
the old one. Neither keeps anything in the world folder: the site list
(`elysium_vale_sites.txt`) they kept until 16 Oct is gone, with every filing
and take-down, after three rounds of review found fourteen ways it let a run
pass when the game had not done what it claimed. Both send every command through `answers.ps1` (15 Oct): each
command is typed once and followed by its own marker, `/tellraw @s "elysium
mark <pid> <n>"`, and is judged only on the chat lines between the marker
before it and its own, since the server answers in order. A marker not back in
ten seconds fails its command and a lone marker then shuts in what it still
says; a read wants exactly one answer, so a command run twice fails; player
chat is no answer; the log is read by byte offset, up to its last whole line.
`mc.ps1`'s `MC-Cmd`, which the other stages still use, is not called: its retry
on a stale garble once ran a verdict twice and the late second pass was read
as the next verdict's. Nor, since round 5, is `MC-CmdOnce`: `answers.ps1`
types each command itself, asking which window is in front before every key,
and stops the stage the moment it is not the game, RETURN never pressed. A key
sent while a console window had the focus for a moment went to that window,
and the command that reached the server, a digit short, tested another block.

Since 16 Oct a step (a command whose answer is not read) must answer too: one
with nothing before its marker, such as a command typed into a screen or one
whose letters came back late, failed. Only `/gamemode creative` is sent
`-Quiet`, since a game mode the player already has answers nothing; and the
vale stage, whose OK line says a survival player was carried, asks
`/execute if entity @s[gamemode=survival]` before the gate and stops on
anything but a pass. Only the client's chat-component lines are chat: a
player can type any text, and the integrated server logs every chat message on
its own thread. Only `<ElysiumTester>` lines are the player's own garbled
commands.

The crossing, since 16 Oct, is the game's own word. On a ring's first trip
elysium-wilds logs one INFO line (ValeTravel):
`Vale first trip: player NAME ring DIMENSION X Y Z side X Y Z axis x|z built|standing`.
The gate step waits for that line, for this player and this ring, read after
the echo of a fence sent just before the step, and is never typed again once
it is read; then the game is asked where the player is, with
`/execute if dimension elysiumwilds:vale run data get entity @s Dimension`,
until it says the Vale. The line is matched only as a Server thread INFO line
whose message starts right after its `]: `: from `[ElysiumWilds/]` in
NeoForge's own log layout, and with no logger in the vanilla launcher's
`client-1.12.xml` layout, which the test client's `launch.ps1` passes and so
is what its `latest.log` has. A player's chat is logged as `<NAME> text` on
the server thread and as `[CHAT]` on the render thread, so it is never the
line. The integrated server does log some text as it is, line breaks and all
(a named mob's death, a `/say` naming it), so a custom name can write a whole
line of its own, a first-trip line or a `[CHAT]` line included (round 4): the
footing stage's record of first trips is the answer to that, and as a guard
besides, in a log whose lines end CR LF (log4j on Windows) a line after one
that ended in a bare LF is inside a message and is read as nothing. Which
line end a log has is decided from every line end in its last 64 KiB when
reading starts, and kept when the log starts anew (round 5): one line alone
can be the inside of a message being written, or a new log's first event, a
named mob's death ending in its name's bare LF. Every
pattern is matched case-sensitively, and every kind of answer is matched whole,
the player's own reads by the player's name (round 5: a pet's death message,
its name and its killer's given with an anvil, was taken for the Vale's
answer). The client's `Loaded N advancements` is not read: it comes only with an
advancement update, and a change of world sends none. A new log (the midnight
rollover, or a restarted client) is told by its first bytes and by the bytes
before the read offset, not by its length; it is read from its start, and
every command still open is failed, never credited.

The world's record of its trips, since round 5: elysium-wilds keeps in
ValeLinks, its own saved data, how many first trips the world has had, the
latest one's line, and for each player how many ways out of the Vale put them
at the world's spawn for want of a way in on record; `/elysium vale trips`
(operators) answers it in one line, `Vale trips: first N, out to spawn M, last
LINE`, and writes nothing. The stages do not stand against an operator who
sets out to fool them (one can move the player, set blocks, or write whole lines
into the log past every guard with a CR LF in a mob's name); `answers.ps1` says
what they do stand against.

Stages from the 26-28 September sessions, kept here because the by-hand run
PLAYTEST.md still owes needs their rigs (none rerun since; expect anchors and
coordinates to want a look):

* `stage_settle_client.ps1` and `stage_days_client.ps1` - a self-founding
  colony in a fresh world, then days skipped at Dunfield so the town raises
  its hall and huts by itself (the colony day-cycle boxes).
* `stage_report_client.ps1` - the colony report at Dunwell and the natural
  listing.
* `stage_kitchen_client.ps1` and `stage_stations_client.ps1` - the kitchen,
  the pots and the three stations' models.
* `stage_augment_client.ps1` - a sword through the reforging table and into
  the augmenting table.
* `stage_features_client.ps1` - Cordon drops from loot, found trinkets, an
  importer at work, a rod that catches.
* `stage_agent8_client.ps1` - creatures by kindred and court, comforts
  fittings, arcana budding blocks.

Pitfalls the creature stage taught (28 Sep):

* A `/fill` over 32768 blocks does nothing and says so only in chat: split
  the air fill into slabs.
* Yaw 0 faces south (+z); the rows sit south of the camera, so `tp ... 0 <pitch>`.
* Undead kinds burn by day and are dead before the close-ups: night plus
  night vision, not noon.
* Negative coordinates floor the other way: a summon at `z -212.5` is in
  block `-213`, not `-212`. A fish summoned "into" a one-block hole at
  `-212` sat inside the stone beside it, suffocating and "out of water",
  which looked exactly like a fish bug and was not one. Sink a 3x3 pool.
* `Select-Object -First` on the stage's output ends the pipeline and the
  stage with it; redirect to a log and read that.

`stage_menus_client.ps1` puts hands on the ten menus that need them
(POLISH.md P12): a station placed, the things given, the menu opened, every
hotbar slot shift-clicked in (so nothing has to know where `/give` or a closed
menu put a thing), the control pressed at its window point, ESC, and the
inventory read back with `/data get`. Geometry: `guiScale:0` at 1600x900 is
GUI scale 3, so leftPos is 179, topPos (300 - imageHeight) / 2, and a gui point
is three window pixels; a shift-click is the shift key held around a click.
The Curios screen opens on G; a piece needs affixes before the augmenting
table has anything to raise (the armoury's reforging table gives it some).
It found the two gaps in CONVERSION.md §16.8.

The reference world (`reference/`): the dedicated test server's `world/level.dat`
and `world/serverconfig`, copied as they are. `elysium-run-e.ps1 -FreshWorld`
runs them through `StripPlayer.java` (player removed, renamed, cheats on,
experimental warning confirmed) into `saves/ElysiumBoot`. The file carries the
overworld's biome list inline, so `elysium-wilds/validate.py` holds it to the
current `normal.json`; when the preset changes, stop the server, copy its
`world/level.dat` and `serverconfig` here again, and commit them.
