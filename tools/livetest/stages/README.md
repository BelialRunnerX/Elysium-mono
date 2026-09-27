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
