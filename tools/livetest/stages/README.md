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
  lit by the compass, a survival player carried through and back (1 Oct).
  Its header lists the traps it found: face north, aim at the sill, stand in
  the gate rather than walk, and the driver's key flag is UP.

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
