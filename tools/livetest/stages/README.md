# Client stages

The dedicated server answers questions over RCON (`../livetest.py`); these
stages drive the test client at `E:\testingbuilds\Elysium` for what only a
player reaches, and take the screenshots. `mc.ps1` is the driver: it finds the
Minecraft window, sends keys and mouse through `SendInput` (only while that
window is foreground), reads the chat back out of `logs/latest.log`, and
captures the window. `MC-Resume` gets the game answering first: it asks the
clock, presses ESC if the pause menu is up, and clicks Respawn if the world was
saved with a dead player.

Run one from PowerShell with the client already in its world:

    powershell -NoProfile -ExecutionPolicy Bypass -File stage_mechanics_client.ps1

A stage is only as new as the jar the client booted with: rerun the launcher
after every install. Screenshots land beside the script, or in
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
  their paths are this machine's test rig.
* `stage_screens_client.ps1` - every block that opens a menu, opened once and
  photographed with a verdict, then two minutes of night with the natural
  spawns counted around the player.
* `stage_creatures_client.ps1` - the creatures on their body plans, summoned in
  two rows and photographed standing and moving (POLISH.md P10).
* `stage_jei_client.ps1` - with a JEI jar dropped beside the seven, the item
  panel and a machine's own recipe category from the library's plugin
  (POLISH.md P6). Take the jar out again afterwards.

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
